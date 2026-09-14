package dev.galysso.obol.api;

import java.util.EnumMap;
import java.util.Optional;

/**
 * An immutable, never-negative amount of coins.
 *
 * <p>The whole amount is a single {@code long} counted in copper, which is
 * the bridge to any persistence: a wallet only has one number to store.
 * {@link Denomination} and {@link #breakdown()} exist for humans; arithmetic
 * never leaves copper.</p>
 *
 * <p>Used both for prices ({@code Coins price}) and for what a wallet holds
 * ({@code wallet.balance()}).</p>
 *
 * @param copper the amount in copper, never negative
 */
public record Coins(long copper) implements Comparable<Coins> {

    /** No coins at all. */
    public static final Coins ZERO = new Coins(0);

    /**
     * Validates the non-negative invariant.
     *
     * @throws IllegalArgumentException if {@code copper} is negative
     */
    public Coins {
        if (copper < 0) {
            throw new IllegalArgumentException("Coins cannot be negative: " + copper);
        }
    }

    /**
     * {@return an amount counted in copper}
     *
     * @param copper the amount in copper
     * @throws IllegalArgumentException if {@code copper} is negative
     */
    public static Coins ofCopper(long copper) {
        return new Coins(copper);
    }

    /**
     * {@return {@code count} coins of the given tier}
     *
     * @param denomination the tier
     * @param count        how many coins of that tier, never negative
     * @throws IllegalArgumentException if {@code count} is negative
     * @throws ArithmeticException      if the amount overflows a {@code long}
     */
    public static Coins of(Denomination denomination, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative: " + count);
        }
        return new Coins(Math.multiplyExact(count, denomination.valueInCopper()));
    }

    /**
     * {@return the sum of the given coins of each tier}
     *
     * <p>Components need not be canonical: {@code of(0, 0, 250, 0)} equals
     * {@code of(0, 2, 50, 0)}.</p>
     *
     * @param mythril mythril coins, never negative
     * @param gold    gold coins, never negative
     * @param silver  silver coins, never negative
     * @param copper  copper coins, never negative
     * @throws IllegalArgumentException if any component is negative
     * @throws ArithmeticException      if the amount overflows a {@code long}
     */
    public static Coins of(long mythril, long gold, long silver, long copper) {
        return of(Denomination.MYTHRIL, mythril)
                .plus(of(Denomination.GOLD, gold))
                .plus(of(Denomination.SILVER, silver))
                .plus(of(Denomination.COPPER, copper));
    }

    /**
     * {@return this amount plus {@code other}}
     *
     * @param other the amount to add
     * @throws ArithmeticException if the sum overflows a {@code long}
     */
    public Coins plus(Coins other) {
        return new Coins(Math.addExact(copper, other.copper));
    }

    /**
     * {@return this amount minus {@code other}, or empty if {@code other} is
     * larger than this}
     *
     * <p>Subtracting from an immutable amount is a question ("is there
     * anything left?"), not an error, hence no exception.</p>
     *
     * @param other the amount to subtract
     */
    public Optional<Coins> minus(Coins other) {
        return covers(other) ? Optional.of(new Coins(copper - other.copper)) : Optional.empty();
    }

    /**
     * {@return this amount multiplied by {@code factor}}
     *
     * @param factor the multiplier, never negative
     * @throws IllegalArgumentException if {@code factor} is negative
     * @throws ArithmeticException      if the product overflows a {@code long}
     */
    public Coins times(long factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Factor cannot be negative: " + factor);
        }
        return new Coins(Math.multiplyExact(copper, factor));
    }

    /**
     * {@return whether this amount is zero}
     */
    public boolean isZero() {
        return copper == 0;
    }

    /**
     * {@return whether this amount is at least {@code other}}
     *
     * @param other the amount to compare against
     */
    public boolean covers(Coins other) {
        return copper >= other.copper;
    }

    /**
     * {@return the canonical decomposition of this amount into tiers}
     *
     * <p>Canonical means every tier below {@link Denomination#largest()} holds
     * fewer coins than the exchange rate: 250 copper is 2 silver and 50
     * copper. Every tier is present, with {@code 0} where it contributes
     * nothing. The map iterates in {@link Denomination} declaration order,
     * i.e. from the smallest tier to the largest.</p>
     */
    public EnumMap<Denomination, Long> breakdown() {
        EnumMap<Denomination, Long> parts = new EnumMap<>(Denomination.class);
        long remaining = copper;
        Denomination[] tiers = Denomination.values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            long value = tiers[i].valueInCopper();
            parts.put(tiers[i], remaining / value);
            remaining %= value;
        }
        return parts;
    }

    /**
     * {@return how many coins of {@code denomination} appear in
     * {@link #breakdown()}}
     *
     * @param denomination the tier
     */
    public long amountOf(Denomination denomination) {
        return breakdown().get(denomination);
    }

    @Override
    public int compareTo(Coins other) {
        return Long.compare(copper, other.copper);
    }

    /**
     * {@return this amount formatted with {@link CoinsFormat#STANDARD}}
     */
    @Override
    public String toString() {
        return CoinsFormat.STANDARD.format(this);
    }
}
