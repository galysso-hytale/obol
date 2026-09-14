package dev.galysso.obol.api.event;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsListener;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;

import java.util.Objects;

/**
 * A balance moved.
 *
 * <p>Published by {@link Wallet} after each accepted write, for every kind of
 * storage: the wallet stored by Obol and the one living in a third-party
 * object are reported alike. A write that changes nothing (a deposit of
 * zero) is not reported, so {@code before} and {@code after} always
 * differ.</p>
 *
 * <p>The event is a snapshot: by the time a {@link CoinsListener} sees it, the
 * balance may already have moved again. Read {@code wallet.balance()} when the
 * current value matters, {@link #after()} when the sequence of changes
 * does.</p>
 *
 * @param wallet the wallet whose balance moved
 * @param before the balance before the write
 * @param after  the balance after the write
 */
public record CoinsChangedEvent(WalletId wallet, Coins before, Coins after) {

    /**
     * @throws NullPointerException if any part is {@code null}
     */
    public CoinsChangedEvent {
        Objects.requireNonNull(wallet, "wallet");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    /**
     * {@return whether the balance went up}
     */
    public boolean increased() {
        return after.compareTo(before) > 0;
    }
}
