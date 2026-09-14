package dev.galysso.obol.api;

import dev.galysso.obol.api.event.CoinsChangedEvent;
import dev.galysso.obol.api.internal.ObolApiHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletTest {

    private static final Coins TEN = Coins.ofCopper(10);

    private InMemoryRuntime runtime;

    @BeforeEach
    void installRuntime() {
        runtime = InMemoryRuntime.install();
    }

    @AfterEach
    void uninstallRuntime() {
        ObolApiHolder.uninstall();
    }

    @Test
    void balanceReadsStorageEveryTime() {
        FieldWallet wallet = new FieldWallet("a", 42);
        assertEquals(Coins.ofCopper(42), wallet.balance());
        wallet.copper = 7;
        assertEquals(Coins.ofCopper(7), wallet.balance());
        assertTrue(wallet.canAfford(Coins.ofCopper(7)));
        assertFalse(wallet.canAfford(Coins.ofCopper(8)));
        assertEquals(0, wallet.saves);
    }

    @Test
    void depositWritesAndReturnsNewBalance() {
        FieldWallet wallet = new FieldWallet("a", 5);
        assertEquals(Coins.ofCopper(15), wallet.deposit(TEN));
        assertEquals(15, wallet.copper);
        assertEquals(1, wallet.saves);
        assertEquals(Coins.ofCopper(15), wallet.deposit(Coins.ZERO));
    }

    @Test
    void depositOverflowFailsWithoutWriting() {
        FieldWallet wallet = new FieldWallet("a", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> wallet.deposit(Coins.ofCopper(1)));
        assertEquals(Long.MAX_VALUE, wallet.copper);
        assertEquals(0, wallet.saves);
    }

    @Test
    void withdrawDebitsWhenCovered() {
        FieldWallet wallet = new FieldWallet("a", 10);
        assertTrue(wallet.withdraw(Coins.ofCopper(4)));
        assertEquals(6, wallet.copper);
        assertTrue(wallet.withdraw(Coins.ofCopper(6)));
        assertEquals(0, wallet.copper);
        assertEquals(2, wallet.saves);
    }

    @Test
    void withdrawRefusesWithoutWriting() {
        FieldWallet wallet = new FieldWallet("a", 10);
        assertFalse(wallet.withdraw(Coins.ofCopper(11)));
        assertEquals(10, wallet.copper);
        assertEquals(0, wallet.saves);
    }

    @Test
    void negativeStorageIsCorruption() {
        FieldWallet wallet = new FieldWallet("a", -1);
        assertThrows(IllegalStateException.class, wallet::balance);
        assertThrows(IllegalStateException.class, () -> wallet.deposit(TEN));
        assertThrows(IllegalStateException.class, () -> wallet.withdraw(TEN));
        FieldWallet other = new FieldWallet("b", 10);
        assertThrows(IllegalStateException.class, () -> other.transferTo(wallet, TEN));
        assertEquals(-1, wallet.copper);
        assertEquals(10, other.copper);
        assertEquals(0, wallet.saves + other.saves);
    }

    @Test
    void transferMovesBothBalances() {
        FieldWallet from = new FieldWallet("a", 10);
        FieldWallet to = new FieldWallet("b", 1);
        assertTrue(from.transferTo(to, Coins.ofCopper(4)));
        assertEquals(6, from.copper);
        assertEquals(5, to.copper);
        assertEquals(1, from.saves);
        assertEquals(1, to.saves);
    }

    @Test
    void transferWithInsufficientFundsWritesNothing() {
        FieldWallet from = new FieldWallet("a", 3);
        FieldWallet to = new FieldWallet("b", 1);
        assertFalse(from.transferTo(to, Coins.ofCopper(4)));
        assertEquals(3, from.copper);
        assertEquals(1, to.copper);
        assertEquals(0, from.saves + to.saves);
    }

    @Test
    void transferRollsBackWhenDestinationSaveFails() {
        FieldWallet from = new FieldWallet("a", 10);
        FieldWallet to = new FieldWallet("b", 1);
        to.failOnSave = new IllegalStateException("disk on fire");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> from.transferTo(to, Coins.ofCopper(4)));
        assertEquals("disk on fire", e.getMessage());
        assertEquals(10, from.copper);
        assertEquals(1, to.copper);
        assertEquals(2, from.saves, "debit then rollback");
    }

    @Test
    void transferOverflowOnDestinationWritesNothing() {
        FieldWallet from = new FieldWallet("a", 10);
        FieldWallet to = new FieldWallet("b", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> from.transferTo(to, Coins.ofCopper(1)));
        assertEquals(10, from.copper);
        assertEquals(Long.MAX_VALUE, to.copper);
        assertEquals(0, from.saves + to.saves);
    }

    @Test
    void transferToSelfWritesNothing() {
        FieldWallet wallet = new FieldWallet("a", 10);
        FieldWallet sameId = new FieldWallet("a", 10);
        assertTrue(wallet.transferTo(wallet, TEN));
        assertTrue(wallet.transferTo(sameId, TEN));
        assertFalse(wallet.transferTo(sameId, Coins.ofCopper(11)));
        assertEquals(0, wallet.saves + sameId.saves);
    }

    @Test
    void storageHooksRunUnderTheWalletLock() {
        FieldWallet observed = new FieldWallet("a", 10) {
            @Override
            protected long loadCopper() {
                assertTrue(runtime.holdsLock(id()), "loadCopper outside the lock");
                return super.loadCopper();
            }

            @Override
            protected void saveCopper(long copper) {
                assertTrue(runtime.holdsLock(id()), "saveCopper outside the lock");
                super.saveCopper(copper);
            }
        };
        FieldWallet other = new FieldWallet("b", 10);
        observed.balance();
        observed.deposit(TEN);
        observed.withdraw(TEN);
        assertTrue(observed.transferTo(other, TEN));
        assertTrue(other.transferTo(observed, TEN));
        assertFalse(runtime.holdsLock(observed.id()));
        assertFalse(runtime.holdsLock(other.id()));
    }

    @Test
    void crossedConcurrentTransfersNeitherDeadlockNorLeak() throws InterruptedException {
        FieldWallet a = new FieldWallet("a", 1_000);
        FieldWallet b = new FieldWallet("b", 1_000);
        int threads = 8;
        int rounds = 2_000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Wallet from = i % 2 == 0 ? a : b;
            Wallet to = i % 2 == 0 ? b : a;
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
        assertEquals(2_000, a.copper + b.copper, "money was created or destroyed");
        assertTrue(a.copper >= 0 && b.copper >= 0);
    }

    @Test
    void depositAndWithdrawPublishBeforeAndAfter() {
        FieldWallet wallet = new FieldWallet("a", 5);
        wallet.deposit(TEN);
        assertTrue(wallet.withdraw(Coins.ofCopper(3)));
        assertEquals(List.of(
                new CoinsChangedEvent(wallet.id(), Coins.ofCopper(5), Coins.ofCopper(15)),
                new CoinsChangedEvent(wallet.id(), Coins.ofCopper(15), Coins.ofCopper(12))),
                runtime.events);
        assertEquals(List.of(false, false), runtime.publishedUnderLock, "published under the lock");
    }

    @Test
    void nothingIsPublishedWhenNothingMoved() {
        FieldWallet wallet = new FieldWallet("a", 5);
        FieldWallet other = new FieldWallet("b", 5);
        wallet.deposit(Coins.ZERO);
        assertFalse(wallet.withdraw(TEN));
        assertFalse(wallet.transferTo(other, TEN));
        assertTrue(wallet.transferTo(wallet, Coins.ofCopper(5)));
        assertTrue(wallet.transferTo(other, Coins.ZERO));
        assertThrows(ArithmeticException.class,
                () -> new FieldWallet("c", Long.MAX_VALUE).deposit(Coins.ofCopper(1)));
        assertTrue(runtime.events.isEmpty(), () -> "unexpected " + runtime.events);
    }

    @Test
    void transferPublishesDebitThenCreditAfterBothLocks() {
        FieldWallet from = new FieldWallet("b", 10);
        FieldWallet to = new FieldWallet("a", 1);
        List<Boolean> lockedDuringDispatch = new ArrayList<>();
        runtime.addListener(event -> {
            lockedDuringDispatch.add(runtime.holdsLock(from.id()) || runtime.holdsLock(to.id()));
        });
        assertTrue(from.transferTo(to, Coins.ofCopper(4)));
        assertEquals(List.of(
                new CoinsChangedEvent(from.id(), Coins.ofCopper(10), Coins.ofCopper(6)),
                new CoinsChangedEvent(to.id(), Coins.ofCopper(1), Coins.ofCopper(5))),
                runtime.events);
        assertEquals(List.of(false, false), lockedDuringDispatch);
    }

    @Test
    void rolledBackTransferPublishesNothing() {
        FieldWallet from = new FieldWallet("a", 10);
        FieldWallet to = new FieldWallet("b", 1);
        to.failOnSave = new IllegalStateException("disk on fire");
        assertThrows(IllegalStateException.class, () -> from.transferTo(to, Coins.ofCopper(4)));
        assertTrue(runtime.events.isEmpty());
    }

    @Test
    void listenersMayMoveMoneyThemselves() {
        FieldWallet wallet = new FieldWallet("a", 0);
        FieldWallet tax = new FieldWallet("b", 0);
        // A listener reacting to a deposit with a withdrawal on the same
        // wallet: legal because the event is delivered outside the lock.
        runtime.addListener(event -> {
            if (event.wallet().equals(wallet.id()) && event.increased()) {
                assertTrue(wallet.transferTo(tax, Coins.ofCopper(1)));
            }
        });
        wallet.deposit(TEN);
        assertEquals(9, wallet.copper);
        assertEquals(1, tax.copper);
        assertEquals(3, runtime.events.size());
    }

    @Test
    void equalityIsClassAndId() {
        FieldWallet a1 = new FieldWallet("a", 1);
        FieldWallet a2 = new FieldWallet("a", 99);
        FieldWallet b = new FieldWallet("b", 1);
        Wallet otherClassSameId = new StoredWallet() {
            @Override
            public WalletId id() {
                return a1.id();
            }
        };
        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotEquals(a1, b);
        assertNotEquals(a1, otherClassSameId);
        assertNotEquals(a1, null);
        assertEquals("FieldWallet[test:a]", a1.toString());
    }

    @Test
    void nullArgumentsAreRejectedBeforeAnyWrite() {
        FieldWallet wallet = new FieldWallet("a", 10);
        assertThrows(NullPointerException.class, () -> wallet.deposit(null));
        assertThrows(NullPointerException.class, () -> wallet.withdraw(null));
        assertThrows(NullPointerException.class, () -> wallet.transferTo(null, TEN));
        assertThrows(NullPointerException.class, () -> wallet.transferTo(wallet, null));
        assertEquals(0, wallet.saves);
    }

    @Test
    void operationsFailClearlyWhenObolIsAbsent() {
        ObolApiHolder.uninstall();
        FieldWallet wallet = new FieldWallet("a", 10);
        IllegalStateException e = assertThrows(IllegalStateException.class, wallet::balance);
        assertTrue(e.getMessage().contains("Galysso:obol"));
    }
}
