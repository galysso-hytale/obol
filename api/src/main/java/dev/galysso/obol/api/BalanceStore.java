package dev.galysso.obol.api;

import dev.galysso.obol.api.event.CoinsChangedEvent;

/**
 * The storage Obol provides for wallets that do not carry their own.
 *
 * <p>This is deliberately a dumb, persistent {@code Map<WalletId, long>}: no
 * business rule lives here. Non-negativity, atomicity and transfers are
 * enforced by {@link Wallet}, so that a wallet stored elsewhere gets exactly
 * the same guarantees as one stored here. Day-to-day code goes through
 * {@link StoredWallet}; this interface is exposed on {@link ObolApi} for
 * administration and migrations.</p>
 *
 * <p>What it does share with {@link Wallet} is consistency: {@link #set} and
 * {@link #delete} take the wallet lock and publish a {@link CoinsChangedEvent}
 * when the balance changes, so that a display tracking the wallet, or any
 * other listener, never misses an administrative write. Reads are consistent
 * with writes in progress. Calls are individually thread-safe; a
 * read-modify-write sequence is not atomic here, which is what {@link Wallet}
 * is for.</p>
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
     * <p>Publishes a {@link CoinsChangedEvent} to the listeners if the
     * balance changed, on the calling thread, outside the lock.</p>
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
     * <p>Publishes a {@link CoinsChangedEvent} down to {@link Coins#ZERO} if
     * the removed entry held anything.</p>
     *
     * @param id the wallet identity
     * @return {@code true} if an entry was removed
     */
    boolean delete(WalletId id);
}
