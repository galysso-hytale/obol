package dev.galysso.obol.api;

import java.util.Objects;

/**
 * How {@link ObolUi} lays coins out in a page. Start from {@link #DEFAULT}
 * and change what you need with the {@code with} methods; every option has
 * a default, and options may be added without breaking anyone.
 *
 * @param alignment where the coins sit in the width of their container
 * @param fontSize  the size of the counts' digits, in UI pixels; the coins
 *                  are drawn two pixels taller, so that an amount fits the
 *                  line of text it sits in
 * @param zeroTiers whether a tier holding no coins is drawn
 */
public record CoinsStyle(Alignment alignment, int fontSize, ZeroTiers zeroTiers) {

    /** Where the coins sit in the width of their container. */
    public enum Alignment {
        /** Packed against the left edge. */
        START,
        /** Centred. */
        CENTER,
        /** Packed against the right edge, the way the HUD pill packs against a screen corner. */
        END
    }

    /** Whether a tier holding no coins is drawn. */
    public enum ZeroTiers {
        /**
         * The HUD's rule: every tier below the largest one holding coins is
         * drawn even at zero, {@code 2 [gold] 0 [silver] 5 [copper]} for
         * {@code 2g 5c}, so that the tiers keep their columns.
         */
        SHOWN,
        /**
         * Only the tiers holding coins, {@code 2 [gold] 5 [copper]}, what
         * {@link Coins#toString()} writes: for a small page. Zero is still
         * {@code 0 [copper]}.
         */
        HIDDEN
    }

    /** The font size the HUD draws its counts at, 24 pixel coins. */
    public static final int HUD_FONT_SIZE = 22;

    /** Centred, at the HUD's size, zero tiers shown. */
    public static final CoinsStyle DEFAULT = new CoinsStyle(Alignment.CENTER, HUD_FONT_SIZE, ZeroTiers.SHOWN);

    /**
     * @throws NullPointerException     if {@code alignment} or {@code zeroTiers} is {@code null}
     * @throws IllegalArgumentException if {@code fontSize} is not positive
     */
    public CoinsStyle {
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(zeroTiers, "zeroTiers");
        if (fontSize <= 0) {
            throw new IllegalArgumentException("fontSize must be positive: " + fontSize);
        }
    }

    /** {@code alignment} at the HUD's size, zero tiers shown. */
    public CoinsStyle(Alignment alignment) {
        this(alignment, HUD_FONT_SIZE, ZeroTiers.SHOWN);
    }

    /** {@return this style with another alignment} */
    public CoinsStyle withAlignment(Alignment alignment) {
        return new CoinsStyle(alignment, fontSize, zeroTiers);
    }

    /** {@return this style drawing, or not, the tiers holding no coins} */
    public CoinsStyle withZeroTiers(ZeroTiers zeroTiers) {
        return new CoinsStyle(alignment, fontSize, zeroTiers);
    }

    /**
     * {@return this style with the digits at {@code fontSize}}
     * A price in a line of 13 pixel text asks for {@code withFontSize(13)}:
     * 13 pixel digits and 15 pixel coins.
     *
     * @throws IllegalArgumentException if {@code fontSize} is not positive
     */
    public CoinsStyle withFontSize(int fontSize) {
        return new CoinsStyle(alignment, fontSize, zeroTiers);
    }
}
