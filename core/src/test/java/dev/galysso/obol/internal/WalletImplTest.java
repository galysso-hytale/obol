package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.event.CoinsChangedEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wallet rules over the real store, locks and listeners, no fake. */
class WalletImplTest {

    private static final Coins TEN = Coins.ofCopper(10);

    private final BalanceStoreImpl store = new BalanceStoreImpl();
    private final WalletLocks locks = new WalletLocks();
    private final Listeners listeners = new Listeners((listener, event, e) -> {
        throw new AssertionError("listener failed on " + event, e);
    });
    /** Every published event, with the lock state at publication time. */
    private final List<CoinsChangedEvent> events = new ArrayList<>();
    private final List<Boolean> publishedUnderLock = new ArrayList<>();

    WalletImplTest() {
        listeners.add(event -> {
            events.add(event);
            publishedUnderLock.add(holdsLock(event.wallet()));
        });
    }

    private WalletImpl wallet(String key) {
        return new WalletImpl(new WalletId("test", key), store, locks, listeners);
    }

    private WalletImpl wallet(String key, long copper) {
        WalletImpl wallet = wallet(key);
        store.set(wallet.id(), Coins.ofCopper(copper));
        return wallet;
    }

    private long copper(Wallet wallet) {
        return store.balance(wallet.id()).copper();
    }

    private boolean holdsLock(WalletId id) {
        return ((ReentrantLock) locks.lockFor(id)).isHeldByCurrentThread();
    }

    @Test
    void balanceReadsTheStoreEveryTime() {
        WalletImpl wallet = wallet("a", 42);
        assertEquals(Coins.ofCopper(42), wallet.balance());
        store.set(wallet.id(), Coins.ofCopper(7));
        assertEquals(Coins.ofCopper(7), wallet.balance());
        assertTrue(wallet.canAfford(Coins.ofCopper(7)));
        assertFalse(wallet.canAfford(Coins.ofCopper(8)));
    }

    @Test
    void unknownIdHoldsZeroWithoutAnEntry() {
        WalletImpl wallet = wallet("nobody");
        assertEquals(Coins.ZERO, wallet.balance());
        assertFalse(store.exists(wallet.id()));
        assertFalse(wallet.withdraw(Coins.ofCopper(1)));
        assertFalse(store.exists(wallet.id()));
    }

    @Test
    void depositWritesAndReturnsNewBalance() {
        WalletImpl wallet = wallet("a", 5);
        assertEquals(Coins.ofCopper(15), wallet.deposit(TEN));
        assertEquals(15, copper(wallet));
        assertEquals(Coins.ofCopper(15), wallet.deposit(Coins.ZERO));
    }

    @Test
    void depositOverflowFailsWithoutWriting() {
        WalletImpl wallet = wallet("a", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> wallet.deposit(Coins.ofCopper(1)));
        assertEquals(Long.MAX_VALUE, copper(wallet));
        assertTrue(events.isEmpty());
    }

    @Test
    void withdrawDebitsWhenCovered() {
        WalletImpl wallet = wallet("a", 10);
        assertTrue(wallet.withdraw(Coins.ofCopper(4)));
        assertEquals(6, copper(wallet));
        assertTrue(wallet.withdraw(Coins.ofCopper(6)));
        assertEquals(0, copper(wallet));
        assertTrue(store.exists(wallet.id()), "zero balance is kept");
    }

    @Test
    void withdrawRefusesWithoutWriting() {
        WalletImpl wallet = wallet("a", 10);
        assertFalse(wallet.withdraw(Coins.ofCopper(11)));
        assertEquals(10, copper(wallet));
        assertTrue(events.isEmpty());
    }

    @Test
    void transferMovesBothBalances() {
        WalletImpl from = wallet("a", 10);
        WalletImpl to = wallet("b", 1);
        assertTrue(from.transferTo(to, Coins.ofCopper(4)));
        assertEquals(6, copper(from));
        assertEquals(5, copper(to));
    }

