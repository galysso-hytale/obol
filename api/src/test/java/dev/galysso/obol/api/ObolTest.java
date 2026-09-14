package dev.galysso.obol.api;

import dev.galysso.obol.api.internal.ObolBackend;
import dev.galysso.obol.api.internal.ObolBackendHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The facade over the holder: no logic of its own, only the wiring. */
class ObolTest {

    /** Writes every call down; the wallet and overlay it hands out are inert. */
    private static final class RecordingBackend implements ObolBackend {
        final List<String> calls = new ArrayList<>();
        final Wallet wallet = new InertWallet();
        final CoinsOverlay overlay = new CoinsOverlay() {
            @Override
            public void update(Coins coins) {
            }

            @Override
            public void hide() {
            }
        };

        @Override
        public Wallet wallet(WalletId id) {
            calls.add("wallet " + id);
            return wallet;
        }

        @Override
        public CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins) {
            calls.add("show " + coins);
            return overlay;
        }

        @Override
        public CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet) {
            calls.add("track " + wallet.id());
            return overlay;
        }

        @Override
        public void addListener(CoinsListener listener) {
            calls.add("add");
        }

        @Override
        public boolean removeListener(CoinsListener listener) {
            calls.add("remove");
            return true;
        }
    }

    private static final class InertWallet implements Wallet {
        @Override
        public WalletId id() {
            return new WalletId("test", "inert");
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
            return Coins.ZERO;
        }

        @Override
        public boolean withdraw(Coins amount) {
            return false;
        }

        @Override
        public boolean transferTo(Wallet to, Coins amount) {
            return false;
        }

        @Override
        public void clear() {
        }
    }

    @AfterEach
    void uninstall() {
        ObolBackendHolder.uninstall();
    }

    @Test
    void absentObolIsExplained() {
        UUID player = UUID.randomUUID();
        for (Runnable call : List.<Runnable>of(
                () -> Obol.wallet(new WalletId("test", "a")),
                () -> Obol.playerWallet(player),
                () -> Obol.show(player, ScreenPosition.topRight(1, 1), Coins.ZERO),
                () -> Obol.track(player, ScreenPosition.topRight(1, 1), new InertWallet()),
                () -> Obol.addListener(event -> { }),
                () -> Obol.removeListener(event -> { }))) {
            IllegalStateException e = assertThrows(IllegalStateException.class, call::run);
            assertTrue(e.getMessage().contains("Galysso:obol"), e.getMessage());
        }
        assertThrows(IllegalStateException.class, ObolBackendHolder::require);
    }

    @Test
    void everyCallGoesToTheInstalledBackend() {
        RecordingBackend backend = new RecordingBackend();
        ObolBackendHolder.install(backend);
        UUID player = UUID.fromString("8f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f");
        ScreenPosition corner = ScreenPosition.topRight(1, 1);
        CoinsListener listener = event -> { };

        assertSame(backend, ObolBackendHolder.require());
        assertSame(backend.wallet, Obol.wallet(new WalletId("shop", "smith")));
        assertSame(backend.wallet, Obol.playerWallet(player));
        assertSame(backend.overlay, Obol.show(player, corner, Coins.ofCopper(5)));
        assertSame(backend.overlay, Obol.track(player, corner, backend.wallet));
        Obol.addListener(listener);
        assertTrue(Obol.removeListener(listener));

        assertEquals(List.of(
                "wallet shop:smith",
                "wallet player:8f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f",
                "show 5c",
                "track test:inert",
                "add",
                "remove"),
                backend.calls);
    }

    @Test
    void theBackendIsResolvedOnEveryCall() {
        RecordingBackend first = new RecordingBackend();
        ObolBackendHolder.install(first);
        assertThrows(IllegalStateException.class, () -> ObolBackendHolder.install(new RecordingBackend()));
        assertThrows(NullPointerException.class, () -> ObolBackendHolder.install(null));

        ObolBackendHolder.uninstall();
        assertThrows(IllegalStateException.class, () -> Obol.playerWallet(UUID.randomUUID()));

        RecordingBackend second = new RecordingBackend();
        ObolBackendHolder.install(second);
        Obol.playerWallet(UUID.randomUUID());
        assertTrue(first.calls.isEmpty());
        assertEquals(1, second.calls.size());
    }
}
