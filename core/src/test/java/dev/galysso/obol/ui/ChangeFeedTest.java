package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeFeedTest {

    private final ChangeFeed feed = new ChangeFeed();

    @Test
    void newestFirstAndNothingMerged() {
        feed.add(Coins.ofCopper(5), true, 1000);
        feed.add(Coins.ofCopper(5), true, 1001);
        feed.add(Coins.ofCopper(2), false, 1002);
        assertEquals(List.of(
                new ChangeFeed.Row(Coins.ofCopper(2), false, 1002),
                new ChangeFeed.Row(Coins.ofCopper(5), true, 1001),
                new ChangeFeed.Row(Coins.ofCopper(5), true, 1000)),
                feed.rows(1002));
    }

    @Test
    void aFullFeedDropsTheOldest() {
        for (int i = 0; i < ChangeFeed.MAX_ROWS + 2; i++) {
            feed.add(Coins.ofCopper(i + 1), true, i);
        }
        List<ChangeFeed.Row> rows = feed.rows(10);
        assertEquals(ChangeFeed.MAX_ROWS, rows.size());
        assertEquals(ChangeFeed.MAX_ROWS + 2, rows.getFirst().amount().copper(), "newest kept");
        assertEquals(3, rows.getLast().amount().copper(), "the two oldest gone");
    }

    @Test
    void aRowHoldsThenFadesStepByStepThenGoes() {
        ChangeFeed.Row row = new ChangeFeed.Row(Coins.ofCopper(1), true, 0);
        assertEquals(0, row.level(0));
        assertEquals(0, row.level(ChangeFeed.HOLD_MS - 1));
        assertEquals(1, row.level(ChangeFeed.HOLD_MS));
        assertEquals(2, row.level(ChangeFeed.HOLD_MS + ChangeFeed.TICK_MS));
        assertEquals(ChangeFeed.FADE_STEPS - 1, row.level(ChangeFeed.HOLD_MS + (ChangeFeed.FADE_STEPS - 2) * ChangeFeed.TICK_MS));
        assertEquals(ChangeFeed.FADE_STEPS, row.level(ChangeFeed.HOLD_MS + (ChangeFeed.FADE_STEPS - 1) * ChangeFeed.TICK_MS));
        assertEquals(ChangeFeed.FADE_STEPS, row.level(ChangeFeed.HOLD_MS + 100 * ChangeFeed.TICK_MS), "never beyond gone");
    }

    @Test
    void opacityFollowsTheLevel() {
        assertEquals(1.0, ChangeFeed.Row.opacity(0));
        assertEquals(0.9, ChangeFeed.Row.opacity(1));
        assertEquals(0.5, ChangeFeed.Row.opacity(5));
        assertEquals(0.0, ChangeFeed.Row.opacity(ChangeFeed.FADE_STEPS));
    }

    @Test
    void goneRowsAreForgottenWhenRead() {
        feed.add(Coins.ofCopper(1), true, 0);
        feed.add(Coins.ofCopper(2), true, 2000);
        long gone = ChangeFeed.HOLD_MS + (ChangeFeed.FADE_STEPS - 1) * ChangeFeed.TICK_MS;
        assertEquals(2, feed.rows(gone - 1).size());
        assertEquals(List.of(new ChangeFeed.Row(Coins.ofCopper(2), true, 2000)), feed.rows(gone));
        assertFalse(feed.isEmpty());
        assertTrue(feed.rows(2000 + gone).isEmpty());
        assertTrue(feed.isEmpty());
    }
}
