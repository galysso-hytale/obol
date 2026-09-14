package dev.galysso.obol.internal;

import dev.galysso.obol.api.BalanceStore;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.event.CoinsChangedEvent;

import java.util.Objects;
import java.util.concurrent.locks.Lock;

/**
 * The public face of {@link BalanceStoreImpl}: the same map, but every call
 * runs under the wallet lock and every change is published.
 *
 * <p>{@code Wallet} does not go through here (it holds the lock and publishes
 * itself); this is for the administrative path, {@code ObolApi.balances()},
 * so that {@code /obol set} and a migration are seen by a tracking display
 * exactly like a deposit is.</p>
 */
public final class GuardedBalanceStore implements BalanceStore {

    private final BalanceStoreImpl store;
    private final WalletLocks locks;
    private final Listeners listeners;

    public GuardedBalanceStore(BalanceStoreImpl store, WalletLocks locks, Listeners listeners) {
        this.store = Objects.requireNonNull(store, "store");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.listeners = Objects.requireNonNull(listeners, "listeners");
    }

    @Override
    public Coins balance(WalletId id) {
        Lock lock = locks.lockFor(id);
        lock.lock();
        try {
            return store.balance(id);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Coins set(WalletId id, Coins coins) {
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
    public boolean exists(WalletId id) {
        return store.exists(id);
    }

    @Override
    public boolean delete(WalletId id) {
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
        return removed;
    }

    /** Outside the lock, and only if something moved, like {@code Wallet}. */
    private void publish(WalletId id, Coins before, Coins after) {
        if (!before.equals(after)) {
            listeners.publish(new CoinsChangedEvent(id, before, after));
        }
    }
}
