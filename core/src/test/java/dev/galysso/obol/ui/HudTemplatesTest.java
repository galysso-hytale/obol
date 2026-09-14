package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsParseException;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.api.ScreenPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudTemplatesTest {

    @Test
    void anchorsNameTheEdgesOfTheCorner() {
        assertEquals("Top: 10, Left: 20", HudTemplates.anchor(ScreenPosition.topLeft(20, 10)));
        assertEquals("Top: 10, Right: 20", HudTemplates.anchor(ScreenPosition.topRight(20, 10)));
        assertEquals("Bottom: 10, Left: 20", HudTemplates.anchor(ScreenPosition.bottomLeft(20, 10)));
        assertEquals("Bottom: 10, Right: 20", HudTemplates.anchor(ScreenPosition.bottomRight(20, 10)));
    }

    @Test
    void theDocumentIsPlacedAndCarriesTheEmptyPillAndFeed() {
        int height = HudTemplates.PILL_HEIGHT + ChangeFeed.MAX_ROWS * (HudTemplates.ROW_HEIGHT + HudTemplates.ROW_GAP);
        String right = HudTemplates.document(ScreenPosition.bottomRight(5, 6));
        assertTrue(right.contains("LayoutMode: Right;"));
        assertTrue(right.contains("LayoutMode: Bottom;"), "stacked from the bottom: the feed above the pill");
        assertTrue(right.contains("Anchor: (Bottom: 6, Right: 5, Height: " + height + ");"));
        assertTrue(right.contains("Group " + HudTemplates.PILL + " {"));
        assertTrue(right.contains("Group " + HudTemplates.FEED + " {"));
        assertFalse(right.contains("%"), "every placeholder is filled");

        String left = HudTemplates.document(ScreenPosition.topLeft(5, 6));
        assertTrue(left.contains("LayoutMode: Left;"));
        assertTrue(left.contains("LayoutMode: Top;"));
        assertFalse(left.contains("Bottom"));
        assertTrue(left.contains("Anchor: (Top: 6, Left: 5, Height: " + height + ");"));
    }

    @Test
    void aFeedRowPacksAgainstTheSideAndKeepsAGap() {
        String right = HudTemplates.feedRow(ScreenPosition.topRight(1, 1));
        assertTrue(right.contains("LayoutMode: Right;"));
        assertTrue(right.contains("Anchor: (Height: " + HudTemplates.ROW_HEIGHT + ", Top: " + HudTemplates.ROW_GAP + ");"));
        assertTrue(right.contains("Group " + HudTemplates.CHANGE + " {"));
        assertFalse(right.contains("%"));
        String bottomLeft = HudTemplates.feedRow(ScreenPosition.bottomLeft(1, 1));
        assertTrue(bottomLeft.contains("LayoutMode: Left;"));
        assertTrue(bottomLeft.contains("Bottom: " + HudTemplates.ROW_GAP + ");"));
        assertEquals("#Feed[3]", HudTemplates.feedRow(3));
    }

    @Test
    void aChangeShowsOnlyItsNonZeroTiers() throws CoinsParseException {
        assertEquals(List.of(tier(Denomination.GOLD, 2), tier(Denomination.COPPER, 5)),
                HudTemplates.feedTiers(Coins.parse("2g 5c")));
        assertEquals(List.of(tier(Denomination.COPPER, 1)), HudTemplates.feedTiers(Coins.ofCopper(1)));
        assertEquals("+2", tier(Denomination.GOLD, 2).signedCountText(true));
        assertEquals("-15", tier(Denomination.SILVER, 15).signedCountText(false));
        assertEquals("Obol/Feed/Gold.ui", tier(Denomination.GOLD, 2).feedDocument());
        assertEquals("#Gold #Icon", tier(Denomination.GOLD, 2).iconSelector());
    }

    @Test
    void fadeStylesAndColoursAreNamedPerLevel() {
        assertEquals("Up", HudTemplates.Tint.UP.styleName(0));
        assertEquals("Down2", HudTemplates.Tint.DOWN.styleName(2));
        // Eight-digit hex, alpha last: the only form a patched colour takes.
        assertEquals("#FFFFFFFF", HudTemplates.withOpacity("#FFFFFF", 1.0));
        assertEquals("#FFFFFFBF", HudTemplates.withOpacity("#FFFFFF", 0.75));
        assertEquals("#00000016", HudTemplates.withOpacity("#000000", 0.35 * 0.25));
        assertEquals("#00000000", HudTemplates.withOpacity("#000000", 0.0));
    }

    @Test
    void tiersRunFromTheLeadingOneDownToCopper() throws CoinsParseException {
        assertEquals(List.of(tier(Denomination.COPPER, 0)), HudTemplates.tiers(Coins.ZERO));
        assertEquals(List.of(tier(Denomination.SILVER, 2), tier(Denomination.COPPER, 50)),
                HudTemplates.tiers(Coins.ofCopper(250)));
        // Zero sub-units stay, so the pill keeps its shape; zero higher tiers go.
        assertEquals(List.of(tier(Denomination.MYTHRIL, 120), tier(Denomination.GOLD, 3),
                        tier(Denomination.SILVER, 0), tier(Denomination.COPPER, 0)),
                HudTemplates.tiers(Coins.parse("120m 3g")));
        assertEquals(List.of(tier(Denomination.GOLD, 1), tier(Denomination.SILVER, 0),
                        tier(Denomination.COPPER, 5)),
                HudTemplates.tiers(Coins.parse("1g 5c")));
    }

    @Test
    void aTierNamesItsDocumentAndCountLabel() {
        HudTemplates.Tier gold = tier(Denomination.GOLD, 35);
        assertEquals("Obol/Gold.ui", gold.document());
        assertEquals("#Gold #Count.Text", gold.countSelector());
        assertEquals("#Gold #Count.Style", gold.styleSelector());
        assertEquals("35", gold.countText());
    }

    @Test
    void tintsNameTheStylesOfTheTierDocuments() {
        assertEquals("Normal", HudTemplates.Tint.NORMAL.styleName());
        assertEquals("Up", HudTemplates.Tint.UP.styleName());
        assertEquals("Down", HudTemplates.Tint.DOWN.styleName());
    }

    @Test
    void onlyTheCountsThatMoveAreTinted() throws CoinsParseException {
        // +2g on 1g 5s 4c: silver and copper do not move.
        assertEquals(Map.of(Denomination.GOLD, HudTemplates.Tint.UP),
                HudTemplates.changed(Coins.parse("1g 5s 4c"), Coins.parse("3g 5s 4c")));
        // A first coin: copper alone, up.
        assertEquals(Map.of(Denomination.COPPER, HudTemplates.Tint.UP),
                HudTemplates.changed(Coins.ZERO, Coins.ofCopper(5)));
        // A tier that appears is a count that moved.
        assertEquals(Map.of(Denomination.GOLD, HudTemplates.Tint.UP, Denomination.SILVER, HudTemplates.Tint.UP),
                HudTemplates.changed(Coins.parse("90s 1c"), Coins.parse("1g 10s 1c")));
    }

    @Test
    void theTintFollowsTheAmountNotTheCount() throws CoinsParseException {
        // 1g - 1s = 99s: the silver count goes up, the amount goes down. The
        // gold tier is no longer shown, so it is not tinted; copper stays 0.
        assertEquals(Map.of(Denomination.SILVER, HudTemplates.Tint.DOWN),
                HudTemplates.changed(Coins.parse("1g"), Coins.parse("99s")));
    }

    @Test
    void countsAreNeverZeroPadded() {
        assertEquals("5", tier(Denomination.SILVER, 5).countText());
        assertEquals("0", tier(Denomination.COPPER, 0).countText());
        assertEquals("120", tier(Denomination.MYTHRIL, 120).countText());
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
        assertEquals(HudTemplates.PACK_DIR + "/" + tier.name() + ".png", denomination.texture(),
                "the public texture path is the pack image next to the documents");
        String document;
        try (InputStream in = HudTemplatesTest.class.getResourceAsStream(base + ".ui")) {
            assertNotNull(in, base + ".ui");
            document = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(document.contains("Group #" + tier.name() + " {"), "root id");
        assertTrue(document.contains("Label #Count {"), "count label");
        assertTrue(document.contains("HorizontalAlignment: End"), "right-aligned in its column");
        assertTrue(document.contains(denomination == Denomination.MYTHRIL
                ? "Anchor: (MinWidth: " : "Anchor: (Width: "), "column sized for two digits");
        assertTrue(document.contains("Background: \"" + tier.name() + ".png\";"), "image next to the count");
        assertTrue(document.contains("Style: @Normal;"), "the count starts in the tier's own colour");
        for (HudTemplates.Tint tint : HudTemplates.Tint.values()) {
            assertTrue(document.contains("@" + tint.styleName() + " = ("), "style " + tint.styleName());
        }
        assertTrue(document.contains("@Normal = (FontSize: 22, TextColor: " + denomination.color()),
                "palette from Denomination");
        String feedBase = "/Common/UI/Custom/" + HudTemplates.PACK_DIR + "/" + HudTemplates.FEED_DIR + "/" + tier.name();
        String feed;
        try (InputStream in = HudTemplatesTest.class.getResourceAsStream(feedBase + ".ui")) {
            assertNotNull(in, feedBase + ".ui");
            feed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(feed.contains("Group #" + tier.name() + " {"), "feed root id");
        assertTrue(feed.contains("Label #Count {"), "feed count label");
        assertTrue(feed.contains("Group #Icon {"), "feed icon, addressable for its tint");
        assertTrue(feed.contains("Background: (TexturePath: \"../" + tier.name() + ".png\");"), "the pill's image, one directory up");
        for (HudTemplates.Tint tint : List.of(HudTemplates.Tint.UP, HudTemplates.Tint.DOWN)) {
            for (int level = 0; level < ChangeFeed.FADE_STEPS; level++) {
                assertTrue(feed.contains("@" + tint.styleName(level) + " = ("), "feed style " + tint.styleName(level));
            }
        }
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
