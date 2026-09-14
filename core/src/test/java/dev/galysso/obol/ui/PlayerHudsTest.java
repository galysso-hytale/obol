package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsListener;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.internal.ObolBackend;
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

    /**
     * A backend that hands out inert wallets and overlays and remembers the
     * calls; only {@code wallet} and {@code track} matter here.
     */
    private static final class FakeBackend implements ObolBackend {
        final List<String> calls = new ArrayList<>();
        final Set<UUID> online = new HashSet<>();

        @Override
        public Wallet wallet(WalletId id) {
            return new Wallet() {
                @Override
                public WalletId id() {
                    return id;
                }

                @Override
                public Coins balance() {
                    return Coins.ZERO;
                }

                @Override
                public boolean canAfford(Coins amount) {
                    return false;
                }

                @Override
                public Coins deposit(Coins amount) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean withdraw(Coins amount) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean transferTo(Wallet to, Coins amount) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void clear() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins) {
            throw new UnsupportedOperationException("Obol's HUD tracks");
        }

        @Override
        public CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet) {
            if (!online.contains(viewer)) {
                throw new IllegalArgumentException("offline");
            }
            calls.add("track " + wallet.id() + " at " + HudTemplates.anchor(position));
            return new CoinsOverlay() {
                @Override
                public void update(Coins coins) {
                }

                @Override
                public void hide() {
                    calls.add("hide");
                }
            };
        }

        @Override
        public void addListener(CoinsListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeListener(CoinsListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private final FakeBackend backend = new FakeBackend();
    private final PlayerHuds huds = new PlayerHuds(backend);
    private final UUID player = UUID.randomUUID();

    @Test
    void theHudGoesUpOnReadyAndOnlyOncePerSession() {
        backend.online.add(player);

        huds.onReady(player);
        huds.onReady(player);   // a world change: the overlay is still there

        assertEquals(List.of("track player:" + player + " at Top: 20, Right: 20"), backend.calls);
    }

    @Test
    void aNewSessionGetsANewOverlay() {
        backend.online.add(player);
        huds.onReady(player);

        // The display dropped the old overlay with the session.
        huds.onDisconnect(player);
        huds.onReady(player);

        assertEquals(2, backend.calls.size());
        assertTrue(backend.calls.stream().allMatch(c -> c.startsWith("track")));
    }

    @Test
    void readyBeforeTheDisplaySeesThePlayerIsReported() {
        assertThrows(IllegalArgumentException.class, () -> huds.onReady(player));
        assertTrue(backend.calls.isEmpty());
    }
}
