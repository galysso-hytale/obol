package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.event.CoinsChangedEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.UnaryOperator;

/**
 * The single {@link Wallet}: a stateless handle on one entry of the
 * {@link BalanceStoreImpl}, enforcing every rule of the contract.
 *
 * <p>Each operation runs under the lock {@link WalletLocks} keeps for the
 * id, reads the store, writes it, and publishes the change through
 * {@link Listeners} once the lock is released. {@link #transferTo} takes
 * both locks in the order of {@link WalletId#toString()} ({@code kind:key}),
 * a global order, so cross transfers cannot deadlock.</p>
 *
 * <p>The store is a map in memory and never throws on a write, so a
 * transfer needs no rollback: both writes happen under both locks, after
 * both new balances were computed.</p>
 */
public final class WalletImpl implements Wallet {

    private final WalletId id;
    private final BalanceStoreImpl store;
    private final WalletLocks locks;
    private final Listeners listeners;

    WalletImpl(WalletId id, BalanceStoreImpl store, WalletLocks locks, Listeners listeners) {
        this.id = Objects.requireNonNull(id, "id");
        this.store = store;
        this.locks = locks;
        this.listeners = listeners;
    }

    @Override
    public WalletId id() {
        return id;
    }

    @Override
    public Coins balance() {
        Lock lock = locks.lockFor(id);
        lock.lock();
        try {
            return store.balance(id);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean canAfford(Coins amount) {
        Objects.requireNonNull(amount, "amount");
        return balance().covers(amount);
    }

    @Override
    public Coins deposit(Coins amount) {
        Objects.requireNonNull(amount, "amount");
        return write(before -> before.plus(amount));
    }

    @Override
    public boolean withdraw(Coins amount) {
        Objects.requireNonNull(amount, "amount");
        return write(before -> before.minus(amount).orElse(null)) != null;
    }

    /**
     * Sets the balance outright, for {@code /obol set}: not a wallet
     * operation of the public API, since money appears or vanishes. Only
     * reachable through {@code ObolBackendImpl.wallet}, never through
     * {@code Obol.wallet}, which returns the interface.
     *
     * @return the previous balance
     */
    public Coins set(Coins coins) {
        Objects.requireNonNull(coins, "coins");
        Coins previous;
        Lock lock = locks.lockFor(id);
        lock.lock();
        try {
            previous = store.set(id, coins);
        } finally {
            lock.unlock();
        }
        publish(id, previous, coins);
        return previous;
    }

    @Override
    public void clear() {
        Coins previous;
        boolean removed;
        Lock lock = locks.lockFor(id);
        lock.lock();
        try {
            previous = store.balance(id);
            removed = store.delete(id);
        } finally {
            lock.unlock();
        }
        if (removed) {
            publish(id, previous, Coins.ZERO);
        }
    }

    /**
     * One read-modify-write under the lock, published after it. The
     * function returns the new balance, or {@code null} to write nothing.
     *
     * @return the new balance, or {@code null} if nothing was written
     */
    private Coins write(UnaryOperator<Coins> change) {
        Coins before;
        Coins after;
        Lock lock = locks.lockFor(id);
        lock.lock();
        try {
            before = store.balance(id);
            after = change.apply(before);
            if (after == null) {
                return null;
            }
            store.set(id, after);
        } finally {
            lock.unlock();
        }
        publish(id, before, after);
        return after;
    }

    @Override
    public boolean transferTo(Wallet to, Coins amount) {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        WalletId toId = to.id();
        if (id.equals(toId)) {
            return canAfford(amount);
        }
        // Global order: the lexicographically smaller key is locked first,
        // whichever side is paying. Two threads crossing A->B and B->A then
        // contend on the same first lock instead of deadlocking.
        boolean thisFirst = id.toString().compareTo(toId.toString()) < 0;
        Lock first = locks.lockFor(thisFirst ? id : toId);
        Lock second = locks.lockFor(thisFirst ? toId : id);
        Transfer done;
        first.lock();
        try {
            second.lock();
            try {
                done = transferLocked(toId, amount);
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }
        if (done == null) {
            return false;
        }
        publish(id, done.fromBefore, done.fromAfter);
        publish(toId, done.toBefore, done.toAfter);
        return true;
    }

    /** What a completed transfer wrote, carried out of the locked section. */
    private record Transfer(Coins fromBefore, Coins fromAfter, Coins toBefore, Coins toAfter) {
    }

    private Transfer transferLocked(WalletId toId, Coins amount) {
        Coins fromBefore = store.balance(id);
        Optional<Coins> fromAfter = fromBefore.minus(amount);
        if (fromAfter.isEmpty()) {
            return null;
        }
        // Computed before any write so that an overflow leaves both intact.
        Coins toBefore = store.balance(toId);
        Coins toAfter = toBefore.plus(amount);
        store.set(id, fromAfter.get());
        store.set(toId, toAfter);
        return new Transfer(fromBefore, fromAfter.get(), toBefore, toAfter);
    }

    /**
     * Reports a write, outside the lock. A write that moved nothing (a
     * deposit of zero) is not an event.
     */
    private void publish(WalletId wallet, Coins before, Coins after) {
        if (!before.equals(after)) {
            listeners.publish(new CoinsChangedEvent(wallet, before, after));
        }
    }

    /** Two handles are equal when they share the same {@link #id()}. */
    @Override
    public boolean equals(Object o) {
        return o instanceof WalletImpl other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Wallet[" + id + "]";
    }
}
