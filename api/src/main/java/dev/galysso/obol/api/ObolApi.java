package dev.galysso.obol.api;

import dev.galysso.obol.api.internal.ObolApiHolder;

import java.util.Optional;

/**
 * Entry point of the Obol public API.
 *
 * <p>Third-party plugins obtain the singleton through {@link #get()} once Obol
 * has finished loading. Declare a manifest dependency so ordering is
 * guaranteed:</p>
 *
 * <pre>{@code
 * "Dependencies": { "Galysso:obol": ">=0.1.0" }
 * }</pre>
 *
 * <p>For an optional integration, declare it under
 * {@code OptionalDependencies} and use {@link #find()} instead.</p>
 */
public interface ObolApi {

    /**
     * {@return the storage Obol provides for {@link StoredWallet}s}
     *
     * <p>Meant for administration and migrations; regular code reads and
     * moves money through a {@link Wallet}.</p>
     */
    BalanceStore balances();

    /**
     * {@return the on-screen display of coins}
     */
    CoinsDisplay display();

    /**
     * Subscribes to balance changes of every wallet, whatever its storage.
     *
     * <p>Listeners run synchronously on the thread that moved the money,
     * outside the wallet lock, in subscription order. A listener that throws
     * is logged and skipped; the others still run and the caller of the
     * wallet operation never sees the exception. Adding the same listener
     * twice calls it twice.</p>
     *
     * @param listener the listener to add
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    void addListener(CoinsListener listener);

    /**
     * Removes a previously added listener.
     *
     * <p>A dispatch already in progress on another thread may still deliver
     * one last event to it.</p>
     *
     * @param listener the listener to remove
     * @return {@code true} if it was subscribed
     */
    boolean removeListener(CoinsListener listener);

    /**
     * {@return the running API instance}
     *
     * @throws IllegalStateException if Obol is not loaded, which means a
     *                               missing or misordered manifest dependency
     */
    static ObolApi get() {
        return ObolApiHolder.require();
    }

    /**
     * {@return the running API instance, or empty if Obol is absent}
     */
    static Optional<ObolApi> find() {
        return ObolApiHolder.find();
    }
}
