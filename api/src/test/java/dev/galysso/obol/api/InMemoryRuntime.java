package dev.galysso.obol.api;

import dev.galysso.obol.api.event.CoinsChangedEvent;
import dev.galysso.obol.api.internal.ObolApiHolder;
import dev.galysso.obol.api.internal.ObolRuntime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * What {@code core} provides at runtime, reduced to what {@link Wallet}
 * needs: one reentrant lock per id, a map-backed {@link BalanceStore} and a
 * plain listener list. Installed in {@link ObolApiHolder} by the tests, no
 * server in the loop.
 *
 * <p>Every published event is also kept in {@link #events}, with the lock
 * state at publication time in {@link #publishedUnderLock}, so that a test
 * can check what {@link Wallet} reported and when.</p>
 */
final class InMemoryRuntime implements ObolApi, ObolRuntime {

    private final Map<WalletId, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<WalletId, Long> store = new ConcurrentHashMap<>();
    private final List<CoinsListener> listeners = new CopyOnWriteArrayList<>();
    final List<CoinsChangedEvent> events = new CopyOnWriteArrayList<>();
    final List<Boolean> publishedUnderLock = new CopyOnWriteArrayList<>();

    static InMemoryRuntime install() {
        InMemoryRuntime runtime = new InMemoryRuntime();
        ObolApiHolder.install(runtime);
        return runtime;
    }

    @Override
    public Lock lockFor(WalletId id) {
        return locks.computeIfAbsent(id, ignored -> new ReentrantLock());
    }

    /** Whether the calling thread holds the lock of that wallet right now. */
    boolean holdsLock(WalletId id) {
        ReentrantLock lock = locks.get(id);
        return lock != null && lock.isHeldByCurrentThread();
    }

    @Override
    public void addListener(CoinsListener listener) {
        listeners.add(listener);
    }

    @Override
    public boolean removeListener(CoinsListener listener) {
        return listeners.remove(listener);
    }

    /** Records, then dispatches without any safety net: that net is core's. */
    @Override
    public void publish(CoinsChangedEvent event) {
        events.add(event);
        publishedUnderLock.add(holdsLock(event.wallet()));
        for (CoinsListener listener : listeners) {
            listener.onCoinsChanged(event);
        }
    }

    @Override
    public BalanceStore balances() {
        return new BalanceStore() {
            @Override
            public Coins balance(WalletId id) {
                return Coins.ofCopper(store.getOrDefault(id, 0L));
            }

            @Override
            public Coins set(WalletId id, Coins coins) {
                Long previous = store.put(id, coins.copper());
                return previous == null ? Coins.ZERO : Coins.ofCopper(previous);
            }

            @Override
            public boolean exists(WalletId id) {
                return store.containsKey(id);
            }

            @Override
            public boolean delete(WalletId id) {
                return store.remove(id) != null;
            }
        };
    }
}
