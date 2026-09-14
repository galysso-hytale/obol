package dev.galysso.obol.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DenominationTest {

    @Test
    void consecutiveTiersDifferByHundred() {
        Denomination[] tiers = Denomination.values();
        assertEquals(1L, tiers[0].valueInCopper());
        for (int i = 1; i < tiers.length; i++) {
            assertEquals(tiers[i - 1].valueInCopper() * 100, tiers[i].valueInCopper(), tiers[i].name());
        }
    }

    @Test
    void largestIsMythril() {
        assertEquals(Denomination.MYTHRIL, Denomination.largest());
        for (Denomination tier : Denomination.values()) {
            assertTrue(tier.valueInCopper() <= Denomination.largest().valueInCopper());
        }
    }

    @Test
    void symbolsAndColoursAreDistinct() {
        Denomination[] tiers = Denomination.values();
        for (int i = 0; i < tiers.length; i++) {
            assertTrue(tiers[i].color().matches("#[0-9A-F]{6}"), tiers[i].color());
            for (int j = i + 1; j < tiers.length; j++) {
                assertTrue(!tiers[i].symbol().equals(tiers[j].symbol()));
                assertTrue(!tiers[i].color().equals(tiers[j].color()));
            }
        }
    }
}
