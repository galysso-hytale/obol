package dev.galysso.obol.ui;

import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.internal.ObolBackend;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Obol's own HUD: every player's balance, tracked, for as long as they are
 * in a world. The first consumer of {@code Obol.track}, through the
 * {@link ObolBackend} directly.
 *
 * <p>Up by default, the HUD is how a player sees their balance. A mod that
 * draws the balance in a HUD of its own takes it down for everyone
 * ({@link #shown}); the players in a world are remembered meanwhile, so
 * that it comes back for them. The overlay does not outlive the session;
 * it is put back each time the player is ready in a world.</p>
 */
public final class PlayerHuds {

    /** Where Obol puts its own HUD. */
    static final ScreenPosition POSITION = ScreenPosition.topRight(20, 20);

    private final ObolBackend backend;
    /** The players in a world, whether or not their HUD is up. */
    private final Set<UUID> inWorld = new HashSet<>();
    private final Map<UUID, CoinsOverlay> overlays = new HashMap<>();
    private boolean shown = true;

    public PlayerHuds(ObolBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /**
     * The player is in a world: put the HUD up, unless it is still there
     * (a world change within one session) or a mod took it down. Nobody
     * else hides this overlay, so an entry in the map means it is on the
     * screen until the session ends and {@link #onDisconnect} removes it.
     *
     * @throws IllegalArgumentException if the display does not see the
     *                                  player yet
     */
    public synchronized void onReady(UUID player) {
        if (shown && !overlays.containsKey(player)) {
            overlays.put(player, track(player));
        }
        inWorld.add(player);
    }

    /** The session is over; the display has already dropped the overlay. */
    public synchronized void onDisconnect(UUID player) {
        inWorld.remove(player);
        overlays.remove(player);
    }

    /**
     * Takes the HUD down for every player in a world and keeps it down, or
     * puts it back for all of them. Nothing happens when it already is as
     * asked.
     */
    public synchronized void shown(boolean shown) {
        if (this.shown == shown) {
            return;
        }
        this.shown = shown;
        if (shown) {
            for (UUID player : inWorld) {
                overlays.put(player, track(player));
            }
        } else {
            overlays.values().forEach(CoinsOverlay::hide);
            overlays.clear();
        }
    }

    private CoinsOverlay track(UUID player) {
        return backend.track(player, POSITION, backend.wallet(WalletId.player(player)));
    }
}
