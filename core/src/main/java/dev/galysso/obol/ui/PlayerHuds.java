package dev.galysso.obol.ui;

import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.internal.ObolBackend;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Obol's own HUD: every player's balance, tracked, for as long as they are
 * in a world. The first consumer of {@code Obol.track}, through the
 * {@link ObolBackend} directly.
 *
 * <p>Always on: the HUD is the only way a player sees their balance, so
 * there is nothing to opt out of. The overlay does not outlive the session;
 * it is put back each time the player is ready in a world.</p>
 */
public final class PlayerHuds {

    /** Where Obol puts its own HUD. */
    static final ScreenPosition POSITION = ScreenPosition.topRight(20, 20);

    private final ObolBackend backend;
    private final Map<UUID, CoinsOverlay> shown = new ConcurrentHashMap<>();

    public PlayerHuds(ObolBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /**
     * The player is in a world: put the HUD up, unless it is still there
     * (a world change within one session). Nobody else hides this overlay,
     * so an entry in the map means it is on the screen until the session
     * ends and {@link #onDisconnect} removes it.
     *
     * @throws IllegalArgumentException if the display does not see the
     *                                  player yet
     */
    public void onReady(UUID player) {
        if (shown.containsKey(player)) {
            return;
        }
        shown.put(player, backend.track(player, POSITION, backend.wallet(WalletId.player(player))));
    }

    /** The session is over; the display has already dropped the overlay. */
    public void onDisconnect(UUID player) {
        shown.remove(player);
    }
}
