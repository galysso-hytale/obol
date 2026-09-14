package dev.galysso.obol.api;

/**
 * The storage Obol provides for wallets that do not carry their own.
 *
 * <p>This is deliberately a dumb, persistent {@code Map<WalletId, long>}: no
 * rule lives here. Non-negativity, atomicity and transfers are enforced by
 * {@link Wallet}, so that a wallet stored elsewhere gets exactly the same
 * guarantees as one stored here. Day-to-day code goes through
 * {@link StoredWallet}; this interface is exposed on {@link ObolApi} for
 * administration and migrations.</p>
 *
 * <p>Implementations are thread-safe for individual calls. Read-modify-write
 * sequences must be done under the wallet lock, which is exactly what
 * {@link Wallet} does; calling {@link #set} directly bypasses that lock and
 * any listener.</p>
 */
public interface BalanceStore {

    /**
     * {@return the stored balance, or {@link Coins#ZERO} if the id is unknown}
     *
     * @param id the wallet identity
     */
    Coins balance(WalletId id);

    /**
     * Stores a balance, creating the entry if needed. A zero balance is kept,
     * not removed.
     *
     * @param id    the wallet identity
     * @param coins the new balance
     * @return the previous balance, {@link Coins#ZERO} if the id was unknown
     */
    Coins set(WalletId id, Coins coins);

    /**
     * {@return whether the store holds an entry for this id, even at zero}
     *
     * @param id the wallet identity
     */
    boolean exists(WalletId id);

    /**
     * Removes an entry. Meant for a third-party wallet whose owning object
     * disappears for good; the balance is lost.
     *
     * @param id the wallet identity
     * @return {@code true} if an entry was removed
     */
    boolean delete(WalletId id);
}
