package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;

import java.util.ArrayList;
import java.util.List;

/**
 * The frames of a rolling counter: the amounts an overlay goes through when
 * it moves from one value to another, so that the player sees the number
 * travel rather than jump.
 *
 * <p>The client has no tweening of its own, so each frame is a packet. The
 * motion eases out: fast at first, so that the direction is read at once,
 * then settling on the exact value. Frames that would repeat the previous
 * one are dropped, so a change of a coin or two is a single frame.</p>
 */
final class Tween {

    /** Time between two frames; below this the client may batch packets. */
    static final long STEP_MS = 40;

    /** Number of frames of a full roll: about half a second. */
    static final int FRAMES = 12;

    /**
     * How long the changed counts keep their tint once the roll has settled:
     * long enough to be read as a colour, short enough to be an event.
     */
    static final long HOLD_MS = 250;

    private Tween() {
    }

    /**
     * {@return the frames from {@code from} (excluded) to {@code to}
     * (included, always last), empty when they are equal}
     */
    static List<Coins> frames(Coins from, Coins to) {
        List<Coins> frames = new ArrayList<>(FRAMES);
        long start = from.copper();
        long distance = to.copper() - start;
        long previous = start;
        for (int i = 1; i <= FRAMES; i++) {
            long value = i == FRAMES ? to.copper() : start + Math.round(distance * easeOut((double) i / FRAMES));
            if (value != previous) {
                frames.add(Coins.ofCopper(value));
                previous = value;
            }
        }
        return frames;
    }

    /** Cubic ease-out: {@code 1 - (1 - t)^3}. */
    private static double easeOut(double t) {
        double u = 1 - t;
        return 1 - u * u * u;
    }
}
