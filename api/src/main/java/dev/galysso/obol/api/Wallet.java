package dev.galysso.obol.api;

import dev.galysso.obol.api.event.CoinsChangedEvent;
import dev.galysso.obol.api.internal.ObolApiHolder;
import dev.galysso.obol.api.internal.ObolRuntime;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

/**
 * Something that holds coins: a player, a shop, a merchant NPC, a guild bank.
 *
 * <p>This is a template method class. Every rule (non-negative balance,
 * atomic read-modify-write, all-or-nothing transfers) is implemented once
 * here and is {@code final}; a subclass only provides an identity and the
 * two accessors to <em>its</em> storage variable:</p>
 *
 * <ul>
 *   <li>{@code extends StoredWallet} + {@link #id()}: Obol stores the balance
 *       in its own balance store.</li>
 *   <li>{@code extends Wallet} + {@link #id()} + {@link #loadCopper()} +
 *       {@link #saveCopper(long)}: the balance lives with the owning object
 *       (an ECS component, the plugin's own file…) and is persisted with
 *       it.</li>
 * </ul>
 *
 * <p>A wallet carries no state of its own: it is a handle on the storage
 * variable, and two instances with the same {@link #id()} are
 * interchangeable. Nothing is cached, so nothing can go out of sync.</p>
 *
 * <p>Every operation runs under the lock Obol keeps for this wallet's id,
 * whatever the storage. {@link Wallet#transferTo} takes both locks in the
 * global order of {@link WalletId#storageKey()}, so cross transfers cannot
 * deadlock.</p>
 *
 * <p>Every accepted write is reported to the {@link CoinsListener}s
 * subscribed on {@link ObolApi}, as a {@link CoinsChangedEvent}, once the
 * lock is released. Again whatever the storage: this is what makes a
 * third-party wallet a first-class citizen for displays and other plugins.</p>
 *
 * <p>The storage hooks trade in {@code long} because that is what a codec, a
 * component or a file persists; everything above them is typed
 * {@link Coins} so that no raw number can be mistaken for money.</p>
 */
public abstract class Wallet {

    protected Wallet() {
    }

    /**
     * {@return the stable identity of this wallet}
     *
     * <p>Used as the lock key, as the storage key for a {@link StoredWallet},
     * and in diagnostics. Must not change over the life of the object.</p>
     */
    public abstract WalletId id();

    /**
     * Reads the storage variable, in copper.
     *
     * <p>Called under the wallet lock and never cached by Obol: the subclass
     * is the only source of truth. A negative value read here is treated as
     * corruption and surfaces as an {@link IllegalStateException} from the
     * public operation.</p>
     *
     * @return the stored balance, in copper
     */
    protected abstract long loadCopper();

    /**
     * Writes the storage variable, in copper.
     *
     * <p>Called under the wallet lock, after each accepted change. The
     * subclass decides when the write becomes durable: immediately, or
     * carried by the persistence of the host entity. An exception thrown here
     * propagates to the caller; in a transfer, the other wallet is restored
     * first.</p>
     *
     * @param copper the new balance, never negative
     */
    protected abstract void saveCopper(long copper);

