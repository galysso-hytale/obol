package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsDisplay;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.Wallet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private final PlayerHuds huds = new PlayerHuds(display);
    private final UUID player = UUID.randomUUID();

    @Test
    void theHudGoesUpOnReadyAndOnlyOncePerSession() {
        display.online.add(player);

        huds.onReady(player);
        huds.onReady(player);   // a world change: the overlay is still there

        assertEquals(List.of("track player:" + player + " at Top: 20, Right: 20"), display.calls);
    }

    @Test
    void aNewSessionGetsANewOverlay() {
        display.online.add(player);
        huds.onReady(player);

        // The display dropped the old overlay with the session.
        huds.onDisconnect(player);
        huds.onReady(player);

        assertEquals(2, display.calls.size());
        assertTrue(display.calls.stream().allMatch(c -> c.startsWith("track")));
    }

    @Test
    void readyBeforeTheDisplaySeesThePlayerIsReported() {
        assertThrows(IllegalArgumentException.class, () -> huds.onReady(player));
        assertTrue(display.calls.isEmpty());
    }
}
