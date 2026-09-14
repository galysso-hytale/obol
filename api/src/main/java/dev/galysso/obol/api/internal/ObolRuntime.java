package dev.galysso.obol.api.internal;

import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.event.CoinsChangedEvent;

import java.util.concurrent.locks.Lock;

/**
 * Services {@code core} provides to {@link dev.galysso.obol.api.Wallet}
 * without exposing them to third-party plugins.
 *
 * @apiNote Not part of the public API, like {@link ObolApiHolder}. It is only
 *          {@code public} because the implementation lives in another module.
 *          The implementation of {@code ObolApi} implements this interface
 *          too, and unit tests install an in-memory one.
 */
public interface ObolRuntime {

    /**
     * {@return the lock guarding every read-modify-write on that wallet}
     *
     * <p>The same id always maps to the same lock (or to locks that behave as
     * one), for the whole lifetime of the runtime. The lock is reentrant.</p>
     *
     * @param id the wallet identity
     */
    Lock lockFor(WalletId id);

    /**
     * Delivers an event to every subscribed listener.
     *
     * <p>Called by {@code Wallet} outside the wallet lock, once the write is
     * done. Must not throw: a failing listener is the implementation's
     * problem to report, not the caller's.</p>
     *
     * @param event the change to report
     */
    void publish(CoinsChangedEvent event);
}
