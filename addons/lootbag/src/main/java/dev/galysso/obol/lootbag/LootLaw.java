package dev.galysso.obol.lootbag;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsParseException;
import dev.galysso.obol.api.Denomination;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * How much a lootbag gives: a distribution over an amount range, or one
 * fixed amount. Written by a drop table or {@code lootbag.json}, carried
 * by the bag in its metadata, rolled when the bag is opened.
 *
 * <p>Pure: the JDK and {@link Coins}, no server class, so that it is unit
 * tested. The four distributions:</p>
 *
 * <ul>
 *   <li>{@code Uniform}: every amount of the range has the same chance.</li>
 *   <li>{@code Triangular}: the density climbs from {@code Min} to
 *   {@code Mode} and falls to {@code Max}. A mode near the minimum gives
 *   often little and sometimes a lot, the shape one expects of loot.</li>
 *   <li>{@code LogUniform}: uniform on the logarithm, every decade gets the
 *   same chance. For a wide range across several tiers, where
 *   {@code Uniform} would almost always land in the top tier.</li>
 *   <li>{@code Fixed}: one amount, a law with a single point.</li>
 * </ul>
 *
 * <p>{@code Step} rounds a roll down to a multiple of itself, then the
 * result is brought back into the range. Its default is one coin of the
 * tier below the largest tier of {@code Max}, so that an epic bag gives
 * "1 gold, 47 silver" and not "1 gold, 47 silver, 83 copper".</p>
 *
 * <p>A law is immutable and its {@link #toEntries() written form} is
 * deterministic: same law, same keys in the same order with the same
 * strings, otherwise two bags of the same law would not stack.</p>
 *
 * @param distribution the shape of the roll
 * @param min          the smallest amount, included. For {@code Fixed}, the amount
 * @param max          the largest amount, included. For {@code Fixed}, the amount
 * @param mode         the most likely amount, {@code Triangular} only, else {@code null}
 * @param step         the explicit rounding step, or {@code null} for the default
 */
public record LootLaw(Distribution distribution, Coins min, Coins max, Coins mode, Coins step) {

    /** The shape of a roll. Spelled as the JSON spells it. */
    public enum Distribution {
        Uniform,
        Triangular,
        LogUniform,
        Fixed;

        /** {@return the distribution of that name, in any case} */
        static Distribution parse(String name) {
            for (Distribution distribution : values()) {
                if (distribution.name().equalsIgnoreCase(name.trim())) {
                    return distribution;
                }
            }
            throw new IllegalArgumentException("Unknown distribution \"" + name
                    + "\", expected Uniform, Triangular, LogUniform or Fixed");
        }
    }

    /** The JSON keys, in the order they are written. */
    public static final String KEY_DISTRIBUTION = "Distribution";
    public static final String KEY_MIN = "Min";
    public static final String KEY_MAX = "Max";
    public static final String KEY_MODE = "Mode";
    public static final String KEY_STEP = "Step";
    public static final String KEY_AMOUNT = "Amount";

    private static final Map<Rarity, LootLaw> DEFAULTS = defaults();

    /**
     * Validates the law.
     *
     * @throws IllegalArgumentException when a bound is missing, {@code min}
     *                                  is above {@code max}, {@code mode} is
     *                                  given for a law that has none or is
     *                                  outside the range, {@code step} is
     *                                  zero, or {@code LogUniform} starts at
     *                                  zero
     */
    public LootLaw {
        Objects.requireNonNull(distribution, "distribution");
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Min " + min + " is above Max " + max);
        }
        switch (distribution) {
            case Fixed -> {
                if (!min.equals(max)) {
                    throw new IllegalArgumentException("A Fixed law has one amount, not a range");
                }
                if (mode != null || step != null) {
                    throw new IllegalArgumentException("A Fixed law takes neither Mode nor Step");
                }
            }
            case Triangular -> {
                if (mode == null) {
                    throw new IllegalArgumentException("A Triangular law needs a Mode");
                }
                if (mode.compareTo(min) < 0 || mode.compareTo(max) > 0) {
                    throw new IllegalArgumentException("Mode " + mode + " is outside " + min + " to " + max);
                }
            }
            case LogUniform -> {
                if (mode != null) {
                    throw new IllegalArgumentException("A LogUniform law takes no Mode");
                }
                if (min.copper() == 0) {
                    throw new IllegalArgumentException("A LogUniform law cannot start at zero");
                }
            }
            case Uniform -> {
                if (mode != null) {
                    throw new IllegalArgumentException("A Uniform law takes no Mode");
                }
            }
        }
        if (step != null && step.copper() == 0) {
            throw new IllegalArgumentException("Step cannot be zero");
        }
    }

    /** {@return the law that always gives {@code amount}} */
    public static LootLaw fixed(Coins amount) {
        return new LootLaw(Distribution.Fixed, amount, amount, null, null);
    }

    /** {@return the law that gives any amount of {@code min} to {@code max} with the same chance} */
    public static LootLaw uniform(Coins min, Coins max) {
        return new LootLaw(Distribution.Uniform, min, max, null, null);
    }

    /** {@return the law that peaks at {@code mode} between {@code min} and {@code max}} */
    public static LootLaw triangular(Coins min, Coins max, Coins mode) {
        return new LootLaw(Distribution.Triangular, min, max, mode, null);
    }

    /** {@return the law uniform on the logarithm of the amount, {@code min} above zero} */
    public static LootLaw logUniform(Coins min, Coins max) {
        return new LootLaw(Distribution.LogUniform, min, max, null, null);
    }

    /** {@return this law with an explicit rounding step, or the default one for {@code null}} */
    public LootLaw withStep(Coins step) {
        return new LootLaw(distribution, min, max, mode, step);
    }

    /** {@return the default law of a rarity, what {@code lootbag.json} starts with} */
    public static LootLaw defaultFor(Rarity rarity) {
        return DEFAULTS.get(Objects.requireNonNull(rarity, "rarity"));
    }

    /** {@return whether the law has a single amount} */
    public boolean isFixed() {
        return distribution == Distribution.Fixed;
    }

    /**
     * {@return the rounding step in force: {@link #step()} when given, else
     * one coin of the tier below the largest tier of {@link #max()}, copper
     * at the least}
     */
    public Coins effectiveStep() {
        return step != null ? step : defaultStep(max);
    }

    /** {@return the default step for a range ending at {@code max}} */
    public static Coins defaultStep(Coins max) {
        Denomination[] tiers = Denomination.values();
        Denomination largest = tiers[0];
        for (int i = tiers.length - 1; i > 0; i--) {
            if (max.copper() >= tiers[i].valueInCopper()) {
                largest = tiers[i];
                break;
            }
        }
        Denomination below = largest.ordinal() == 0 ? largest : tiers[largest.ordinal() - 1];
        return Coins.of(below, 1);
    }

    /**
     * {@return one amount drawn from this law}
     *
     * @param random a source of doubles in {@code [0, 1)}, one call per roll
     */
    public Coins roll(DoubleSupplier random) {
        if (isFixed() || min.equals(max)) {
            return min;
        }
        long lo = min.copper();
        long hi = max.copper();
        long unit = effectiveStep().copper();
        double u = random.getAsDouble();
        double x = switch (distribution) {
            // Over [lo, hi + step) so that, once floored, every multiple of
            // the step in the range has the same chance, the top included.
            case Uniform -> lo + u * (hi - lo + unit);
            case Triangular -> triangular(u, lo, hi, mode.copper());
            case LogUniform -> Math.exp(Math.log(lo) + u * (Math.log(hi) - Math.log(lo)));
            case Fixed -> lo;
        };
        long rounded = (long) Math.floor(x);
        rounded -= Math.floorMod(rounded, unit);
        return Coins.ofCopper(Math.max(lo, Math.min(hi, rounded)));
    }

    /** The inverse of the triangular distribution's cumulative function. */
    private static double triangular(double u, double lo, double hi, double mode) {
        double range = hi - lo;
        if (u < (mode - lo) / range) {
            return lo + Math.sqrt(u * range * (mode - lo));
        }
        return hi - Math.sqrt((1 - u) * range * (hi - mode));
    }

    /**
     * {@return the sum of {@code count} independent rolls, what a whole
     * stack gives}
     *
     * @throws ArithmeticException if the sum overflows a {@code long}
     */
    public Coins rollSum(int count, DoubleSupplier random) {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative: " + count);
        }
        Coins sum = Coins.ZERO;
        for (int i = 0; i < count; i++) {
            sum = sum.plus(roll(random));
        }
        return sum;
    }

    /**
     * {@return the written form of the law, keys in a fixed order}
     *
     * <p>{@code Fixed}: {@code Distribution} and {@code Amount}. The others:
     * {@code Distribution}, {@code Min}, {@code Max}, then {@code Mode} for
     * {@code Triangular} and {@code Step} only when it was given. Amounts
     * are {@link Coins#toString()}.</p>
     */
    public Map<String, String> toEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(KEY_DISTRIBUTION, distribution.name());
        if (isFixed()) {
            entries.put(KEY_AMOUNT, min.toString());
            return entries;
        }
        entries.put(KEY_MIN, min.toString());
        entries.put(KEY_MAX, max.toString());
        if (mode != null) {
            entries.put(KEY_MODE, mode.toString());
        }
        if (step != null) {
            entries.put(KEY_STEP, step.toString());
        }
        return entries;
    }

    /**
     * {@return the law written as {@code entries}, the reverse of {@link #toEntries()}}
     *
     * <p>{@code Distribution} may be left out: {@code Amount} alone means
     * {@code Fixed}, {@code Mode} means {@code Triangular}, anything else
     * {@code Uniform}. Amounts are read by {@link Coins#parse(String)}.
     * Empty strings count as absent.</p>
     *
     * @throws IllegalArgumentException for an unknown key or distribution,
     *                                  an amount that does not parse, a
     *                                  missing bound, or an invalid law
     */
    public static LootLaw of(Map<String, String> entries) {
        Map<String, String> given = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                given.put(entry.getKey(), entry.getValue());
            }
        }
        for (String key : given.keySet()) {
            switch (key) {
                case KEY_DISTRIBUTION, KEY_MIN, KEY_MAX, KEY_MODE, KEY_STEP, KEY_AMOUNT -> { }
                default -> throw new IllegalArgumentException("Unknown key \"" + key + "\" in a loot law");
            }
        }
        Distribution distribution;
        if (given.containsKey(KEY_DISTRIBUTION)) {
            distribution = Distribution.parse(given.get(KEY_DISTRIBUTION));
        } else if (given.containsKey(KEY_AMOUNT)) {
            distribution = Distribution.Fixed;
        } else if (given.containsKey(KEY_MODE)) {
            distribution = Distribution.Triangular;
        } else {
            distribution = Distribution.Uniform;
        }
        if (distribution == Distribution.Fixed) {
            if (given.containsKey(KEY_MIN) || given.containsKey(KEY_MAX)) {
                throw new IllegalArgumentException("A Fixed law takes Amount, not Min and Max");
            }
            return fixed(amount(given, KEY_AMOUNT, true));
        }
        if (given.containsKey(KEY_AMOUNT)) {
            throw new IllegalArgumentException("Amount is for a Fixed law, this one takes Min and Max");
        }
        return new LootLaw(distribution, amount(given, KEY_MIN, true), amount(given, KEY_MAX, true),
                amount(given, KEY_MODE, false), amount(given, KEY_STEP, false));
    }

    private static Coins amount(Map<String, String> given, String key, boolean required) {
        String text = given.get(key);
        if (text == null) {
            if (required) {
                throw new IllegalArgumentException(key + " is missing");
            }
            return null;
        }
        try {
            return Coins.parse(text);
        } catch (CoinsParseException e) {
            throw new IllegalArgumentException(key + ": " + e.getMessage(), e);
        }
    }

    /**
     * {@return the law written in one line of text}
     *
     * <p>Either an amount ({@code 2g 35s}, a {@code Fixed} law, the form a
     * bag carries for one), or a distribution followed by its amounts, one
     * word each ({@code 1g20s}, not {@code 1g 20s}): {@code uniform 1g 5g},
     * {@code triangular 5s 50s 12s}, {@code loguniform 1g 9g},
     * {@code fixed 5g}, with {@code step 1s} at the end if wanted.</p>
     *
     * @throws IllegalArgumentException when the text follows neither form
     */
    public static LootLaw parse(String text) {
        Objects.requireNonNull(text, "text");
        try {
            return fixed(Coins.parse(text));
        } catch (CoinsParseException e) {
            // Not a lone amount: the worded form.
        }
        List<String> words = new ArrayList<>(List.of(text.trim().split("\\s+")));
        if (words.isEmpty() || words.get(0).isEmpty()) {
            throw new IllegalArgumentException("Empty loot law");
        }
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(KEY_DISTRIBUTION, Distribution.parse(words.remove(0)).name());
        int stepAt = -1;
        for (int i = 0; i < words.size(); i++) {
            if (words.get(i).equalsIgnoreCase("step")) {
                stepAt = i;
            }
        }
        if (stepAt >= 0) {
            if (stepAt != words.size() - 2) {
                throw new IllegalArgumentException("\"step\" takes one amount, at the end");
            }
            entries.put(KEY_STEP, words.get(stepAt + 1));
            words = words.subList(0, stepAt);
        }
        String[] keys = switch (Distribution.parse(entries.get(KEY_DISTRIBUTION))) {
            case Fixed -> new String[] {KEY_AMOUNT};
            case Triangular -> new String[] {KEY_MIN, KEY_MAX, KEY_MODE};
            case Uniform, LogUniform -> new String[] {KEY_MIN, KEY_MAX};
        };
        if (words.size() != keys.length) {
            throw new IllegalArgumentException(entries.get(KEY_DISTRIBUTION) + " takes "
                    + String.join(", ", keys).toLowerCase(Locale.ROOT) + ", got " + words.size() + " amount(s)");
        }
        for (int i = 0; i < keys.length; i++) {
            entries.put(keys[i], words.get(i));
        }
        return of(entries);
    }

    /** {@return the law in the form {@link #parse(String)} reads: {@code triangular 5s 50s 12s}} */
    @Override
    public String toString() {
        if (isFixed()) {
            return min.toString();
        }
        StringBuilder out = new StringBuilder(distribution.name().toLowerCase(Locale.ROOT));
        for (Coins amount : mode == null ? List.of(min, max) : List.of(min, max, mode)) {
            out.append(' ').append(amount.toString().replace(" ", ""));
        }
        if (step != null) {
            out.append(" step ").append(step.toString().replace(" ", ""));
        }
        return out.toString();
    }

    /** A tenfold scale per rarity, from a few copper to a few tens of gold. */
    private static Map<Rarity, LootLaw> defaults() {
        Map<Rarity, LootLaw> laws = new EnumMap<>(Rarity.class);
        laws.put(Rarity.Common, triangular(copper(5), copper(50), copper(15)));
        laws.put(Rarity.Uncommon, triangular(copper(50), copper(500), copper(120)));
        laws.put(Rarity.Rare, triangular(copper(500), copper(5_000), copper(1_200)));
        laws.put(Rarity.Epic, triangular(copper(5_000), copper(50_000), copper(12_000)));
        laws.put(Rarity.Legendary, triangular(copper(50_000), copper(500_000), copper(120_000)));
        return Map.copyOf(laws);
    }

    private static Coins copper(long copper) {
        return Coins.ofCopper(copper);
    }
}
