package dev.galysso.obol.api;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinsTest {

    @Test
    void rejectsNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new Coins(-1));
        assertThrows(IllegalArgumentException.class, () -> Coins.ofCopper(-1));
        assertThrows(IllegalArgumentException.class, () -> Coins.of(Denomination.GOLD, -1));
        assertThrows(IllegalArgumentException.class, () -> Coins.of(1, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> Coins.ofCopper(5).times(-1));
    }

    @Test
    void factoriesAgreeOnValue() {
        assertEquals(Coins.ofCopper(1_000_000 + 2 * 10_000 + 35 * 100 + 4), Coins.of(1, 2, 35, 4));
        assertEquals(Coins.ofCopper(25_000), Coins.of(0, 0, 250, 0));
        assertEquals(Coins.of(Denomination.GOLD, 3), Coins.ofCopper(30_000));
        assertEquals(Coins.ZERO, Coins.of(0, 0, 0, 0));
    }

    @Test
    void arithmetic() {
        Coins a = Coins.ofCopper(150);
        Coins b = Coins.ofCopper(50);
        assertEquals(Coins.ofCopper(200), a.plus(b));
        assertEquals(Optional.of(Coins.ofCopper(100)), a.minus(b));
        assertEquals(Optional.of(Coins.ZERO), a.minus(a));
        assertEquals(Optional.empty(), b.minus(a));
        assertEquals(Coins.ofCopper(450), a.times(3));
        assertEquals(Coins.ZERO, a.times(0));
    }

    @Test
    void overflowFailsLoudly() {
        Coins max = Coins.ofCopper(Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> max.plus(Coins.ofCopper(1)));
        assertThrows(ArithmeticException.class, () -> max.times(2));
        assertThrows(ArithmeticException.class, () -> Coins.of(Denomination.MYTHRIL, Long.MAX_VALUE));
    }

    @Test
    void predicatesAndOrdering() {
        assertTrue(Coins.ZERO.isZero());
        assertFalse(Coins.ofCopper(1).isZero());
        assertTrue(Coins.ofCopper(10).covers(Coins.ofCopper(10)));
        assertTrue(Coins.ofCopper(10).covers(Coins.ofCopper(9)));
        assertFalse(Coins.ofCopper(9).covers(Coins.ofCopper(10)));
        assertTrue(Coins.ofCopper(9).compareTo(Coins.ofCopper(10)) < 0);
        assertEquals(0, Coins.ofCopper(10).compareTo(Coins.ofCopper(10)));
    }

    @Test
    void breakdownAtBoundaries() {
        assertBreakdown(Coins.ZERO, 0, 0, 0, 0);
        assertBreakdown(Coins.ofCopper(99), 0, 0, 0, 99);
        assertBreakdown(Coins.ofCopper(100), 0, 0, 1, 0);
        assertBreakdown(Coins.ofCopper(9_999), 0, 0, 99, 99);
        assertBreakdown(Coins.ofCopper(10_000), 0, 1, 0, 0);
        assertBreakdown(Coins.ofCopper(999_999), 0, 99, 99, 99);
        assertBreakdown(Coins.ofCopper(1_000_000), 1, 0, 0, 0);
        assertBreakdown(Coins.of(1, 2, 35, 4), 1, 2, 35, 4);
        // Only the largest tier may exceed the exchange rate.
        assertBreakdown(Coins.of(Denomination.MYTHRIL, 12_345), 12_345, 0, 0, 0);
    }

    @Test
    void breakdownListsEveryTier() {
        EnumMap<Denomination, Long> parts = Coins.ofCopper(7).breakdown();
        assertEquals(Denomination.values().length, parts.size());
        assertEquals(7L, Coins.ofCopper(7).amountOf(Denomination.COPPER));
        assertEquals(0L, Coins.ofCopper(7).amountOf(Denomination.MYTHRIL));
    }

    @Test
    void toStringUsesStandardFormat() {
        assertEquals("2g 35s 4c", Coins.of(0, 2, 35, 4).toString());
    }

    private static void assertBreakdown(Coins coins, long mythril, long gold, long silver, long copper) {
        EnumMap<Denomination, Long> parts = coins.breakdown();
        assertEquals(mythril, parts.get(Denomination.MYTHRIL), "mythril of " + coins.copper());
        assertEquals(gold, parts.get(Denomination.GOLD), "gold of " + coins.copper());
        assertEquals(silver, parts.get(Denomination.SILVER), "silver of " + coins.copper());
        assertEquals(copper, parts.get(Denomination.COPPER), "copper of " + coins.copper());
    }
}
