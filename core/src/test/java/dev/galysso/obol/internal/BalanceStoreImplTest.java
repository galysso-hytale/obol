package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.WalletId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceStoreImplTest {

    private final BalanceStoreImpl store = new BalanceStoreImpl();
    private final WalletId id = new WalletId("player", "a");

    @Test
    void unknownIdReadsZeroWithoutAnEntry() {
        assertEquals(Coins.ZERO, store.balance(id));
        assertFalse(store.exists(id));
        assertFalse(store.isDirty());
    }

    @Test
    void setStoresAndReturnsPrevious() {
        assertEquals(Coins.ZERO, store.set(id, Coins.ofCopper(150)));
        assertEquals(Coins.ofCopper(150), store.set(id, Coins.ofCopper(20)));
        assertEquals(Coins.ofCopper(20), store.balance(id));
        assertTrue(store.isDirty());
    }

    @Test
    void zeroBalanceIsKeptAsAnEntry() {
        store.set(id, Coins.ZERO);
        assertTrue(store.exists(id));
        assertEquals(Coins.ZERO, store.balance(id));
    }

    @Test
    void deleteRemovesTheEntry() {
        store.set(id, Coins.ofCopper(5));
        store.snapshot();
        assertTrue(store.delete(id));
        assertFalse(store.exists(id));
        assertTrue(store.isDirty());
        assertFalse(store.delete(id));
    }

    @Test
    void deletingAnUnknownIdDoesNotDirtyTheStore() {
        assertFalse(store.delete(id));
        assertFalse(store.isDirty());
    }

    @Test
    void snapshotCopiesByStorageKeyAndClearsDirty() {
        store.set(id, Coins.ofCopper(7));
        store.set(new WalletId("shop", "smith"), Coins.ofCopper(300));

        Map<String, Long> copy = store.snapshot();

        assertEquals(Map.of("player:a", 7L, "shop:smith", 300L), copy);
        assertFalse(store.isDirty());

        // A copy: later writes do not leak into it, and it cannot write back.
        store.set(id, Coins.ofCopper(8));
        assertEquals(7L, copy.get("player:a"));
        assertTrue(store.isDirty());
    }

    @Test
    void loadReplacesEverythingWithoutDirtying() {
        store.set(id, Coins.ofCopper(1));
        store.snapshot();

        store.load(Map.of("shop:smith", 300L));

        assertFalse(store.exists(id));
        assertEquals(Coins.ofCopper(300), store.balance(new WalletId("shop", "smith")));
        assertFalse(store.isDirty());
    }

    @Test
    void loadRefusesNegativeValuesBeforeTouchingTheStore() {
        store.set(id, Coins.ofCopper(1));
        assertThrows(IllegalArgumentException.class,
                () -> store.load(Map.of("player:a", 5L, "player:b", -1L)));
        assertEquals(Coins.ofCopper(1), store.balance(id));
    }

    @Test
    void markDirtyForcesTheNextSnapshot() {
        store.snapshot();
        assertFalse(store.isDirty());
        store.markDirty();
        assertTrue(store.isDirty());
    }

    @Test
    void nullArgumentsRejected() {
        assertThrows(NullPointerException.class, () -> store.balance(null));
        assertThrows(NullPointerException.class, () -> store.set(null, Coins.ZERO));
        assertThrows(NullPointerException.class, () -> store.set(id, null));
        assertThrows(NullPointerException.class, () -> store.exists(null));
        assertThrows(NullPointerException.class, () -> store.delete(null));
    }
}
