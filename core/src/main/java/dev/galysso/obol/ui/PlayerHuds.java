package dev.galysso.obol.ui;

import dev.galysso.obol.api.CoinsDisplay;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.PlayerWallet;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.internal.HudPreferences;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Obol's own HUD: the player's balance, tracked, for players who asked for
 * it with {@code /balance hud on}. The first consumer of {@link CoinsDisplay}.
 *
 * <p>The preference outlives the session; the overlay does not. It is put
 * back each time the player is ready in a world.</p>
 */
public final class PlayerHuds {

    /** Where Obol puts its own HUD. */
    static final ScreenPosition POSITION = ScreenPosition.topRight(20, 20);

    private final CoinsDisplay display;
    private final HudPreferences preferences;
    private final Map<UUID, CoinsOverlay> shown = new ConcurrentHashMap<>();

    public PlayerHuds(CoinsDisplay display, HudPreferences preferences) {
        this.display = Objects.requireNonNull(display, "display");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    /**
     * Turns the HUD on for a connected player and remembers it.
     *
     * @return {@code false} if it was already on
     * @throws IllegalArgumentException if the player is not connected and in
     *                                  a world
     */
    public boolean enable(UUID player) {
        boolean changed = preferences.set(player, true);
        show(player);
        return changed;
    }

    /**
     * Turns the HUD off and forgets it.
     *
     * @return {@code false} if it was already off
     */
    public boolean disable(UUID player) {
        boolean changed = preferences.set(player, false);
        CoinsOverlay overlay = shown.remove(player);
        if (overlay != null) {
            overlay.hide();
        }
        return changed;
    }

    public boolean isEnabled(UUID player) {
        return preferences.isEnabled(player);
    }

    /**
     * The player is in a world: put the HUD back if they want it.
     *
     * @throws IllegalArgumentException if the display does not see the
     *                                  player yet
     */
    public void onReady(UUID player) {
        if (preferences.isEnabled(player)) {
            show(player);
        }
    }

    /** The session is over; the display has already dropped the overlay. */
    public void onDisconnect(UUID player) {
        shown.remove(player);
    }

    private void show(UUID player) {
        CoinsOverlay current = shown.get(player);
        if (current != null && current.isVisible()) {
            return;
        }
        shown.put(player, display.track(player, POSITION, new PlayerWallet(player), CoinsFormat.STANDARD));
    }
}
