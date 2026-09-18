package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsListener;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.internal.ObolBackend;
import dev.galysso.obol.ui.CoinsDisplayImpl;
import dev.galysso.obol.ui.OverlayHuds;
import dev.galysso.obol.ui.PlayerHuds;

import java.util.UUID;

/**
 * The single {@link ObolBackend}, published to the static facade through
 * {@code ObolBackendHolder}: the locks, the store, the listeners and the
 * display, wired together.
 */
public final class ObolBackendImpl implements ObolBackend {

    private final WalletLocks locks = new WalletLocks();
    private final BalanceStoreImpl store = new BalanceStoreImpl();
    private final Listeners listeners;
    private final CoinsDisplayImpl display;
    private final PlayerHuds playerHuds;

    /**
     * @param listenerFailures where a listener that throws is reported
     * @param huds             what draws the overlays
     */
    public ObolBackendImpl(Listeners.FailureReporter listenerFailures, OverlayHuds huds) {
        this.listeners = new Listeners(listenerFailures);
        this.display = new CoinsDisplayImpl(huds, listeners);
        this.playerHuds = new PlayerHuds(this);
    }

    /** Covariant on purpose: the commands reach {@link WalletImpl#set}. */
    @Override
    public WalletImpl wallet(WalletId id) {
        return new WalletImpl(id, store, locks, listeners);
    }

    @Override
    public CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins) {
        return display.show(viewer, position, coins);
    }

    @Override
    public CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet) {
        return display.track(viewer, position, wallet);
    }

    @Override
    public void hud(boolean shown) {
        playerHuds.shown(shown);
    }

    @Override
    public void addListener(CoinsListener listener) {
        listeners.add(listener);
    }

    @Override
    public boolean removeListener(CoinsListener listener) {
        return listeners.remove(listener);
    }

    /** Obol's own HUD, for the plugin to forward ready events and disconnects to. */
    public PlayerHuds playerHuds() {
        return playerHuds;
    }

    /** The display, for the plugin to forward disconnects to. */
    public CoinsDisplayImpl display() {
        return display;
    }

    /** The raw store, for the persistence layer. */
    public BalanceStoreImpl storedBalances() {
        return store;
    }
}
