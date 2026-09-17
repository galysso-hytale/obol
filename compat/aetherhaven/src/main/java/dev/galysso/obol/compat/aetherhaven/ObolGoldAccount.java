package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.economy.api.GoldAccount;
import dev.galysso.obol.api.Wallet;

import java.util.Objects;

/**
 * An Obol wallet (a player's, a town treasury's, a shop safe's) seen as
 * Aetherhaven gold: the balance is the wallet's, converted through the
 * {@link Rate} and rounded down, and every withdrawal or deposit moves the
 * converted amount on the wallet. The rate touches only what Aetherhaven
 * counts in coins (a price, the tithe): the wallet itself holds Obol coins,
 * at Obol's precision, and {@link ObolGoldProvider#transfer} moves between
 * two wallets without it.
 */
final class ObolGoldAccount implements GoldAccount {

    private final Wallet wallet;
    private final Rate rate;

    ObolGoldAccount(Wallet wallet, Rate rate) {
        this.wallet = Objects.requireNonNull(wallet, "wallet");
        this.rate = Objects.requireNonNull(rate, "rate");
    }

    Wallet wallet() {
        return wallet;
    }

    @Override
    public long balance() {
        return rate.toAetherhaven(wallet.balance());
    }

    @Override
    public boolean withdraw(long amount) {
        if (amount <= 0) {
            return true;
        }
        return wallet.withdraw(rate.toCoins(amount));
    }

    /** A balance has always room: never refuses. */
    @Override
    public boolean deposit(long amount) {
        if (amount > 0) {
            wallet.deposit(rate.toCoins(amount));
        }
        return true;
    }
}