    @Test
    void transferWithInsufficientFundsWritesNothing() {
        WalletImpl from = wallet("a", 3);
        WalletImpl to = wallet("b", 1);
        assertFalse(from.transferTo(to, Coins.ofCopper(4)));
        assertEquals(3, copper(from));
        assertEquals(1, copper(to));
        assertTrue(events.isEmpty());
    }

    @Test
    void transferOverflowOnDestinationWritesNothing() {
        WalletImpl from = wallet("a", 10);
        WalletImpl to = wallet("b", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> from.transferTo(to, Coins.ofCopper(1)));
        assertEquals(10, copper(from));
        assertEquals(Long.MAX_VALUE, copper(to));
        assertTrue(events.isEmpty());
    }

    @Test
    void transferToSelfWritesNothing() {
        WalletImpl wallet = wallet("a", 10);
        WalletImpl sameId = wallet("a");
        assertTrue(wallet.transferTo(wallet, TEN));
        assertTrue(wallet.transferTo(sameId, TEN));
        assertFalse(wallet.transferTo(sameId, Coins.ofCopper(11)));
        assertEquals(10, copper(wallet));
        assertTrue(events.isEmpty());
    }

    @Test
    void transferAcceptsAnyWalletOfTheSameStore() {
        // The receiving side is only asked its id: a handle from another
        // backend instance, or a test double, works as long as the id is
        // one of this store's.
        WalletImpl from = wallet("a", 10);
        WalletId toId = new WalletId("test", "b");
        Wallet foreign = new WalletImpl(toId, new BalanceStoreImpl(), new WalletLocks(), listeners);
        assertTrue(from.transferTo(foreign, Coins.ofCopper(4)));
        assertEquals(6, copper(from));
        assertEquals(Coins.ofCopper(4), store.balance(toId));
    }

