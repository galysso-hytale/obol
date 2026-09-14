package dev.galysso.obol.api;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text rendering of a {@link Coins} amount, and parsing of the reverse form.
 *
 * <p>Formats are a closed set: an on-screen format needs a matching UI
 * template inside Obol, so adding one is an evolution of Obol, not of a
 * third-party mod. Each instance has a stable {@link #id()} that
 * {@code CoinsDisplay} maps to a template.</p>
 *
 * <p>No colours or server-side {@code Message} here: this module has no
 * Hytale dependency. See {@link Denomination#color()} for the suggested
 * palette.</p>
 */
public final class CoinsFormat {

    /**
     * {@code "2g 35s 4c"}: symbols, zero tiers omitted, {@code "0c"} for
     * {@link Coins#ZERO}. The only on-screen format in v0.1.
     */
    public static final CoinsFormat STANDARD =
            new CoinsFormat("standard", Denomination::symbol, "", " ");

    /**
     * {@code "2 gold, 35 silver, 4 copper"}: text only (chat, logs).
     */
    public static final CoinsFormat LONG =
            new CoinsFormat("long", CoinsFormat::name, " ", ", ");

    private static final Pattern TOKEN = Pattern.compile("\\G[\\s,]*(\\d+)\\s*([a-z]*)");
    private static final Map<String, Denomination> UNITS = units();

    private final String id;
    private final Function<Denomination, String> unit;
    private final String unitSeparator;
    private final String partSeparator;

    private CoinsFormat(String id, Function<Denomination, String> unit,
                        String unitSeparator, String partSeparator) {
        this.id = id;
        this.unit = unit;
        this.unitSeparator = unitSeparator;
        this.partSeparator = partSeparator;
    }

    /**
     * {@return the stable identifier of this format, e.g. {@code "standard"}}
     */
    public String id() {
        return id;
    }

    /**
     * {@return {@code coins} rendered with this format}
     *
     * <p>Tiers are listed from the largest to the smallest; tiers with no
     * coins are omitted, except that zero renders as the copper tier alone.</p>
     *
     * @param coins the amount to render
     */
    public String format(Coins coins) {
        if (coins.isZero()) {
            return part(Denomination.COPPER, 0);
        }
        EnumMap<Denomination, Long> parts = coins.breakdown();
        StringBuilder out = new StringBuilder();
        Denomination[] tiers = Denomination.values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            long count = parts.get(tiers[i]);
            if (count == 0) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(partSeparator);
            }
            out.append(part(tiers[i], count));
        }
        return out.toString();
    }

    private String part(Denomination tier, long count) {
        return count + unitSeparator + unit.apply(tier);
    }

    /**
     * Parses an amount written by a human.
     *
     * <p>Accepted: {@code "2g35s"}, {@code "2g 35s 4c"}, {@code "250s"},
     * {@code "1200"} (copper when the text is a bare number), and the output
     * of every format ({@code "2 gold, 35 silver"}). Case-insensitive; tiers
     * in any order, each at most once. Components need not be canonical:
     * {@code "250s"} is 2 gold 50 silver.</p>
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
                throw new CoinsParseException("Tier given twice: " + tier.name().toLowerCase(Locale.ROOT), text);
            }
            try {
                total = Math.addExact(total, Coins.of(tier, Long.parseLong(matcher.group(1))).copper());
            } catch (NumberFormatException | ArithmeticException e) {
                throw new CoinsParseException("Amount is too large", text);
            }
        }
        if (end != input.length()) {
            throw new CoinsParseException("Unexpected text at position " + end + ": \"" + input.substring(end) + "\"", text);
        }
        return Coins.ofCopper(total);
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

    @Override
    public String toString() {
        return "CoinsFormat[" + id + "]";
    }
}
