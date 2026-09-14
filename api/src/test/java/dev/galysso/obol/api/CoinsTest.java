package dev.galysso.obol.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertThrows(IllegalArgumentException.class, () -> Coins.ofCopper(5).times(-1));
    }

    @Test
    void factoriesAgreeOnValue() throws CoinsParseException {
        assertEquals(Coins.ofCopper(1_000_000 + 2 * 10_000 + 35 * 100 + 4), Coins.parse("1m 2g 35s 4c"));
        assertEquals(Coins.ofCopper(25_000), Coins.of(Denomination.SILVER, 250));
        assertEquals(Coins.of(Denomination.GOLD, 3), Coins.ofCopper(30_000));
        assertEquals(Coins.ZERO, Coins.of(Denomination.MYTHRIL, 0));
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
        assertTrue(Coins.ofCopper(10).covers(Coins.ofCopper(10)));
        assertTrue(Coins.ofCopper(10).covers(Coins.ofCopper(9)));
        assertFalse(Coins.ofCopper(9).covers(Coins.ofCopper(10)));
        assertTrue(Coins.ofCopper(9).compareTo(Coins.ofCopper(10)) < 0);
        assertEquals(0, Coins.ofCopper(10).compareTo(Coins.ofCopper(10)));
    }

    @Test
    void breakdownAtBoundaries() throws CoinsParseException {
        assertBreakdown(Coins.ZERO, 0, 0, 0, 0);
        assertBreakdown(Coins.ofCopper(99), 0, 0, 0, 99);
        assertBreakdown(Coins.ofCopper(100), 0, 0, 1, 0);
        assertBreakdown(Coins.ofCopper(9_999), 0, 0, 99, 99);
        assertBreakdown(Coins.ofCopper(10_000), 0, 1, 0, 0);
        assertBreakdown(Coins.ofCopper(999_999), 0, 99, 99, 99);
        assertBreakdown(Coins.ofCopper(1_000_000), 1, 0, 0, 0);
        assertBreakdown(Coins.parse("1m 2g 35s 4c"), 1, 2, 35, 4);
        // Only the largest tier may exceed the exchange rate.
        assertBreakdown(Coins.of(Denomination.MYTHRIL, 12_345), 12_345, 0, 0, 0);
    }

    @Test
    void breakdownListsEveryTier() {
        EnumMap<Denomination, Long> parts = Coins.ofCopper(7).breakdown();
        assertEquals(Denomination.values().length, parts.size());
        assertEquals(7L, parts.get(Denomination.COPPER));
        assertEquals(0L, parts.get(Denomination.MYTHRIL));
    }

    @Test
    void toStringOmitsZeroTiers() throws CoinsParseException {
        assertEquals("0c", Coins.ZERO.toString());
        assertEquals("4c", Coins.ofCopper(4).toString());
        assertEquals("2g 4c", Coins.ofCopper(20_004).toString());
        assertEquals("1m 2g 35s 4c", Coins.parse("1m 2g 35s 4c").toString());
        assertEquals("12345m", Coins.of(Denomination.MYTHRIL, 12_345).toString());
    }

    @ParameterizedTest
    @CsvSource({
            "2g35s, 23500",
            "2g 35s 4c, 23504",
            "250s, 25000",
            "1200, 1200",
            "0, 0",
            "4c 2g, 20004",
            "2G 35S, 23500",
            "'  2g  ', 20000",
            "2 gold, 20000",
            "'2 gold, 35 silver, 4 copper', 23504",
            "1 Mythril, 1000000",
    })
    void parses(String text, long copper) throws CoinsParseException {
        assertEquals(Coins.ofCopper(copper), Coins.parse(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "-5c",
            "5c -3s",
            "2g 2g",
            "2g 3 gold",
            "2x",
            "2 platinum",
            "2g 35",
            "5 3c",
            "g",
            "2.5g",
            "99999999999999999999c",
            "9223372036854775807m",
    })
    void rejects(String text) {
        CoinsParseException e = assertThrows(CoinsParseException.class, () -> Coins.parse(text));
        assertEquals(text, e.input());
    }

    @Test
    void toStringRoundTrips() throws CoinsParseException {
        long[] sample = {0, 1, 99, 100, 101, 9_999, 10_000, 999_999, 1_000_000, 1_020_304, 123_456_789_012L, Long.MAX_VALUE};
        for (long copper : sample) {
            Coins coins = Coins.ofCopper(copper);
            assertEquals(coins, Coins.parse(coins.toString()), "copper " + copper);
        }
    }

    private static void assertBreakdown(Coins coins, long mythril, long gold, long silver, long copper) {
        EnumMap<Denomination, Long> parts = coins.breakdown();
        assertEquals(mythril, parts.get(Denomination.MYTHRIL), "mythril of " + coins.copper());
        assertEquals(gold, parts.get(Denomination.GOLD), "gold of " + coins.copper());
        assertEquals(silver, parts.get(Denomination.SILVER), "silver of " + coins.copper());
        assertEquals(copper, parts.get(Denomination.COPPER), "copper of " + coins.copper());
    }
}