    @Test
    void crossedConcurrentTransfersNeitherDeadlockNorLeak() throws InterruptedException {
        WalletImpl a = wallet("a", 1_000);
        WalletImpl b = wallet("b", 1_000);
        int threads = 8;
        int rounds = 2_000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        // The recording listener is not thread-safe: these handles publish
        // to a quiet one, on the same store and locks.
        Listeners quiet = new Listeners((listener, event, e) -> {
            throw new AssertionError(e);
        });
        WalletImpl qa = new WalletImpl(a.id(), store, locks, quiet);
        WalletImpl qb = new WalletImpl(b.id(), store, locks, quiet);
        for (int i = 0; i < threads; i++) {
            Wallet from = i % 2 == 0 ? qa : qb;
            Wallet to = i % 2 == 0 ? qb : qa;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int r = 0; r < rounds; r++) {
                        from.transferTo(to, Coins.ofCopper(r % 7));
                    }
                } catch (Throwable t) {
                    synchronized (failures) {
                        failures.add(t);
                    }
                }
            });
            worker.start();
            workers.add(worker);
        }
        start.countDown();
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            for (Thread worker : workers) {
                worker.join();
            }
        });
        assertTrue(failures.isEmpty(), () -> "worker failed: " + failures.getFirst());
        assertEquals(2_000, copper(a) + copper(b), "money was created or destroyed");
    }

    @Test
    void depositAndWithdrawPublishBeforeAndAfterOutsideTheLock() {
        WalletImpl wallet = wallet("a", 5);
        wallet.deposit(TEN);
        assertTrue(wallet.withdraw(Coins.ofCopper(3)));
        assertEquals(List.of(
                new CoinsChangedEvent(wallet.id(), Coins.ofCopper(5), Coins.ofCopper(15)),
                new CoinsChangedEvent(wallet.id(), Coins.ofCopper(15), Coins.ofCopper(12))),
                events);
        assertEquals(List.of(false, false), publishedUnderLock, "published under the lock");
        assertFalse(holdsLock(wallet.id()));
    }

    @Test
    void nothingIsPublishedWhenNothingMoved() {
        WalletImpl wallet = wallet("a", 5);
        WalletImpl other = wallet("b", 5);
        wallet.deposit(Coins.ZERO);
        assertFalse(wallet.withdraw(TEN));
        assertFalse(wallet.transferTo(other, TEN));
        assertTrue(wallet.transferTo(wallet, Coins.ofCopper(5)));
        assertTrue(wallet.transferTo(other, Coins.ZERO));
        wallet.set(Coins.ofCopper(5));
        wallet("c").clear();
        assertTrue(events.isEmpty(), () -> "unexpected " + events);
    }

    @Test
    void transferPublishesDebitThenCreditAfterBothLocks() {
        WalletImpl from = wallet("b", 10);
        WalletImpl to = wallet("a", 1);
        List<Boolean> lockedDuringDispatch = new ArrayList<>();
        listeners.add(event -> lockedDuringDispatch.add(holdsLock(from.id()) || holdsLock(to.id())));
        assertTrue(from.transferTo(to, Coins.ofCopper(4)));
        assertEquals(List.of(
                new CoinsChangedEvent(from.id(), Coins.ofCopper(10), Coins.ofCopper(6)),
                new CoinsChangedEvent(to.id(), Coins.ofCopper(1), Coins.ofCopper(5))),
                events);
        assertEquals(List.of(false, false), lockedDuringDispatch);
    }

    @Test
    void listenersMayMoveMoneyThemselves() {
        WalletImpl wallet = wallet("a");
        WalletImpl tax = wallet("b");
        // A listener reacting to a deposit with a withdrawal on the same
        // wallet: legal because the event is delivered outside the lock.
        listeners.add(event -> {
            if (event.wallet().equals(wallet.id()) && event.increased()) {
                assertTrue(wallet.transferTo(tax, Coins.ofCopper(1)));
            }
        });
        wallet.deposit(TEN);
        assertEquals(9, copper(wallet));
        assertEquals(1, copper(tax));
        assertEquals(3, events.size());
    }

    @Test
    void setPublishesTheChangeOutsideTheLock() {
        WalletImpl wallet = wallet("a");
        assertEquals(Coins.ZERO, wallet.set(Coins.ofCopper(10)));
        assertEquals(Coins.ofCopper(10), wallet.set(Coins.ofCopper(3)));
        assertEquals(Coins.ofCopper(3), wallet.balance());
        assertEquals(List.of(
                new CoinsChangedEvent(wallet.id(), Coins.ZERO, Coins.ofCopper(10)),
                new CoinsChangedEvent(wallet.id(), Coins.ofCopper(10), Coins.ofCopper(3))),
                events);
        assertEquals(List.of(false, false), publishedUnderLock);
    }

    @Test
    void clearRemovesTheEntryAndPublishesDownToZeroOnlyIfSomethingWasHeld() {
        WalletImpl wallet = wallet("a");
        wallet.clear();
        wallet.set(Coins.ZERO);
        wallet.clear();
        assertFalse(store.exists(wallet.id()));
        assertTrue(events.isEmpty(), "zero to zero is not a change");

        wallet.set(Coins.ofCopper(7));
        wallet.clear();
        wallet.clear();
        assertFalse(store.exists(wallet.id()));
        assertEquals(Coins.ZERO, wallet.balance());
        assertEquals(new CoinsChangedEvent(wallet.id(), Coins.ofCopper(7), Coins.ZERO), events.getLast());
        assertEquals(List.of(false, false), publishedUnderLock);
    }

    @Test
    void equalityIsTheId() {
        WalletImpl a1 = wallet("a", 1);
        WalletImpl a2 = wallet("a", 99);
        WalletImpl b = wallet("b", 1);
        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotEquals(a1, b);
        assertNotEquals(a1, null);
        assertEquals("Wallet[test:a]", a1.toString());
    }

    @Test
    void nullArgumentsAreRejectedBeforeAnyWrite() {
        WalletImpl wallet = wallet("a", 10);
        assertThrows(NullPointerException.class, () -> wallet.deposit(null));
        assertThrows(NullPointerException.class, () -> wallet.withdraw(null));
        assertThrows(NullPointerException.class, () -> wallet.canAfford(null));
        assertThrows(NullPointerException.class, () -> wallet.transferTo(null, TEN));
        assertThrows(NullPointerException.class, () -> wallet.transferTo(wallet, null));
        assertThrows(NullPointerException.class, () -> wallet.set(null));
        assertEquals(10, copper(wallet));
        assertTrue(events.isEmpty());
    }
}
