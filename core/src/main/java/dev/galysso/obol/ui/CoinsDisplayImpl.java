package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsDisplay;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.internal.Listeners;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The single {@link CoinsDisplay}: a registry of overlays per viewer over an
 * {@link OverlayHuds} that does the actual drawing.
 *
 * <p>Plain JDK code: the server is behind {@link OverlayHuds}, so this class
 * is unit-tested with a fake one.</p>
 */
public final class CoinsDisplayImpl implements CoinsDisplay {

    private final OverlayHuds huds;
    private final Listeners listeners;
    private final Map<UUID, Set<Overlay>> byViewer = new ConcurrentHashMap<>();
    private final AtomicLong nextKey = new AtomicLong();

    public CoinsDisplayImpl(OverlayHuds huds, Listeners listeners) {
        this.huds = Objects.requireNonNull(huds, "huds");
        this.listeners = Objects.requireNonNull(listeners, "listeners");
    }

    @Override
    public CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins, CoinsFormat format) {
        Objects.requireNonNull(coins, "coins");
        Overlay overlay = open(viewer, position, null, format);
        overlay.show(coins);
        return overlay;
    }

    @Override
    public CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet, CoinsFormat format) {
        Objects.requireNonNull(wallet, "wallet");
        Overlay overlay = open(viewer, position, wallet, format);
        // Subscribed before the first read: a change between the read and
        // the subscription is then refreshed rather than lost.
        listeners.add(overlay.subscribe());
        try {
            overlay.show(null);
        } catch (RuntimeException | Error e) {
            forget(overlay);
            throw e;
        }
        return overlay;
    }

    private Overlay open(UUID viewer, ScreenPosition position, Wallet tracked, CoinsFormat format) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(format, "format");
        if (!HudTemplates.supports(format)) {
            throw new IllegalArgumentException("Format " + format + " has no on-screen template");
        }
        OverlayHud hud = huds.open(viewer, "obol:" + nextKey.incrementAndGet(), format)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Player " + viewer + " is not connected"));
        Overlay overlay = new Overlay(viewer, hud, position, tracked, this::forget);
        byViewer.computeIfAbsent(viewer, ignored -> ConcurrentHashMap.newKeySet()).add(overlay);
        return overlay;
    }

    /**
     * The viewer's session ended: every overlay of theirs is gone with it.
     * The HUD is not touched, there is no screen any more.
     */
    public void onDisconnect(UUID viewer) {
        Set<Overlay> overlays = byViewer.remove(viewer);
        if (overlays != null) {
            overlays.forEach(Overlay::dropped);
        }
    }

    /** {@return how many overlays the viewer has on screen} */
    int count(UUID viewer) {
        Set<Overlay> overlays = byViewer.get(viewer);
        return overlays == null ? 0 : overlays.size();
    }

    private void forget(Overlay overlay) {
        if (overlay.isTracking()) {
            listeners.remove(overlay.listener());
        }
        byViewer.computeIfPresent(overlay.viewer(), (viewer, overlays) -> {
            overlays.remove(overlay);
            return overlays.isEmpty() ? null : overlays;
        });
    }
}
