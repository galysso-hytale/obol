package dev.galysso.obol.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoinsFormatTest {

    @Test
    void ids() {
        assertEquals("standard", CoinsFormat.STANDARD.id());
        assertEquals("long", CoinsFormat.LONG.id());
    }

    @Test
    void standardFormat() {
        assertEquals("0c", CoinsFormat.STANDARD.format(Coins.ZERO));
        assertEquals("4c", CoinsFormat.STANDARD.format(Coins.ofCopper(4)));
        assertEquals("2g 4c", CoinsFormat.STANDARD.format(Coins.of(0, 2, 0, 4)));
        assertEquals("1m 2g 35s 4c", CoinsFormat.STANDARD.format(Coins.of(1, 2, 35, 4)));
        assertEquals("12345m", CoinsFormat.STANDARD.format(Coins.of(Denomination.MYTHRIL, 12_345)));
    }

    @Test
    void longFormat() {
        assertEquals("0 copper", CoinsFormat.LONG.format(Coins.ZERO));
        assertEquals("2 gold, 4 copper", CoinsFormat.LONG.format(Coins.of(0, 2, 0, 4)));
        assertEquals("1 mythril, 2 gold, 35 silver, 4 copper", CoinsFormat.LONG.format(Coins.of(1, 2, 35, 4)));
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
        assertEquals(Coins.ofCopper(copper), CoinsFormat.parse(text));
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
        CoinsParseException e = assertThrows(CoinsParseException.class, () -> CoinsFormat.parse(text));
        assertEquals(text, e.input());
    }

    @Test
    void roundTrips() throws CoinsParseException {
        long[] sample = {0, 1, 99, 100, 101, 9_999, 10_000, 999_999, 1_000_000, 1_020_304, 123_456_789_012L, Long.MAX_VALUE};
        for (long copper : sample) {
            Coins coins = Coins.ofCopper(copper);
            assertEquals(coins, CoinsFormat.parse(CoinsFormat.STANDARD.format(coins)), "standard " + copper);
            assertEquals(coins, CoinsFormat.parse(CoinsFormat.LONG.format(coins)), "long " + copper);
        }
    }
}
