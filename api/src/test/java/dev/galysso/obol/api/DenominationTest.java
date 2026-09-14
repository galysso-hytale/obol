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
    void declaredInAscendingValue() {
        Denomination[] tiers = Denomination.values();
        assertEquals(Denomination.COPPER, tiers[0]);
        assertEquals(Denomination.MYTHRIL, tiers[tiers.length - 1]);
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(tiers[i - 1].valueInCopper() < tiers[i].valueInCopper());
        }
    }

    @Test
    void textureIsThePackImageOfTheTier() {
        assertEquals("Obol/Copper.png", Denomination.COPPER.texture());
        assertEquals("Obol/Silver.png", Denomination.SILVER.texture());
        assertEquals("Obol/Gold.png", Denomination.GOLD.texture());
        assertEquals("Obol/Mythril.png", Denomination.MYTHRIL.texture());
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
