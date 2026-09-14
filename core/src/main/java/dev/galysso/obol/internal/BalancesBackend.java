package dev.galysso.obol.internal;

import java.util.Map;

/**
 * Where {@link BalanceStoreImpl} balances are kept between two runs of the
 * server.
 *
 * <p>Kept behind an interface so that {@link BalancesPersistence} is plain
 * JDK code, tested with an in-memory backend; the server-bound
 * implementation is {@link ConfigBalancesBackend}.</p>
 */
public interface BalancesBackend {

    /**
     * Reads from durable storage.
     *
     * @return balances keyed by {@code WalletId.storageKey()}; empty if
     *         nothing was ever saved
     * @throws RuntimeException if the storage exists but cannot be read
     */
    Map<String, Long> load();

    /**
     * Writes durably, returning once written.
     *
     * @param balances the full state to persist; the caller keeps no
     *                 reference to it afterwards
     * @throws RuntimeException if the write failed; nothing is assumed about
     *                          what is on disk then
     */
    void save(Map<String, Long> balances);
}
