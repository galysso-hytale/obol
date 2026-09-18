package dev.galysso.obol.api.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsListener;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.Obol;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;

import java.util.UUID;

/**
 * What the static facade {@link Obol} delegates to, implemented by
 * {@code core}. Same methods, same contracts, minus the {@code static}.
 *
 * @apiNote Not part of the public API. It is only {@code public} because the
 *          implementation lives in another module. Third-party plugins go
 *          through {@link Obol}; their unit tests may install a fake one
 *          with {@link ObolBackendHolder#install}.
 */
public interface ObolBackend {

    /** See {@link Obol#wallet(WalletId)}. */
    Wallet wallet(WalletId id);

    /** See {@link Obol#show(UUID, ScreenPosition, Coins)}. */
    CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins);

    /** See {@link Obol#track(UUID, ScreenPosition, Wallet)}. */
    CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet);

    /** See {@link Obol#hud(boolean)}. */
    void hud(boolean shown);

    /** See {@link Obol#addListener(CoinsListener)}. */
    void addListener(CoinsListener listener);

    /** See {@link Obol#removeListener(CoinsListener)}. */
    boolean removeListener(CoinsListener listener);
}
