package dev.galysso.obol.internal;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Where {@link BalanceStoreImpl} balances and {@link HudPreferences} are kept
 * between two runs of the server.
 *
 * <p>Kept behind an interface so that {@link BalancesPersistence} is plain
 * JDK code, tested with an in-memory backend; the server-bound
 * implementation is {@link ConfigBalancesBackend}.</p>
 */
public interface BalancesBackend {

    /**
     * Everything the backend holds, as one unit: both parts live in the same
     * file and are read and written together.
     *
     * @param balances   balances keyed by {@code WalletId.storageKey()}
     * @param hudEnabled players who turned the coins HUD on
     */
    record Snapshot(Map<String, Long> balances, Set<UUID> hudEnabled) {
    }

    /**
     * Reads from durable storage.
     *
     * @return what was saved; empty parts if nothing was ever saved
     * @throws RuntimeException if the storage exists but cannot be read
     */
    Snapshot load();

    /**
     * Writes durably, returning once written.
     *
     * @param snapshot the full state to persist; the caller keeps no
     *                 reference to it afterwards
     * @throws RuntimeException if the write failed; nothing is assumed about
     *                          what is on disk then
     */
    void save(Snapshot snapshot);
}
