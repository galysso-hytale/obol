package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.api.ScreenPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudTemplatesTest {

    @Test
    void onlyStandardHasATemplate() {
        assertTrue(HudTemplates.supports(CoinsFormat.STANDARD));
        assertFalse(HudTemplates.supports(CoinsFormat.LONG));
        assertThrows(IllegalArgumentException.class,
                () -> HudTemplates.document(CoinsFormat.LONG, ScreenPosition.topLeft(0, 0)));
    }

    @Test
    void anchorsNameTheEdgesOfTheCorner() {
        assertEquals("Top: 10, Left: 20", HudTemplates.anchor(ScreenPosition.topLeft(20, 10)));
        assertEquals("Top: 10, Right: 20", HudTemplates.anchor(ScreenPosition.topRight(20, 10)));
        assertEquals("Bottom: 10, Left: 20", HudTemplates.anchor(ScreenPosition.bottomLeft(20, 10)));
        assertEquals("Bottom: 10, Right: 20", HudTemplates.anchor(ScreenPosition.bottomRight(20, 10)));
    }

    @Test
    void theDocumentIsPlacedAndCarriesTheEmptyPill() {
        String right = HudTemplates.document(CoinsFormat.STANDARD, ScreenPosition.bottomRight(5, 6));
        assertTrue(right.contains("LayoutMode: Right;"));
        assertTrue(right.contains("Anchor: (Bottom: 6, Right: 5, Height: 36);"));
        assertTrue(right.contains("Group " + HudTemplates.PILL + " {"));
        assertFalse(right.contains("%"), "every placeholder is filled");

        String left = HudTemplates.document(CoinsFormat.STANDARD, ScreenPosition.topLeft(5, 6));
        assertTrue(left.contains("LayoutMode: Left;"));
        assertTrue(left.contains("Anchor: (Top: 6, Left: 5, Height: 36);"));
    }

    @Test
    void tiersFollowTheTextFormat() {
        assertEquals(List.of(tier(Denomination.COPPER, 0)), HudTemplates.tiers(Coins.ZERO));
        assertEquals(List.of(tier(Denomination.SILVER, 2), tier(Denomination.COPPER, 50)),
                HudTemplates.tiers(Coins.ofCopper(250)));
        assertEquals(List.of(tier(Denomination.MYTHRIL, 120), tier(Denomination.GOLD, 3)),
                HudTemplates.tiers(Coins.of(120, 3, 0, 0)));
    }

    @Test
    void aTierNamesItsDocumentAndCountLabel() {
        HudTemplates.Tier gold = tier(Denomination.GOLD, 35);
        assertEquals("Obol/Gold.ui", gold.document());
        assertEquals("#Gold #Count.Text", gold.countSelector());
        assertEquals("35", gold.countText());
    }

    /**
     * The tier documents and their images ship in the asset pack, under
     * {@code src/main/resources}; a missing or mismatched file would only
     * show up on a player's screen.
     */
    @ParameterizedTest
    @EnumSource(Denomination.class)
    void everyTierHasItsDocumentAndImageInThePack(Denomination denomination) throws IOException {
        HudTemplates.Tier tier = tier(denomination, 1);
        String base = "/Common/UI/Custom/" + HudTemplates.PACK_DIR + "/" + tier.name();
        String document;
        try (InputStream in = HudTemplatesTest.class.getResourceAsStream(base + ".ui")) {
            assertNotNull(in, base + ".ui");
            document = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(document.contains("Group #" + tier.name() + " {"), "root id");
        assertTrue(document.contains("Label #Count {"), "count label");
        assertTrue(document.contains("Background: \"" + tier.name() + ".png\";"), "image next to the count");
        assertTrue(document.contains("TextColor: " + denomination.color()), "palette from Denomination");
        try (InputStream in = HudTemplatesTest.class.getResourceAsStream(base + ".png")) {
            assertNotNull(in, base + ".png");
            byte[] header = in.readNBytes(8);
            assertEquals((byte) 0x89, header[0]);
            assertEquals('P', header[1]);
            assertEquals('N', header[2]);
            assertEquals('G', header[3]);
        }
    }

    private static HudTemplates.Tier tier(Denomination denomination, long count) {
        return new HudTemplates.Tier(denomination, count);
    }
}
