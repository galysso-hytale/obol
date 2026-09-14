package dev.galysso.obol.api;

import dev.galysso.obol.api.event.CoinsChangedEvent;

/**
 * Callback for plugins reacting to balance changes.
 *
 * <p>Subscribe through {@link ObolApi#addListener(CoinsListener)}. A listener
 * is called on the thread that made the change, right after the wallet lock
 * was released: keep it short, and never throw. An exception is logged by
 * Obol and does not reach the code that moved the money, nor does it stop the
 * other listeners.</p>
 */
@FunctionalInterface
public interface CoinsListener {

    /**
     * Called after a balance moved.
     *
     * @param event what changed
     */
    void onCoinsChanged(CoinsChangedEvent event);
}
