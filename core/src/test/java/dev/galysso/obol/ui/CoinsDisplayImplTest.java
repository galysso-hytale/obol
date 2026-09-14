package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.internal.ObolBackendImpl;
import dev.galysso.obol.internal.WalletImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The display over the real backend (locks, store, listeners) and a fake
 * screen, so that what a tracking overlay sends is checked against real
 * wallet operations.
 */
class CoinsDisplayImplTest {

    private final RecordingHuds huds = new RecordingHuds();
    private final ObolBackendImpl backend = new ObolBackendImpl(
            (listener, event, e) -> {
                throw new AssertionError("listener failed on " + event, e);
            },
            huds);
    private final CoinsDisplayImpl display = backend.display();
    private final UUID viewer = UUID.randomUUID();
    private final ScreenPosition corner = ScreenPosition.topRight(20, 10);

    @BeforeEach
    void connect() {
        huds.online.add(viewer);
    }

    private static boolean visible(CoinsOverlay overlay) {
        return ((Overlay) overlay).isVisible();
    }

    @Test
    void aFixedOverlayShowsWhatItIsGiven() {
        CoinsOverlay overlay = display.show(viewer, corner, Coins.ofCopper(250));
        assertTrue(visible(overlay));
        overlay.update(Coins.ofCopper(10203));
        overlay.hide();
        overlay.hide();
        overlay.update(Coins.ZERO);

        assertFalse(visible(overlay));
        assertEquals(List.of(
                "obol:1 show Top: 10, Right: 20 \"2s 50c\"",
                "obol:1 text \"1g 2s 3c\"",
                "obol:1 hide"),
                huds.calls);
        assertEquals(0, display.count(viewer));
    }

    @Test
    void aTrackingOverlayFollowsEveryWriteThroughObol() {
        WalletImpl wallet = backend.wallet(WalletId.player(viewer));
        wallet.deposit(Coins.ofCopper(100));
        CoinsOverlay overlay = display.track(viewer, corner, wallet);

        wallet.deposit(Coins.ofCopper(50));
        assertTrue(wallet.withdraw(Coins.ofCopper(1)));
        assertFalse(wallet.withdraw(Coins.ofCopper(10_000)));
        wallet.deposit(Coins.ZERO);
        backend.wallet(WalletId.player(UUID.randomUUID())).deposit(Coins.ofCopper(7));
        wallet.set(Coins.ofCopper(9));
        overlay.update(Coins.ofCopper(123_456));
        overlay.hide();
        wallet.deposit(Coins.ofCopper(1));

        assertEquals(List.of(
                "obol:1 show Top: 10, Right: 20 \"1s\"",
                "obol:1 text \"1s 50c\"",
                "obol:1 log +50c",
                "obol:1 text \"1s 49c\"",
                "obol:1 log -1c",
                "obol:1 text \"9c\"",
                "obol:1 log -1s 40c",
                "obol:1 hide"),
                huds.calls);
    }

    @Test
    void aTrackingOverlayReadsTheWalletRatherThanTrustingTheEvent() {
        // A wallet whose listener sees a newer state than the event reports:
        // a second write done by a listener that runs before the display's.
        WalletImpl wallet = backend.wallet(WalletId.player(viewer));
        WalletImpl tax = backend.wallet(new WalletId("test", "tax"));
        backend.addListener(event -> {
            if (event.wallet().equals(wallet.id()) && event.increased()) {
                wallet.transferTo(tax, Coins.ofCopper(1));
            }
        });
        display.track(viewer, corner, wallet);

        wallet.deposit(Coins.ofCopper(10));

        // The nested transfer refreshes first (9c), then the outer deposit's
        // own event refreshes again and still reads 9c: never 10c. The feed,
        // on the other hand, gets each change as it was.
        assertEquals(List.of(
                "obol:1 show Top: 10, Right: 20 \"0c\"",
                "obol:1 text \"9c\"",
                "obol:1 log -1c",
                "obol:1 text \"9c\"",
                "obol:1 log +10c"),
                huds.calls);
    }

    @Test
    void severalOverlaysPerViewerEachWithTheirOwnKey() {
        display.show(viewer, corner, Coins.ZERO);
        display.track(viewer, corner, backend.wallet(WalletId.player(viewer)));
        assertEquals(List.of("obol:1", "obol:2"), huds.keys);
        assertEquals(2, display.count(viewer));
    }

    @Test
    void disconnectingDropsEveryOverlayWithoutTouchingTheScreen() {
        WalletImpl wallet = backend.wallet(WalletId.player(viewer));
        CoinsOverlay fixed = display.show(viewer, corner, Coins.ZERO);
        CoinsOverlay tracking = display.track(viewer, corner, wallet);

        display.onDisconnect(viewer);
        wallet.deposit(Coins.ofCopper(1));
        tracking.hide();

        assertFalse(visible(fixed));
        assertFalse(visible(tracking));
        assertEquals(0, display.count(viewer));
        assertEquals(2, huds.calls.size(), "only the two initial shows");
    }

    @Test
    void anOfflineViewerIsACallerBug() {
        UUID offline = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> display.show(offline, corner, Coins.ZERO));
        assertThrows(NullPointerException.class,
                () -> display.show(viewer, null, Coins.ZERO));
        assertThrows(NullPointerException.class,
                () -> display.show(viewer, corner, null));
        assertThrows(NullPointerException.class,
                () -> display.track(viewer, corner, null));
        assertTrue(huds.calls.isEmpty());
        assertEquals(0, display.count(viewer));
    }
}
