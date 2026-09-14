package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.PlayerWallet;
import dev.galysso.obol.api.internal.ObolApiHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real runtime behind the real wallets, no server in the loop. */
class ObolApiImplTest {

    private final ObolApiImpl api = new ObolApiImpl();

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
        assertEquals(Coins.ofCopper(120), api.balances().balance(bob.id()));
        assertSame(api.lockFor(alice.id()), api.lockFor(alice.id()));
    }
}
