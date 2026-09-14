package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsListener;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.Wallet;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The handle handed to callers. All state changes are serialized on the
 * instance, so that what reaches the client follows the order of the calls
 * — including the refreshes of a tracking overlay, which may come from any
 * thread that moved money.
 */
final class Overlay implements CoinsOverlay {

    private final UUID viewer;
    private final OverlayHud hud;
    private final Wallet tracked;
    private final Consumer<Overlay> onHidden;
    private ScreenPosition position;
    private boolean visible;
    /** The subscription of a tracking overlay, until hidden. */
    private volatile CoinsListener listener;

    /**
     * @param tracked  the wallet to follow, or {@code null} for a fixed
     *                 amount
     * @param onHidden called once, when the overlay leaves the screen for
     *                 any reason, so the registry can forget it
     */
    Overlay(UUID viewer, OverlayHud hud, ScreenPosition position,
            Wallet tracked, Consumer<Overlay> onHidden) {
        this.viewer = viewer;
        this.hud = hud;
        this.position = position;
        this.tracked = tracked;
        this.onHidden = onHidden;
    }

    UUID viewer() {
        return viewer;
    }

    boolean isTracking() {
        return tracked != null;
    }

    /**
     * First display. Reads the wallet, for a tracking overlay; if that read
     * throws, the overlay never becomes visible.
     */
    synchronized void show(Coins fixed) {
        Coins coins = tracked == null ? fixed : tracked.balance();
        visible = true;
        hud.show(position, coins);
    }

    /**
     * The listener a tracking overlay subscribes: refreshes on any change of
     * the followed wallet. Kept so that it can be unsubscribed.
     */
    CoinsListener subscribe() {
        listener = event -> {
            if (event.wallet().equals(tracked.id())) {
                refresh();
            }
        };
        return listener;
    }

    /** The listener to unsubscribe, or {@code null} for a fixed overlay. */
    CoinsListener listener() {
        return listener;
    }

    /**
     * Re-reads the balance under the overlay's monitor: two refreshes that
     * race read in the same order they send, so the last one on the wire is
     * the last state read.
     */
    synchronized void refresh() {
        if (visible) {
            hud.setCoins(tracked.balance());
        }
    }

    @Override
    public synchronized void update(Coins coins) {
        Objects.requireNonNull(coins, "coins");
        if (visible && tracked == null) {
            hud.setCoins(coins);
        }
    }

    @Override
    public synchronized void move(ScreenPosition position) {
        Objects.requireNonNull(position, "position");
        this.position = position;
        if (visible) {
            hud.move(position);
        }
    }

    @Override
    public void hide() {
        if (leaveScreen()) {
            hud.hide();
        }
    }

    /** The viewer is gone: forget the overlay without touching the HUD. */
    void dropped() {
        leaveScreen();
    }

    private boolean leaveScreen() {
        synchronized (this) {
            if (!visible) {
                return false;
            }
            visible = false;
        }
        // Outside the monitor: the registry unsubscribes the listener, and a
        // refresh in flight must not have to wait for that.
        onHidden.accept(this);
        return true;
    }

    @Override
    public synchronized boolean isVisible() {
        return visible;
    }
}
