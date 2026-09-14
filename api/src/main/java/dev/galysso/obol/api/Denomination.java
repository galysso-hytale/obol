package dev.galysso.obol.api;

import java.util.Locale;

/**
 * The four coin tiers, from the smallest to the largest.
 *
 * <p>The exchange rate between two consecutive tiers is fixed at ×100 and is
 * encoded only here, through {@link #valueInCopper()}. Everything else
 * ({@link Coins#breakdown()}, {@link Coins#toString()}) derives from it.</p>
 *
 * <p>Declaration order is ascending, so {@link #ordinal()} and
 * {@link #compareTo(Enum)} order tiers by value.</p>
 */
public enum Denomination {

    COPPER(1L, "c", "#B87333"),
    SILVER(100L, "s", "#C0C0C0"),
    GOLD(10_000L, "g", "#FFD700"),
    MYTHRIL(1_000_000L, "m", "#7FDBFF");

    /** Directory of the coin images in Obol's asset pack, under {@code Common/UI/Custom/}. */
    private static final String PACK_DIR = "Obol";

    private final long valueInCopper;
    private final String symbol;
    private final String color;
    private final String texture;

    Denomination(long valueInCopper, String symbol, String color) {
        this.valueInCopper = valueInCopper;
        this.symbol = symbol;
        this.color = color;
        this.texture = PACK_DIR + "/" + name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT) + ".png";
    }

    /**
     * {@return how many copper coins one coin of this tier is worth}
     */
    public long valueInCopper() {
        return valueInCopper;
    }

    /**
     * {@return the short symbol used by {@link Coins#toString()} in text}
     *
     * <p>The English initial ({@code c}, {@code s}, {@code g}, {@code m}).
     * On screen, Obol draws the coin itself instead.</p>
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
     * {@return the path of the coin image Obol draws for this tier, relative
     * to {@code Common/UI/Custom/}, e.g. {@code Obol/Gold.png}}
     *
     * <p>A 48×48 PNG shipped in Obol's asset pack, so every server running
     * Obol has it. A UI document of another mod, in its own directory under
     * {@code Common/UI/Custom/}, reaches it as {@code "../" + texture()}.
     * The path and the image size are part of the API and follow its
     * versioning; the drawing itself may change.</p>
     *
     * <p>This is a UI texture, not an item icon: a mod giving coins a
     * physical form needs its own item assets.</p>
     */
    public String texture() {
        return texture;
    }
}
