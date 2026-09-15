package dev.galysso.obol.api;

import java.util.Objects;

/**
 * How {@link ObolUi} lays coins out in a page. Start from {@link #DEFAULT}
 * and change what you need with the {@code with} methods; every option has
 * a default, and options may be added without breaking anyone.
 *
 * @param alignment where the coins sit in the width of their container
 */
public record CoinsStyle(Alignment alignment) {

    /** Where the coins sit in the width of their container. */
    public enum Alignment {
        /** Packed against the left edge. */
        START,
        /** Centred. */
        CENTER,
        /** Packed against the right edge, the way the HUD pill packs against a screen corner. */
        END
    }

    /** Centred. */
    public static final CoinsStyle DEFAULT = new CoinsStyle(Alignment.CENTER);

    /**
     * @throws NullPointerException if {@code alignment} is {@code null}
     */
    public CoinsStyle {
        Objects.requireNonNull(alignment, "alignment");
    }

    /** {@return this style with another alignment} */
    public CoinsStyle withAlignment(Alignment alignment) {
        return new CoinsStyle(alignment);
    }
}
