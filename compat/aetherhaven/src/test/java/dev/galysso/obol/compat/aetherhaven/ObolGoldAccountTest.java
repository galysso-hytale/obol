package dev.galysso.obol.compat.aetherhaven;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObolGoldAccountTest {

    /** A wallet that is only a number. */
    private static final class MemoryWallet implements Wallet {
        Coins balance = Coins.ZERO;

        @Override public WalletId id() { return new WalletId("test", "memory"); }
        @Override public Coins balance() { return balance; }
        @Override public boolean canAfford(Coins amount) { return balance.covers(amount); }
        @Override public Coins deposit(Coins amount) { balance = balance.plus(amount); return balance; }
        @Override public boolean withdraw(Coins amount) {
            var left = balance.minus(amount);
            left.ifPresent(c -> balance = c);
            return left.isPresent();
        }
        @Override public boolean transferTo(Wallet to, Coins amount) { return false; }
        @Override public void clear() { balance = Coins.ZERO; }
    }

    private final MemoryWallet wallet = new MemoryWallet();
    private final ObolGoldAccount account = new ObolGoldAccount(wallet, Rate.DEFAULT);

    @Test
    void balanceIsTheWalletInWholeCoins() {
        wallet.balance = Coins.of(Denomination.SILVER, 27);
        assertEquals(5, account.balance());
    }

    @Test
    void withdrawMovesTheConvertedAmount() {
        wallet.balance = Coins.of(Denomination.GOLD, 1);
        assertTrue(account.withdraw(5));
        assertEquals(Coins.of(Denomination.SILVER, 75), wallet.balance);
    }

    @Test
    void withdrawRefusesWithoutMovingAnything() {
        wallet.balance = Coins.of(Denomination.SILVER, 20);
        assertFalse(account.withdraw(5));
        assertEquals(Coins.of(Denomination.SILVER, 20), wallet.balance);
    }

    @Test
    void depositNeverRefuses() {
        assertTrue(account.deposit(10));
        assertEquals(Coins.of(Denomination.SILVER, 50), wallet.balance);
        assertTrue(account.deposit(0));
        assertEquals(Coins.of(Denomination.SILVER, 50), wallet.balance);
    }
}
