package dev.galysso.obol.ui;

import dev.galysso.obol.api.CoinsDisplay;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.PlayerWallet;
import dev.galysso.obol.api.ScreenPosition;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Obol's own HUD: every player's balance, tracked, for as long as they are
 * in a world. The first consumer of {@link CoinsDisplay}.
 *
 * <p>Always on: the HUD is the only way a player sees their balance, so
 * there is nothing to opt out of. The overlay does not outlive the session;
 * it is put back each time the player is ready in a world.</p>
 */
public final class PlayerHuds {

    /** Where Obol puts its own HUD. */
    static final ScreenPosition POSITION = ScreenPosition.topRight(20, 20);

    private final CoinsDisplay display;
    private final Map<UUID, CoinsOverlay> shown = new ConcurrentHashMap<>();

    public PlayerHuds(CoinsDisplay display) {
        this.display = Objects.requireNonNull(display, "display");
    }

    /**
     * The player is in a world: put the HUD up, unless it is still there
     * (a world change within one session).
     *
     * @throws IllegalArgumentException if the display does not see the
     *                                  player yet
     */
    public void onReady(UUID player) {
        CoinsOverlay current = shown.get(player);
        if (current != null && current.isVisible()) {
            return;
        }
        shown.put(player, display.track(player, POSITION, new PlayerWallet(player), CoinsFormat.STANDARD));
    }

    /** The session is over; the display has already dropped the overlay. */
    public void onDisconnect(UUID player) {
        shown.remove(player);
    }
}
