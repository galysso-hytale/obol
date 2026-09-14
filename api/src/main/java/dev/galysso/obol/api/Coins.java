package dev.galysso.obol.api;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An immutable, never-negative amount of coins.
 *
 * <p>The whole amount is a single {@code long} counted in copper, which is
 * the bridge to any persistence: a wallet only has one number to store.
 * {@link Denomination} and {@link #breakdown()} exist for humans; arithmetic
 * never leaves copper.</p>
 *
 * <p>Used both for prices ({@code Coins price}) and for what a wallet holds
 * ({@code wallet.balance()}).</p>
 *
 * <p>Text form: {@link #toString()} writes {@code "2g 35s 4c"} and
 * {@link #parse(String)} reads it back, along with the looser forms a
 * player types. No colours or server-side {@code Message} here: this module
 * has no Hytale dependency. See {@link Denomination#color()} for the
 * suggested palette.</p>
 *
 * @param copper the amount in copper, never negative
 */
public record Coins(long copper) implements Comparable<Coins> {

    /** No coins at all. */
    public static final Coins ZERO = new Coins(0);

    private static final Pattern TOKEN = Pattern.compile("\\G[\\s,]*(\\d+)\\s*([a-z]*)");
    private static final Map<String, Denomination> UNITS = units();

    /**
     * Validates the non-negative invariant.
     *
     * @throws IllegalArgumentException if {@code copper} is negative
     */
    public Coins {
        if (copper < 0) {
            throw new IllegalArgumentException("Coins cannot be negative: " + copper);
        }
    }

    /**
     * {@return an amount counted in copper}
     *
     * @param copper the amount in copper
     * @throws IllegalArgumentException if {@code copper} is negative
     */
    public static Coins ofCopper(long copper) {
        return new Coins(copper);
    }

    /**
     * {@return {@code count} coins of the given tier}
     *
     * @param denomination the tier
     * @param count        how many coins of that tier, never negative
     * @throws IllegalArgumentException if {@code count} is negative
     * @throws ArithmeticException      if the amount overflows a {@code long}
     */
    public static Coins of(Denomination denomination, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative: " + count);
        }
        return new Coins(Math.multiplyExact(count, denomination.valueInCopper()));
    }

    /**
     * Parses an amount written by a human.
     *
     * <p>Accepted: {@code "2g35s"}, {@code "2g 35s 4c"}, {@code "250s"},
     * {@code "1200"} (copper when the text is a bare number), and tier names
     * in full ({@code "2 gold, 35 silver"}). Case-insensitive; tiers in any
     * order, each at most once. Components need not be canonical:
     * {@code "250s"} is 2 gold 50 silver. The output of {@link #toString()}
     * always parses back to the same amount.</p>
     *
     * @param text the text to parse
     * @return the amount
     * @throws CoinsParseException if the text is empty, contains anything but
     *                             numbers and tier names, names a tier twice,
     *                             or exceeds what a {@code long} holds
     */
    public static Coins parse(String text) throws CoinsParseException {
        String input = text.strip().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) {
            throw new CoinsParseException("Amount is empty", text);
        }
        Matcher matcher = TOKEN.matcher(input);
        EnumSet<Denomination> seen = EnumSet.noneOf(Denomination.class);
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            end = matcher.end();
            Denomination tier = tierOf(matcher.group(2), matcher.start() == 0 && end == input.length(), text);
            if (!seen.add(tier)) {
                throw new CoinsParseException("Tier given twice: " + name(tier), text);
            }
            try {
                total = Math.addExact(total, of(tier, Long.parseLong(matcher.group(1))).copper);
            } catch (NumberFormatException | ArithmeticException e) {
                throw new CoinsParseException("Amount is too large", text);
            }
        }
        if (end != input.length()) {
            throw new CoinsParseException("Unexpected text at position " + end + ": \"" + input.substring(end) + "\"", text);
        }
        return new Coins(total);
    }

    private static Denomination tierOf(String unit, boolean bareNumber, String text) throws CoinsParseException {
        if (unit.isEmpty()) {
            if (bareNumber) {
                return Denomination.COPPER;
            }
            throw new CoinsParseException("Missing tier after a number", text);
        }
        Denomination tier = UNITS.get(unit);
        if (tier == null) {
            throw new CoinsParseException("Unknown tier: \"" + unit + "\"", text);
        }
        return tier;
    }

    private static String name(Denomination tier) {
        return tier.name().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Denomination> units() {
        Map<String, Denomination> units = new HashMap<>();
        for (Denomination tier : Denomination.values()) {
            units.put(tier.symbol(), tier);
            units.put(name(tier), tier);
        }
        return Map.copyOf(units);
    }

    /**
     * {@return this amount plus {@code other}}
     *
     * @param other the amount to add
     * @throws ArithmeticException if the sum overflows a {@code long}
     */
    public Coins plus(Coins other) {
        return new Coins(Math.addExact(copper, other.copper));
    }

    /**
     * {@return this amount minus {@code other}, or empty if {@code other} is
     * larger than this}
     *
     * <p>Subtracting from an immutable amount is a question ("is there
     * anything left?"), not an error, hence no exception.</p>
     *
     * @param other the amount to subtract
     */
    public Optional<Coins> minus(Coins other) {
        return covers(other) ? Optional.of(new Coins(copper - other.copper)) : Optional.empty();
    }

    /**
     * {@return this amount multiplied by {@code factor}}
     *
     * @param factor the multiplier, never negative
     * @throws IllegalArgumentException if {@code factor} is negative
     * @throws ArithmeticException      if the product overflows a {@code long}
     */
    public Coins times(long factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Factor cannot be negative: " + factor);
        }
        return new Coins(Math.multiplyExact(copper, factor));
    }

    /**
     * {@return whether this amount is at least {@code other}}
     *
     * @param other the amount to compare against
     */
    public boolean covers(Coins other) {
        return copper >= other.copper;
    }

    /**
     * {@return the canonical decomposition of this amount into tiers}
     *
     * <p>Canonical means every tier but the largest holds fewer coins than
     * the exchange rate: 250 copper is 2 silver and 50 copper. Every tier is
     * present, with {@code 0} where it contributes nothing. The map iterates
     * in {@link Denomination} declaration order, i.e. from the smallest tier
     * to the largest.</p>
     */
    public EnumMap<Denomination, Long> breakdown() {
        EnumMap<Denomination, Long> parts = new EnumMap<>(Denomination.class);
        long remaining = copper;
        Denomination[] tiers = Denomination.values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            long value = tiers[i].valueInCopper();
            parts.put(tiers[i], remaining / value);
            remaining %= value;
        }
        return parts;
    }

    @Override
    public int compareTo(Coins other) {
        return Long.compare(copper, other.copper);
    }

    /**
     * {@return this amount as {@code "2g 35s 4c"}}
     *
     * <p>Tiers are listed from the largest to the smallest, each count
     * followed by its {@link Denomination#symbol()}; tiers with no coins are
     * omitted, except that zero renders as {@code "0c"}.</p>
     */
    @Override
    public String toString() {
        if (copper == 0) {
            return "0" + Denomination.COPPER.symbol();
        }
        EnumMap<Denomination, Long> parts = breakdown();
        StringBuilder out = new StringBuilder();
        Denomination[] tiers = Denomination.values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            long count = parts.get(tiers[i]);
            if (count == 0) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(count).append(tiers[i].symbol());
        }
        return out.toString();
    }
}
