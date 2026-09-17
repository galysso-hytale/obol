package dev.galysso.obol.compat.aetherhaven;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObolGoldProviderTest {

    private final ObolGoldProvider provider = new ObolGoldProvider(Rate.DEFAULT, (itemId, amount) -> List.of());

    @Test
    void readsObolNotationAsWholeCoinsRoundedDown() {
        assertEquals(20L, provider.parseAmount("1g").getAsLong());
        assertEquals(2L, provider.parseAmount("12s").getAsLong());
        assertEquals(7L, provider.parseAmount("35 silver").getAsLong());
        // Too little for one coin: parsed, but nothing to move.
        assertEquals(0L, provider.parseAmount("3s").getAsLong());
        assertEquals(0L, provider.parseAmount("10").getAsLong());
    }

    @Test
    void rejectsWhatIsNotAnAmount() {
        assertTrue(provider.parseAmount("ten").isEmpty());
        assertTrue(provider.parseAmount("").isEmpty());
        assertTrue(provider.parseAmount("1g1g").isEmpty());
    }
}
