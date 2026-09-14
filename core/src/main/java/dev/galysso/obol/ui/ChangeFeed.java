package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The recent changes of a tracked wallet, as a short-lived list under the
 * balance: what the player would ask "what just happened?" about.
 *
 * <p>The shape of a notification feed (kill feeds, loot toasts): a fixed
 * number of rows, newest nearest the balance, each row living its own
 * {@link #HOLD_MS} in full then fading over {@link #FADE_STEPS} steps of
 * {@link #TICK_MS} rather than vanishing, the oldest row making room when
 * the feed is full. Nothing is merged: two changes are two rows, so the
 * feed is complete for the rare burst of transactions, and bounded so that
 * it never takes over the screen.</p>
 *
 * <p>Pure state, timed by the caller's clock.</p>
 */
final class ChangeFeed {

    /** Rows shown at most; beyond, the oldest goes at once. */
    static final int MAX_ROWS = 5;

    /** Period of the fade steps, and of the caller's timer. */
    static final long TICK_MS = 100;

    /**
     * How long a row stays fully visible. Long enough to be read after it
     * caught the eye, short enough that a feed of five is gone before it
     * becomes a fixture (toast guidelines: 4 to 10 s; a row is two or three
     * tokens).
     */
    static final long HOLD_MS = 4000;

    /**
     * Steps of the fade, one per tick: a second from full to gone, in steps
     * of a tenth — below what the eye picks out on a dark ground, so the
     * row leaves without drawing a look on its way out.
     */
    static final int FADE_STEPS = 10;

    private final Deque<Row> rows = new ArrayDeque<>();

    /**
     * One change.
     *
     * @param amount how much moved, never zero
     * @param gain   whether the wallet grew
     * @param bornAt when it was added, in the caller's milliseconds
     */
    record Row(Coins amount, boolean gain, long bornAt) {

        /**
         * {@return how faded the row is at {@code now}: {@code 0} in full,
         * then {@code 1} to {@code FADE_STEPS - 1}, {@code FADE_STEPS} once
         * gone}
         */
        int level(long now) {
            long age = now - bornAt;
            if (age < HOLD_MS) {
                return 0;
            }
            return (int) Math.min(FADE_STEPS, 1 + (age - HOLD_MS) / TICK_MS);
        }

        /** {@return the row's opacity at that level, {@code 1.0} down to {@code 0.0}} */
        static double opacity(int level) {
            return 1.0 - (double) level / FADE_STEPS;
        }
    }

    /** Adds a change at the front of the feed. */
    void add(Coins amount, boolean gain, long now) {
        rows.addFirst(new Row(amount, gain, now));
        while (rows.size() > MAX_ROWS) {
            rows.removeLast();
        }
    }

    /**
     * {@return the rows alive at {@code now}, newest first}, forgetting
     * the ones that faded out
     */
    List<Row> rows(long now) {
        rows.removeIf(row -> row.level(now) >= FADE_STEPS);
        return List.copyOf(rows);
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }
}
