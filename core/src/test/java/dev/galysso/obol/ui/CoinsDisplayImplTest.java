package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.PlayerWallet;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.internal.ObolApiHolder;
import dev.galysso.obol.internal.ObolApiImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The display over the real runtime (locks, store, listeners) and a fake
 * screen, so that what a tracking overlay sends is checked against real
 * wallet operations.
 */
class CoinsDisplayImplTest {

    private final RecordingHuds huds = new RecordingHuds();
    private final ObolApiImpl api = new ObolApiImpl(
            (listener, event, e) -> {
                throw new AssertionError("listener failed on " + event, e);
            },
            huds);
    private final CoinsDisplayImpl display = api.display();
    private final UUID viewer = UUID.randomUUID();
    private final ScreenPosition corner = ScreenPosition.topRight(20, 10);

    @BeforeEach
    void install() {
        ObolApiHolder.install(api);
        huds.online.add(viewer);
    }

    @AfterEach
    void uninstall() {
        ObolApiHolder.uninstall();
    }

    @Test
    void aFixedOverlayShowsWhatItIsGiven() {
        CoinsOverlay overlay = display.show(viewer, corner, Coins.ofCopper(250), CoinsFormat.STANDARD);
        assertTrue(overlay.isVisible());
        overlay.update(Coins.ofCopper(10203));
        overlay.move(ScreenPosition.bottomLeft(1, 2));
        overlay.hide();
        overlay.hide();
        overlay.update(Coins.ZERO);
        overlay.move(corner);

        assertFalse(overlay.isVisible());
        assertEquals(List.of(
                "obol:1 show Top: 10, Right: 20 \"2s 50c\"",
                "obol:1 text \"1g 2s 3c\"",
                "obol:1 move Bottom: 2, Left: 1",
                "obol:1 hide"),
                huds.calls);
        assertEquals(0, display.count(viewer));
    }

    @Test
    void aTrackingOverlayFollowsEveryWriteThroughObol() {
        PlayerWallet wallet = new PlayerWallet(viewer);
        wallet.deposit(Coins.ofCopper(100));
        CoinsOverlay overlay = display.track(viewer, corner, wallet, CoinsFormat.STANDARD);

        wallet.deposit(Coins.ofCopper(50));
        assertTrue(wallet.withdraw(Coins.ofCopper(1)));
        assertFalse(wallet.withdraw(Coins.ofCopper(10_000)));
        wallet.deposit(Coins.ZERO);
        new PlayerWallet(UUID.randomUUID()).deposit(Coins.ofCopper(7));
        api.balances().set(wallet.id(), Coins.ofCopper(9));
        overlay.update(Coins.ofCopper(123_456));
        overlay.hide();
        wallet.deposit(Coins.ofCopper(1));

        assertEquals(List.of(
                "obol:1 show Top: 10, Right: 20 \"1s\"",
                "obol:1 text \"1s 50c\"",
                "obol:1 text \"1s 49c\"",
                "obol:1 text \"9c\"",
                "obol:1 hide"),
                huds.calls);
    }

    @Test
    void aTrackingOverlayReadsTheWalletRatherThanTrustingTheEvent() {
        // A wallet whose listener sees a newer state than the event reports:
        // a second write done by a listener that runs before the display's.
        PlayerWallet wallet = new PlayerWallet(viewer);
        WalletId tax = new WalletId("test", "tax");
        api.addListener(event -> {
            if (event.wallet().equals(wallet.id()) && event.increased()) {
                wallet.transferTo(new Wallet() {
                    @Override
                    public WalletId id() {
                        return tax;
                    }

                    @Override
                    protected long loadCopper() {
                        return 0;
                    }

                    @Override
                    protected void saveCopper(long copper) {
                    }
                }, Coins.ofCopper(1));
            }
        });
        display.track(viewer, corner, wallet, CoinsFormat.STANDARD);

        wallet.deposit(Coins.ofCopper(10));

        // The nested transfer refreshes first (9c), then the outer deposit's
        // own event refreshes again and still reads 9c: never 10c.
        assertEquals(List.of(
                "obol:1 show Top: 10, Right: 20 \"0c\"",
                "obol:1 text \"9c\"",
                "obol:1 text \"9c\""),
                huds.calls);
    }

    @Test
    void severalOverlaysPerViewerEachWithTheirOwnKey() {
        display.show(viewer, corner, Coins.ZERO, CoinsFormat.STANDARD);
        display.track(viewer, corner, new PlayerWallet(viewer), CoinsFormat.STANDARD);
        assertEquals(List.of("obol:1", "obol:2"), huds.keys);
        assertEquals(2, display.count(viewer));
    }

    @Test
    void disconnectingDropsEveryOverlayWithoutTouchingTheScreen() {
        PlayerWallet wallet = new PlayerWallet(viewer);
        CoinsOverlay fixed = display.show(viewer, corner, Coins.ZERO, CoinsFormat.STANDARD);
        CoinsOverlay tracking = display.track(viewer, corner, wallet, CoinsFormat.STANDARD);

        display.onDisconnect(viewer);
        wallet.deposit(Coins.ofCopper(1));
        tracking.hide();

        assertFalse(fixed.isVisible());
        assertFalse(tracking.isVisible());
        assertEquals(0, display.count(viewer));
        assertEquals(2, huds.calls.size(), "only the two initial shows");
    }

    @Test
    void anOfflineViewerOrATextFormatIsACallerBug() {
        UUID offline = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> display.show(offline, corner, Coins.ZERO, CoinsFormat.STANDARD));
        assertThrows(IllegalArgumentException.class,
                () -> display.show(viewer, corner, Coins.ZERO, CoinsFormat.LONG));
        assertThrows(NullPointerException.class,
                () -> display.show(viewer, null, Coins.ZERO, CoinsFormat.STANDARD));
        assertThrows(NullPointerException.class,
                () -> display.track(viewer, corner, null, CoinsFormat.STANDARD));
        assertTrue(huds.calls.isEmpty());
        assertEquals(0, display.count(viewer));
    }

    @Test
    void aWalletThatCannotBeReadLeavesNothingBehind() {
        Wallet corrupt = new Wallet() {
            @Override
            public WalletId id() {
                return new WalletId("test", "corrupt");
            }

            @Override
            protected long loadCopper() {
                return -1;
            }

            @Override
            protected void saveCopper(long copper) {
            }
        };
        assertThrows(IllegalStateException.class,
                () -> display.track(viewer, corner, corrupt, CoinsFormat.STANDARD));

        assertEquals(0, display.count(viewer));
        assertTrue(huds.calls.isEmpty());
        // No listener left behind: a later write on any wallet reaches nobody.
        new PlayerWallet(viewer).deposit(Coins.ofCopper(1));
        assertTrue(huds.calls.isEmpty());
    }
}
