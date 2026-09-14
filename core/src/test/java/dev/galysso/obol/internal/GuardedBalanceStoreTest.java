package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.event.CoinsChangedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardedBalanceStoreTest {

    private final BalanceStoreImpl raw = new BalanceStoreImpl();
    private final WalletLocks locks = new WalletLocks();
    private final List<CoinsChangedEvent> seen = new ArrayList<>();
    private final List<Boolean> underLock = new ArrayList<>();
    private final Listeners listeners = new Listeners((listener, event, e) -> {
        throw new AssertionError(e);
    });
    private final GuardedBalanceStore store = new GuardedBalanceStore(raw, locks, listeners);
    private final WalletId alice = new WalletId("player", "alice");

    GuardedBalanceStoreTest() {
        listeners.add(event -> {
            seen.add(event);
            underLock.add(((ReentrantLock) locks.lockFor(event.wallet())).isHeldByCurrentThread());
        });
    }

    @Test
    void setPublishesTheChangeOutsideTheLock() {
        assertEquals(Coins.ZERO, store.set(alice, Coins.ofCopper(10)));
        assertEquals(Coins.ofCopper(10), store.set(alice, Coins.ofCopper(3)));

        assertEquals(Coins.ofCopper(3), store.balance(alice));
        assertEquals(Coins.ofCopper(3), raw.balance(alice));
        assertEquals(List.of(
                new CoinsChangedEvent(alice, Coins.ZERO, Coins.ofCopper(10)),
                new CoinsChangedEvent(alice, Coins.ofCopper(10), Coins.ofCopper(3))),
                seen);
        assertEquals(List.of(false, false), underLock);
    }

    @Test
    void writingTheSameBalanceIsNotAnEvent() {
        store.set(alice, Coins.ofCopper(10));
        store.set(alice, Coins.ofCopper(10));
        assertEquals(1, seen.size());
        assertTrue(store.exists(alice));
    }

    @Test
    void deletePublishesDownToZeroOnlyIfSomethingWasHeld() {
        assertFalse(store.delete(alice));
        store.set(alice, Coins.ZERO);
        assertTrue(store.delete(alice));
        assertTrue(seen.isEmpty(), "zero to zero is not a change");

        store.set(alice, Coins.ofCopper(7));
        assertTrue(store.delete(alice));
        assertFalse(store.exists(alice));
        assertEquals(new CoinsChangedEvent(alice, Coins.ofCopper(7), Coins.ZERO), seen.get(seen.size() - 1));
    }

    @Test
    void nullsAreRejectedBeforeAnythingIsWritten() {
        assertThrows(NullPointerException.class, () -> store.set(alice, null));
        assertThrows(NullPointerException.class, () -> store.set(null, Coins.ZERO));
        assertFalse(store.exists(alice));
        assertTrue(seen.isEmpty());
    }
}
