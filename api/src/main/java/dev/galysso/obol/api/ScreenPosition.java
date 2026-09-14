package dev.galysso.obol.api;

import java.util.Objects;

/**
 * Where an overlay sits on the viewer's screen: a corner and an offset from
 * it, in UI pixels.
 *
 * <p>A corner plus an offset is what the client's anchoring system knows how
 * to do, and it is the only thing that makes sense when every player has a
 * different screen size: absolute coordinates would land off-screen for
 * some and in the middle for others.</p>
 *
 * @param corner  the corner the offsets are measured from
 * @param offsetX horizontal distance from that corner, in UI pixels, never
 *                negative
 * @param offsetY vertical distance from that corner, in UI pixels, never
 *                negative
 */
public record ScreenPosition(Corner corner, int offsetX, int offsetY) {

    /** The four corners of the screen. */
    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        /** {@return whether this corner is on the right edge} */
        public boolean isRight() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        /** {@return whether this corner is on the bottom edge} */
        public boolean isBottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
    }

    /**
     * @throws NullPointerException     if {@code corner} is {@code null}
     * @throws IllegalArgumentException if an offset is negative, which would
     *                                  push the overlay off-screen
     */
    public ScreenPosition {
        Objects.requireNonNull(corner, "corner");
        if (offsetX < 0 || offsetY < 0) {
            throw new IllegalArgumentException(
                    "Offsets must not be negative: " + offsetX + ", " + offsetY);
        }
    }

    /** {@return a position measured from the top-left corner} */
    public static ScreenPosition topLeft(int offsetX, int offsetY) {
        return new ScreenPosition(Corner.TOP_LEFT, offsetX, offsetY);
    }

    /** {@return a position measured from the top-right corner} */
    public static ScreenPosition topRight(int offsetX, int offsetY) {
        return new ScreenPosition(Corner.TOP_RIGHT, offsetX, offsetY);
    }

    /** {@return a position measured from the bottom-left corner} */
    public static ScreenPosition bottomLeft(int offsetX, int offsetY) {
        return new ScreenPosition(Corner.BOTTOM_LEFT, offsetX, offsetY);
    }

    /** {@return a position measured from the bottom-right corner} */
    public static ScreenPosition bottomRight(int offsetX, int offsetY) {
        return new ScreenPosition(Corner.BOTTOM_RIGHT, offsetX, offsetY);
    }
}
