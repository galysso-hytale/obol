package dev.galysso.obol.api;

/**
 * The four coin tiers, from the smallest to the largest.
 *
 * <p>The exchange rate between two consecutive tiers is fixed at ×100 and is
 * encoded only here, through {@link #valueInCopper()}. Everything else
 * ({@link Coins#breakdown()}, {@link CoinsFormat}) derives from it.</p>
 *
 * <p>Declaration order is ascending, so {@link #ordinal()} and
 * {@link #compareTo(Enum)} order tiers by value.</p>
 */
public enum Denomination {

    COPPER(1L, "c", "#B87333"),
    SILVER(100L, "s", "#C0C0C0"),
    GOLD(10_000L, "g", "#FFD700"),
    MYTHRIL(1_000_000L, "m", "#7FDBFF");

    private final long valueInCopper;
    private final String symbol;
    private final String color;

    Denomination(long valueInCopper, String symbol, String color) {
        this.valueInCopper = valueInCopper;
        this.symbol = symbol;
        this.color = color;
    }

    /**
     * {@return how many copper coins one coin of this tier is worth}
     */
    public long valueInCopper() {
        return valueInCopper;
    }

    /**
     * {@return the short symbol used by {@link CoinsFormat#STANDARD} in text}
     *
     * <p>The English initial ({@code c}, {@code s}, {@code g}, {@code m}).
     * On screen, {@code CoinsDisplay} draws the coin itself instead.</p>
     */
    public String symbol() {
        return symbol;
    }

    /**
     * {@return a suggested display colour, as a {@code #RRGGBB} hex string}
     *
     * <p>Consumers rendering amounts in their own pages can use it to keep a
     * consistent palette without duplicating it.</p>
     */
    public String color() {
        return color;
    }

    /**
     * {@return the tier with the highest value}
     */
    public static Denomination largest() {
        Denomination[] all = values();
        return all[all.length - 1];
    }
}
