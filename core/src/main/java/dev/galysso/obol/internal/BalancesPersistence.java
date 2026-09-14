package dev.galysso.obol.internal;

import java.util.Map;
import java.util.Objects;

/**
 * Moves balances between the in-memory store and a {@link BalancesBackend}.
 *
 * <p>Saves are serialized on one lock so that the periodic tick, a player
 * disconnect and the shutdown never write the same file at once, and so
 * that snapshots reach the disk in the order they were taken. Transactions
 * are never held up: {@link BalanceStoreImpl#snapshot()} copies the map,
 * and the copy is what gets written.</p>
 */
public final class BalancesPersistence {

    private final BalanceStoreImpl store;
    private final BalancesBackend backend;
    private final Object saveLock = new Object();

    public BalancesPersistence(BalanceStoreImpl store, BalancesBackend backend) {
        this.store = Objects.requireNonNull(store, "store");
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /**
     * Replaces the store's content with what the backend holds.
     *
     * @return the number of balances loaded
     * @throws RuntimeException if the backend cannot be read or holds a
     *                          negative balance; the store is left untouched
     */
    public int load() {
        Map<String, Long> loaded = backend.load();
        store.load(loaded);
        return loaded.size();
    }

    /**
     * Saves if something changed since the last save.
     *
     * @return {@code true} if a save was performed
     * @throws RuntimeException if the backend failed; the store is marked
     *                          dirty again so the next call retries
     */
    public boolean saveIfDirty() {
        synchronized (saveLock) {
            if (!store.isDirty()) {
                return false;
            }
            saveLocked();
            return true;
        }
    }

    /**
     * Saves unconditionally, for shutdown.
     *
     * @throws RuntimeException if the backend failed; the store is marked
     *                          dirty again
     */
    public void save() {
        synchronized (saveLock) {
            saveLocked();
        }
    }

    private void saveLocked() {
        Map<String, Long> snapshot = store.snapshot();
        try {
            backend.save(snapshot);
        } catch (RuntimeException | Error e) {
            store.markDirty();
            throw e;
        }
    }
}