    /**
     * {@return the current balance}
     *
     * @throws IllegalStateException if the storage holds a negative value
     */
    public final Coins balance() {
        Lock lock = runtime().lockFor(id());
        lock.lock();
        try {
            return Coins.ofCopper(load());
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@return whether the balance covers {@code amount}}
     *
     * <p>A convenience for display and pre-checks only: the answer can change
     * before the next call. {@link #withdraw} and {@link #transferTo} do
     * their own check under the lock.</p>
     *
     * @param amount the amount to cover
     */
    public final boolean canAfford(Coins amount) {
        return balance().covers(amount);
    }

    /**
     * Credits {@code amount} and returns the new balance.
     *
     * <p>Cannot fail on business grounds. Overflowing a {@code long} of copper
     * is a bug, not a case, and surfaces as an {@link ArithmeticException}
     * without writing.</p>
     *
     * @param amount the amount to add
     * @return the balance after the deposit
     * @throws IllegalStateException if the storage holds a negative value
     */
    public final Coins deposit(Coins amount) {
        Objects.requireNonNull(amount, "amount");
        ObolRuntime runtime = runtime();
        Coins before;
        Coins after;
        Lock lock = runtime.lockFor(id());
        lock.lock();
        try {
            before = Coins.ofCopper(load());
            after = before.plus(amount);
            saveCopper(after.copper());
        } finally {
            lock.unlock();
        }
        publish(runtime, before, after);
        return after;
    }

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
     * @throws IllegalStateException if the storage holds a negative value
     */
    public final boolean withdraw(Coins amount) {
        Objects.requireNonNull(amount, "amount");
        ObolRuntime runtime = runtime();
        Coins before;
        Coins after;
        Lock lock = runtime.lockFor(id());
        lock.lock();
        try {
            before = Coins.ofCopper(load());
            Optional<Coins> covered = before.minus(amount);
            if (covered.isEmpty()) {
                return false;
            }
            after = covered.get();
            saveCopper(after.copper());
        } finally {
            lock.unlock();
        }
        publish(runtime, before, after);
        return true;
    }

    /**
     * Debits {@code this} and credits {@code to}, or does nothing.
     *
     * <p>Either both balances move or neither does: with insufficient funds
     * nothing is written and {@code false} is returned; if crediting
     * {@code to} throws (an exception in a third-party
     * {@link #saveCopper(long)}), the debit is rolled back before the
     * exception propagates.</p>
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
     * @throws IllegalStateException if either storage holds a negative value
     * @throws ArithmeticException   if the credit would overflow; nothing is
     *                               written
     */
    public final boolean transferTo(Wallet to, Coins amount) {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        if (id().equals(to.id())) {
            return canAfford(amount);
        }
        ObolRuntime runtime = runtime();
        // Global order: the lexicographically smaller key is locked first,
        // whichever side is paying. Two threads crossing A->B and B->A then
        // contend on the same first lock instead of deadlocking.
        boolean thisFirst = id().storageKey().compareTo(to.id().storageKey()) < 0;
        Lock first = runtime.lockFor(thisFirst ? id() : to.id());
        Lock second = runtime.lockFor(thisFirst ? to.id() : id());
        Transfer done;
        first.lock();
        try {
            second.lock();
            try {
                done = transferLocked(to, amount);
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }
        if (done == null) {
            return false;
        }
        publish(runtime, done.fromBefore, done.fromAfter);
        to.publish(runtime, done.toBefore, done.toAfter);
        return true;
    }

    /** What a completed transfer wrote, carried out of the locked section. */
    private record Transfer(Coins fromBefore, Coins fromAfter, Coins toBefore, Coins toAfter) {
    }

    private Transfer transferLocked(Wallet to, Coins amount) {
        Coins fromBefore = Coins.ofCopper(load());
        Optional<Coins> fromAfter = fromBefore.minus(amount);
        if (fromAfter.isEmpty()) {
            return null;
        }
        // Computed before any write so that an overflow leaves both intact.
        Coins toBefore = Coins.ofCopper(to.load());
        Coins toAfter = toBefore.plus(amount);
        saveCopper(fromAfter.get().copper());
        try {
            to.saveCopper(toAfter.copper());
        } catch (RuntimeException | Error e) {
            try {
                saveCopper(fromBefore.copper());
            } catch (RuntimeException | Error rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        }
        return new Transfer(fromBefore, fromAfter.get(), toBefore, toAfter);
    }

    private long load() {
        long copper = loadCopper();
        if (copper < 0) {
            throw new IllegalStateException(
                    "Wallet " + id() + " holds a negative balance: " + copper);
        }
        return copper;
    }

    /**
     * Reports a write, outside the lock. A write that moved nothing (a
     * deposit of zero) is not an event.
     */
    private void publish(ObolRuntime runtime, Coins before, Coins after) {
        if (!before.equals(after)) {
            runtime.publish(new CoinsChangedEvent(id(), before, after));
        }
    }

    /**
     * Resolved once per operation so that the lock and the event go to the
     * same runtime even if Obol is uninstalled in between.
     */
    private static ObolRuntime runtime() {
        return ObolApiHolder.runtime();
    }

    /**
     * Two wallets are equal when they are of the same class and share the
     * same {@link #id()}.
     */
    @Override
    public final boolean equals(Object o) {
        return o != null
                && o.getClass() == getClass()
                && id().equals(((Wallet) o).id());
    }

    @Override
    public final int hashCode() {
        return id().hashCode();
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "[" + id() + "]";
    }
}
