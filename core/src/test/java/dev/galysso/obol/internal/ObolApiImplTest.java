package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.PlayerWallet;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.event.CoinsChangedEvent;
import dev.galysso.obol.api.internal.ObolApiHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real runtime behind the real wallets, no server in the loop. */
class ObolApiImplTest {

    private final ObolApiImpl api = new ObolApiImpl(
            (listener, event, e) -> {
                throw new AssertionError("listener failed on " + event, e);
            },
            (viewer, key, format) -> Optional.empty());

    @BeforeEach
    void install() {
        ObolApiHolder.install(api);
    }

    @AfterEach
    void uninstall() {
        ObolApiHolder.uninstall();
    }

    @Test
    void playerWalletsRunOnTheStoreAndLocks() {
        PlayerWallet alice = new PlayerWallet(UUID.randomUUID());
        PlayerWallet bob = new PlayerWallet(UUID.randomUUID());

        alice.deposit(Coins.ofCopper(500));
        assertTrue(alice.transferTo(bob, Coins.ofCopper(120)));

        assertEquals(Coins.ofCopper(380), api.balances().balance(alice.id()));
        assertEquals(Coins.ofCopper(120), api.storedBalances().balance(bob.id()));
        assertSame(api.lockFor(alice.id()), api.lockFor(alice.id()));
    }

    @Test
    void administrativeWritesReachTheListenersLikeWalletOnes() {
        PlayerWallet alice = new PlayerWallet(UUID.randomUUID());
        List<CoinsChangedEvent> seen = new ArrayList<>();
        api.addListener(seen::add);

        alice.deposit(Coins.ofCopper(5));
        assertEquals(Coins.ofCopper(5), api.balances().set(alice.id(), Coins.ofCopper(20)));
        assertEquals(Coins.ofCopper(20), alice.balance());
        assertTrue(api.balances().delete(alice.id()));

        assertEquals(List.of(
                new CoinsChangedEvent(alice.id(), Coins.ZERO, Coins.ofCopper(5)),
                new CoinsChangedEvent(alice.id(), Coins.ofCopper(5), Coins.ofCopper(20)),
                new CoinsChangedEvent(alice.id(), Coins.ofCopper(20), Coins.ZERO)),
                seen);
    }

    @Test
    void displayRejectsAViewerTheServerDoesNotSee() {
        assertThrows(IllegalArgumentException.class, () -> api.display().show(
                UUID.randomUUID(), ScreenPosition.topRight(1, 1), Coins.ZERO, CoinsFormat.STANDARD));
    }

    @Test
    void walletWritesReachSubscribedListeners() {
        PlayerWallet alice = new PlayerWallet(UUID.randomUUID());
        List<CoinsChangedEvent> seen = new ArrayList<>();
        api.addListener(seen::add);

        alice.deposit(Coins.ofCopper(500));
        assertFalse(api.removeListener(seen::add), "a fresh method reference is not the subscribed one");
        alice.deposit(Coins.ofCopper(1));

        assertEquals(List.of(
                new CoinsChangedEvent(alice.id(), Coins.ZERO, Coins.ofCopper(500)),
                new CoinsChangedEvent(alice.id(), Coins.ofCopper(500), Coins.ofCopper(501))),
                seen);
    }
}
