package dev.galysso.obol.compat.aetherhaven;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateTest {

    @Test
    void defaultIsFiveSilverACoin() {
        assertEquals(Coins.of(Denomination.SILVER, 5), Rate.DEFAULT.coin());
        assertEquals(Coins.of(Denomination.GOLD, 1), Rate.DEFAULT.toCoins(20));
    }

    @Test
    void convertsBackRoundingDown() {
        Rate rate = Rate.DEFAULT;
        assertEquals(0, rate.toAetherhaven(Coins.ofCopper(499)));
        assertEquals(1, rate.toAetherhaven(Coins.ofCopper(500)));
        assertEquals(3, rate.toAetherhaven(Coins.of(Denomination.SILVER, 17)));
    }

    @Test
    void refusesAWorthlessCoin() {
        assertThrows(IllegalArgumentException.class, () -> new Rate(Coins.ZERO));
    }

    @Test
    void overflowSurfacesInsteadOfWrapping() {
        assertThrows(ArithmeticException.class, () -> Rate.DEFAULT.toCoins(Long.MAX_VALUE));
    }
}
