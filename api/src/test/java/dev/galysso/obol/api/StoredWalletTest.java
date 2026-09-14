package dev.galysso.obol.api;

import dev.galysso.obol.api.internal.ObolApiHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredWalletTest {

    private BalanceStore store;

    @BeforeEach
    void installRuntime() {
        store = InMemoryRuntime.install().balances();
    }

    @AfterEach
    void uninstallRuntime() {
        ObolApiHolder.uninstall();
    }

    @Test
    void playerWalletIsKeyedByUuid() {
        UUID uuid = UUID.fromString("8f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f");
        PlayerWallet wallet = new PlayerWallet(uuid);
        assertSame(uuid, wallet.playerId());
        assertEquals(new WalletId("player", "8f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f"), wallet.id());
        assertEquals("player:8f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f", wallet.id().storageKey());
        assertThrows(NullPointerException.class, () -> new PlayerWallet(null));
    }

    @Test
    void unknownPlayerStartsAtZeroWithoutAnEntry() {
        PlayerWallet wallet = new PlayerWallet(UUID.randomUUID());
        assertEquals(Coins.ZERO, wallet.balance());
        assertFalse(store.exists(wallet.id()));
        assertFalse(wallet.withdraw(Coins.ofCopper(1)));
        assertFalse(store.exists(wallet.id()));
    }

    @Test
    void writesLandInTheStore() {
        UUID uuid = UUID.randomUUID();
        PlayerWallet wallet = new PlayerWallet(uuid);
        wallet.deposit(Coins.of(Denomination.GOLD, 2));
        assertEquals(Coins.ofCopper(20_000), store.balance(wallet.id()));
        assertTrue(store.exists(wallet.id()));

        // Stateless handle: a fresh instance sees the same variable.
        assertEquals(Coins.ofCopper(20_000), new PlayerWallet(uuid).balance());
        assertEquals(wallet, new PlayerWallet(uuid));

        assertTrue(wallet.withdraw(Coins.ofCopper(20_000)));
        assertTrue(store.exists(wallet.id()), "zero balance is kept");
        assertTrue(store.delete(wallet.id()));
        assertFalse(store.exists(wallet.id()));
    }

    @Test
    void thirdPartyStoredWalletOnlyNeedsAnId() {
        class ShopWallet extends StoredWallet {
            private final WalletId id;

            ShopWallet(String name) {
                id = new WalletId("shop", name);
            }

            @Override
            public WalletId id() {
                return id;
            }
        }
        ShopWallet shop = new ShopWallet("smith_01");
        PlayerWallet player = new PlayerWallet(UUID.randomUUID());
        player.deposit(Coins.ofCopper(500));

        assertTrue(player.transferTo(shop, Coins.ofCopper(120)));
        assertEquals(Coins.ofCopper(380), player.balance());
        assertEquals(Coins.ofCopper(120), store.balance(new WalletId("shop", "smith_01")));
        assertFalse(shop.transferTo(player, Coins.ofCopper(121)));
        assertEquals(Coins.ofCopper(120), shop.balance());
    }
}
