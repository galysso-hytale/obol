package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.WalletId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Obol's balance store: a concurrent map of balances keyed by
 * {@link WalletId#toString()} ({@code kind:key}), plus a dirty flag for the
 * persistence layer.
 *
 * <p>Deliberately a dumb {@code Map<WalletId, long>}: no rule lives here.
 * Non-negativity, atomicity, transfers and events are {@link WalletImpl}'s,
 * which calls {@link #set} under the wallet lock. Nothing in this class does
 * I/O; every call is a map operation. A zero balance is kept, not
 * removed.</p>
 */
public final class BalanceStoreImpl {

    private final ConcurrentMap<String, Long> balances = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();

    /** {@return the stored balance, or {@link Coins#ZERO} if the id is unknown} */
    public Coins balance(WalletId id) {
        Long copper = balances.get(key(id));
        return copper == null ? Coins.ZERO : Coins.ofCopper(copper);
    }

    /**
     * Stores a balance, creating the entry if needed.
     *
     * @return the previous balance, {@link Coins#ZERO} if the id was unknown
     */
    public Coins set(WalletId id, Coins coins) {
        Objects.requireNonNull(coins, "coins");
        Long previous = balances.put(key(id), coins.copper());
        dirty.set(true);
        return previous == null ? Coins.ZERO : Coins.ofCopper(previous);
    }

    /** {@return whether the store holds an entry for this id, even at zero} */
    public boolean exists(WalletId id) {
        return balances.containsKey(key(id));
    }

    /**
     * Removes an entry.
     *
     * @return {@code true} if an entry was removed
     */
    public boolean delete(WalletId id) {
        boolean removed = balances.remove(key(id)) != null;
        if (removed) {
            dirty.set(true);
        }
        return removed;
    }

    /**
     * Replaces every balance with the given ones, as read from durable
     * storage. Does not mark the store dirty: what was just loaded is by
     * definition what is on disk.
     *
     * @param loaded balances keyed by {@link WalletId#toString()}
     * @throws IllegalArgumentException if a value is negative, which cannot
     *                                  come from Obol and is treated as a
     *                                  corrupt file rather than silently
     *                                  clamped
     */
    public void load(Map<String, Long> loaded) {
        Objects.requireNonNull(loaded, "loaded");
        loaded.forEach((key, copper) -> {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(copper, "copper");
            if (copper < 0) {
                throw new IllegalArgumentException(
                        "Negative balance for " + key + ": " + copper);
            }
        });
        balances.clear();
        balances.putAll(loaded);
    }

    /**
     * Whether something changed since the last {@link #snapshot()}.
     * Read by the persistence layer to skip a save that would write the same
     * file again.
     */
    public boolean isDirty() {
        return dirty.get();
    }

    /**
     * Marks the store dirty again. Called by the persistence layer when a
     * save fails after {@link #snapshot()} cleared the flag, so that the next
     * save retries instead of believing the disk is up to date.
     */
    public void markDirty() {
        dirty.set(true);
    }

    /**
     * Copies the current balances and clears the dirty flag. The copy is what
     * gets serialized: a slow save never holds up a transaction, and never
     * observes a map in the middle of a mutation.
     *
     * <p>The flag is cleared before the copy is taken, so a {@link #set} that
     * races with the snapshot is either in the copy or leaves the store dirty
     * for the next one; it cannot be lost.</p>
     */
    public Map<String, Long> snapshot() {
        dirty.set(false);
        return new HashMap<>(balances);
    }

    private static String key(WalletId id) {
        return Objects.requireNonNull(id, "id").toString();
    }
}
