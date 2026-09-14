package dev.galysso.obol.api;

import dev.galysso.obol.api.event.CoinsChangedEvent;

/**
 * Something that holds coins: a player, a shop, a merchant NPC, a guild bank.
 *
 * <p>Obtained from {@link Obol#wallet(WalletId)} or
 * {@link Obol#playerWallet(java.util.UUID)}. A wallet carries no state of
 * its own: it is a handle on an entry of Obol's balance store, and two
 * handles with the same {@link #id()} are interchangeable and equal. Nothing
 * is cached, so nothing can go out of sync, and there is nothing to register
 * or look up: naming an id is enough, an unknown one simply holds
 * {@link Coins#ZERO}.</p>
 *
 * <p>Every operation runs under the lock Obol keeps for this wallet's id, so
 * concurrent callers never see a torn read-modify-write.
 * {@link #transferTo} takes both locks in a global order, so cross transfers
 * cannot deadlock.</p>
 *
 * <p>Every accepted write is reported to the {@link CoinsListener}s
 * subscribed through {@link Obol#addListener}, as a
 * {@link CoinsChangedEvent}, once the lock is released. A write that moves
 * nothing (a deposit of zero) is not an event.</p>
 *
 * <p>Balances are persisted by Obol; an entry is never removed on its own.
 * Call {@link #clear()} when the owner of a wallet disappears for good.</p>
 */
public interface Wallet {

    /**
     * {@return the stable identity of this wallet}
     */
    WalletId id();

    /**
     * {@return the current balance}
     */
    Coins balance();

    /**
     * {@return whether the balance covers {@code amount}}
     *
     * <p>A convenience for display and pre-checks only: the answer can change
     * before the next call. {@link #withdraw} and {@link #transferTo} do
     * their own check under the lock.</p>
     *
     * @param amount the amount to cover
     * @throws NullPointerException if {@code amount} is {@code null}
     */
    boolean canAfford(Coins amount);

    /**
     * Credits {@code amount} and returns the new balance.
     *
     * <p>Cannot fail on business grounds. Overflowing a {@code long} of copper
     * is a bug, not a case, and surfaces as an {@link ArithmeticException}
     * without writing.</p>
     *
     * @param amount the amount to add
     * @return the balance after the deposit
     * @throws NullPointerException if {@code amount} is {@code null}
     * @throws ArithmeticException  if the balance would overflow; nothing is
     *                              written
     */
    Coins deposit(Coins amount);

    /**
     * Debits {@code amount} if the balance covers it.
     *
     * <p><strong>The return value is the contract: never ignore it.</strong>
     * Insufficient funds are a normal case, not an error, so no exception is
     * thrown.</p>
     *
     * @param amount the amount to remove
     * @return {@code true} if debited; {@code false} otherwise, and the balance
     *         has not moved
     * @throws NullPointerException if {@code amount} is {@code null}
     */
    boolean withdraw(Coins amount);

    /**
     * Debits {@code this} and credits {@code to}, or does nothing.
     *
     * <p>Either both balances move or neither does: with insufficient funds
     * nothing is written and {@code false} is returned.</p>
     *
     * <p>A transfer from a wallet to itself (same {@link #id()}) writes
     * nothing and returns whether the balance covers {@code amount}.</p>
     *
     * <p>A successful transfer publishes two events once both locks are
     * released: the debit of {@code this}, then the credit of {@code to}.</p>
     *
     * <p><strong>The return value is the contract: never ignore it.</strong></p>
     *
     * @param to     the receiving wallet
     * @param amount the amount to move
     * @return {@code false} if the funds are insufficient; neither balance has
     *         then moved
     * @throws NullPointerException if an argument is {@code null}
     * @throws ArithmeticException  if the credit would overflow; nothing is
     *                              written
     */
    boolean transferTo(Wallet to, Coins amount);

    /**
     * Sets the balance to zero and removes the entry from Obol's store.
     *
     * <p>Meant for a wallet whose owner disappears for good: the coins are
     * lost, not moved. Publishes a {@link CoinsChangedEvent} down to
     * {@link Coins#ZERO} if the wallet held anything. Idempotent.</p>
     */
    void clear();
}
