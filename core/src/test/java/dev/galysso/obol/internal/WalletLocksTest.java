package dev.galysso.obol.internal;

import dev.galysso.obol.api.WalletId;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletLocksTest {

    private final WalletLocks locks = new WalletLocks();

    @Test
    void sameIdSharesOneLockAcrossCalls() {
        WalletId a = new WalletId("player", "a");
        WalletId again = WalletId.parse("player:a");
        assertSame(locks.lockFor(a), locks.lockFor(again));
    }

    @Test
    void distinctIdsNeverShareALock() {
        WalletId a = new WalletId("player", "a");
        WalletId b = new WalletId("player", "b");
        WalletId shopA = new WalletId("shop", "a");
        assertNotSame(locks.lockFor(a), locks.lockFor(b));
        assertNotSame(locks.lockFor(a), locks.lockFor(shopA));
    }

    @Test
    void lockIsReentrant() {
        Lock lock = locks.lockFor(new WalletId("player", "a"));
        lock.lock();
        try {
            assertTrue(lock.tryLock());
            lock.unlock();
        } finally {
            lock.unlock();
        }
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class, () -> locks.lockFor(null));
    }
}
