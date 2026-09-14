package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Obol;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.event.CoinsChangedEvent;
import dev.galysso.obol.api.internal.ObolBackendHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real backend behind the facade, no server in the loop. */
class ObolBackendImplTest {

    private final ObolBackendImpl backend = new ObolBackendImpl(
            (listener, event, e) -> {
                throw new AssertionError("listener failed on " + event, e);
            },
            (viewer, key) -> Optional.empty());

    @BeforeEach
    void install() {
        ObolBackendHolder.install(backend);
    }

    @AfterEach
    void uninstall() {
        ObolBackendHolder.uninstall();
    }

    @Test
    void walletsRunOnTheStoreAndLocks() {
        UUID aliceId = UUID.randomUUID();
        Wallet alice = Obol.playerWallet(aliceId);
        Wallet bob = Obol.wallet(new WalletId("shop", "smith"));

        alice.deposit(Coins.ofCopper(500));
        assertTrue(alice.transferTo(bob, Coins.ofCopper(120)));

        assertEquals(Coins.ofCopper(380), backend.storedBalances().balance(WalletId.player(aliceId)));
        assertEquals(Coins.ofCopper(120), backend.storedBalances().balance(bob.id()));
        // Stateless handle: a fresh instance sees the same entry.
        assertEquals(Coins.ofCopper(380), Obol.playerWallet(aliceId).balance());
        assertEquals(alice, Obol.playerWallet(aliceId));
    }

    @Test
    void administrativeWritesReachTheListenersLikeWalletOnes() {
        WalletImpl alice = backend.wallet(WalletId.player(UUID.randomUUID()));
        List<CoinsChangedEvent> seen = new ArrayList<>();
        backend.addListener(seen::add);

        alice.deposit(Coins.ofCopper(5));
        assertEquals(Coins.ofCopper(5), alice.set(Coins.ofCopper(20)));
        assertEquals(Coins.ofCopper(20), alice.balance());
        alice.clear();
        assertFalse(backend.storedBalances().exists(alice.id()));

        assertEquals(List.of(
                new CoinsChangedEvent(alice.id(), Coins.ZERO, Coins.ofCopper(5)),
                new CoinsChangedEvent(alice.id(), Coins.ofCopper(5), Coins.ofCopper(20)),
                new CoinsChangedEvent(alice.id(), Coins.ofCopper(20), Coins.ZERO)),
                seen);
    }

    @Test
    void displayRejectsAViewerTheServerDoesNotSee() {
        assertThrows(IllegalArgumentException.class, () -> Obol.show(
                UUID.randomUUID(), ScreenPosition.topRight(1, 1), Coins.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Obol.track(
                UUID.randomUUID(), ScreenPosition.topRight(1, 1), Obol.playerWallet(UUID.randomUUID())));
    }

    @Test
    void walletWritesReachSubscribedListeners() {
        Wallet alice = Obol.playerWallet(UUID.randomUUID());
        List<CoinsChangedEvent> seen = new ArrayList<>();
        Obol.addListener(seen::add);

        alice.deposit(Coins.ofCopper(500));
        assertFalse(Obol.removeListener(seen::add), "a fresh method reference is not the subscribed one");
        alice.deposit(Coins.ofCopper(1));

        assertEquals(List.of(
                new CoinsChangedEvent(alice.id(), Coins.ZERO, Coins.ofCopper(500)),
                new CoinsChangedEvent(alice.id(), Coins.ofCopper(500), Coins.ofCopper(501))),
                seen);
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> backend.wallet(null));
        assertThrows(NullPointerException.class, () -> Obol.playerWallet(null));
        assertThrows(NullPointerException.class, () -> Obol.addListener(null));
    }
}
