package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TweenTest {

    @Test
    void aRollEndsOnTheExactValueAndNeverRepeatsAFrame() {
        List<Coins> frames = Tween.frames(Coins.ofCopper(0), Coins.ofCopper(20_000));
        assertEquals(Tween.FRAMES, frames.size());
        assertEquals(Coins.ofCopper(20_000), frames.getLast());
        for (int i = 1; i < frames.size(); i++) {
            assertTrue(frames.get(i).compareTo(frames.get(i - 1)) > 0, "frame " + i + " moves forward");
        }
    }

    @Test
    void theRollEasesOut() {
        List<Coins> frames = Tween.frames(Coins.ofCopper(0), Coins.ofCopper(12_000));
        long firstStep = frames.get(0).copper();
        long lastStep = frames.getLast().copper() - frames.get(frames.size() - 2).copper();
        assertTrue(firstStep > lastStep, "fast first, then settling");
        assertTrue(frames.get(0).copper() > 12_000 / Tween.FRAMES, "faster than linear at the start");
    }

    @Test
    void aLossRollsDown() {
        List<Coins> frames = Tween.frames(Coins.ofCopper(500), Coins.ofCopper(100));
        assertEquals(Coins.ofCopper(100), frames.getLast());
        for (int i = 1; i < frames.size(); i++) {
            assertTrue(frames.get(i).compareTo(frames.get(i - 1)) < 0);
        }
    }

    @Test
    void aTinyChangeIsASingleFrame() {
        assertEquals(List.of(Coins.ofCopper(6)), Tween.frames(Coins.ofCopper(5), Coins.ofCopper(6)));
        assertEquals(List.of(Coins.ofCopper(4)), Tween.frames(Coins.ofCopper(5), Coins.ofCopper(4)));
    }

    @Test
    void noChangeIsNoFrame() {
        assertEquals(List.of(), Tween.frames(Coins.ofCopper(7), Coins.ofCopper(7)));
    }

    @Test
    void aHugeChangeDoesNotOverflow() {
        List<Coins> frames = Tween.frames(Coins.ZERO, Coins.ofCopper(Long.MAX_VALUE));
        assertEquals(Coins.ofCopper(Long.MAX_VALUE), frames.getLast());
        for (int i = 1; i < frames.size(); i++) {
            assertTrue(frames.get(i).compareTo(frames.get(i - 1)) > 0);
        }
    }
}
