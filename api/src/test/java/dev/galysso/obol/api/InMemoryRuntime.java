package dev.galysso.obol.api;

import dev.galysso.obol.api.internal.ObolApiHolder;
import dev.galysso.obol.api.internal.ObolRuntime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * What {@code core} provides at runtime, reduced to what {@link Wallet}
 * needs: one reentrant lock per id and a map-backed {@link BalanceStore}. Installed
 * in {@link ObolApiHolder} by the tests, no server in the loop.
 */
final class InMemoryRuntime implements ObolApi, ObolRuntime {

    private final Map<WalletId, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<WalletId, Long> store = new ConcurrentHashMap<>();

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
