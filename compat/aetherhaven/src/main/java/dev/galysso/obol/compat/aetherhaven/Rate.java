package dev.galysso.obol.compat.aetherhaven;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;

import java.util.Objects;

/**
 * What one Aetherhaven gold coin is worth in Obol coins.
 *
 * <p>Aetherhaven counts everything in its gold coins (a chest gives 5 to 10,
 * a building costs 5 to 45, a guard 35 to 45). Obol's own scale is one gold
 * for about an hour of play. At the default of five silver a coin, twenty
 * coins make one gold and a building costs one to two hours.</p>
 *
 * @param coin the value of one Aetherhaven coin, never zero
 */
public record Rate(Coins coin) {

    /** Five silver a coin. */
    public static final Rate DEFAULT = new Rate(Coins.of(Denomination.SILVER, 5));

    public Rate {
        Objects.requireNonNull(coin, "coin");
        if (coin.copper() <= 0) {
            throw new IllegalArgumentException("A coin must be worth something: " + coin);
        }
    }

    /**
     * {@code count} Aetherhaven coins in Obol coins.
     *
     * @throws ArithmeticException if the product overflows a {@code long}
     */
    public Coins toCoins(long count) {
        return coin.times(count);
    }

    /** How many whole Aetherhaven coins {@code coins} buy, rounded down. */
    public long toAetherhaven(Coins coins) {
        return coins.copper() / coin.copper();
    }
}
