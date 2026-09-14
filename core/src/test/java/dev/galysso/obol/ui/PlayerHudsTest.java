package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsDisplay;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.internal.HudPreferences;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHudsTest {

    /** A display that hands out inert overlays and remembers the calls. */
    private static final class FakeDisplay implements CoinsDisplay {
        final List<String> calls = new ArrayList<>();
        final Set<UUID> online = new HashSet<>();

        @Override
        public CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins, CoinsFormat format) {
            throw new UnsupportedOperationException("Obol's HUD tracks");
        }

        @Override
        public CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet, CoinsFormat format) {
            if (!online.contains(viewer)) {
                throw new IllegalArgumentException("offline");
            }
            calls.add("track " + wallet.id() + " at " + HudTemplates.anchor(position));
            return new CoinsOverlay() {
                boolean visible = true;

                @Override
                public void update(Coins coins) {
                }

                @Override
                public void move(ScreenPosition position) {
                }

                @Override
                public void hide() {
                    if (visible) {
                        visible = false;
                        calls.add("hide");
                    }
                }

                @Override
                public boolean isVisible() {
                    return visible;
                }
            };
        }
    }

    private final FakeDisplay display = new FakeDisplay();
    private final HudPreferences preferences = new HudPreferences();
    private final PlayerHuds huds = new PlayerHuds(display, preferences);
    private final UUID player = UUID.randomUUID();

    @Test
    void enableShowsOnceAndRemembers() {
        display.online.add(player);

        assertTrue(huds.enable(player));
        assertFalse(huds.enable(player), "already on, and not shown twice");
        assertTrue(huds.isEnabled(player));
        assertTrue(preferences.isEnabled(player));
        assertEquals(List.of("track player:" + player + " at Top: 20, Right: 20"), display.calls);
    }

    @Test
    void disableHidesAndForgets() {
        display.online.add(player);
        huds.enable(player);

        assertTrue(huds.disable(player));
        assertFalse(huds.disable(player));
        assertFalse(preferences.isEnabled(player));
        assertEquals("hide", display.calls.get(display.calls.size() - 1));
    }

    @Test
    void theHudComesBackOnReadyOnlyForThoseWhoWantIt() {
        huds.onReady(player);
        assertTrue(display.calls.isEmpty());

        preferences.set(player, true);
        display.online.add(player);
        huds.onReady(player);
        assertEquals(1, display.calls.size());

        // A new session: the display dropped the old overlay.
        huds.onDisconnect(player);
        huds.onReady(player);
        assertEquals(2, display.calls.size());
    }

    @Test
    void enablingWhileNotInAWorldIsRefusedButRemembered() {
        assertThrows(IllegalArgumentException.class, () -> huds.enable(player));
        assertTrue(preferences.isEnabled(player), "the choice is kept for the next ready");
    }
}
