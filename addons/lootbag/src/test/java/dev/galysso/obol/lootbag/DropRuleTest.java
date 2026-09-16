package dev.galysso.obol.lootbag;

import dev.galysso.obol.api.Coins;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropRuleTest {

    // Patterns

    @Test
    void aPatternWithoutSlashMatchesTheId() {
        DropRule.Matcher matcher = DropRule.Matcher.of("Zone*_Encounters_Tier2");
        assertTrue(matcher.matches("Zone1_Encounters_Tier2", "Prefabs/Zone1_Encounters_Tier2"));
        assertTrue(matcher.matches("zone3_encounters_tier2", null));
        assertFalse(matcher.matches("Zone1_Encounters_Tier1", "Prefabs/Zone1_Encounters_Tier1"));
        assertFalse(matcher.matches("Zone1_Trork_Tier2", "Prefabs/Zone1_Trork_Tier2"));
    }

    @Test
    void aPatternWithSlashMatchesThePath() {
        DropRule.Matcher matcher = DropRule.Matcher.of("NPCs/Undead/*");
        assertTrue(matcher.matches("Drop_Skeleton", "NPCs/Undead/Drop_Skeleton"));
        assertTrue(matcher.matches("Drop_Skeleton", "npcs/undead/drop_skeleton"));
        assertFalse(matcher.matches("Drop_Skeleton", "NPCs/Undead/Skeleton/Drop_Skeleton"));
        assertFalse(matcher.matches("Drop_Skeleton", null));
        assertFalse(matcher.matches("NPCs/Undead/Drop_Skeleton", null));
    }

    @Test
    void doubleStarCrossesFolders() {
        DropRule.Matcher matcher = DropRule.Matcher.of("NPCs/Intelligent/**");
        assertTrue(matcher.matches("Drop_Trork_Warrior", "NPCs/Intelligent/Trork/Drop_Trork_Warrior"));
        assertTrue(matcher.matches("Drop_X", "NPCs/Intelligent/Drop_X"));
        assertFalse(matcher.matches("Drop_Skeleton", "NPCs/Undead/Drop_Skeleton"));
    }

    @Test
    void patternsAreLiteralButForStars() {
        DropRule.Matcher matcher = DropRule.Matcher.of("Drop_Skeleton.Knight");
        assertFalse(matcher.matches("Drop_SkeletonXKnight", null));
        assertTrue(matcher.matches("Drop_Skeleton.Knight", null));
    }

    @Test
    void severalPatternsAreAnyOf() {
        DropRule.Matcher matcher = DropRule.Matcher.of("Drop_Golem_*", "NPCs/Boss/*");
        assertTrue(matcher.matches("Drop_Golem_Firesteel", "NPCs/Elemental/Drop_Golem_Firesteel"));
        assertTrue(matcher.matches("Drop_Dragon", "NPCs/Boss/Drop_Dragon"));
        assertFalse(matcher.matches("Drop_Dragon", "NPCs/Elemental/Drop_Dragon"));
        assertEquals("Drop_Golem_*, NPCs/Boss/*", matcher.text());
    }

    // Resolution

    @Test
    void bagsBecomeAChoiceOfLootbagsWeightedByChance() {
        Map<Rarity, Integer> bags = new LinkedHashMap<>();
        bags.put(Rarity.Common, 70);
        bags.put(Rarity.Rare, 5);
        DropRule.Resolved rule = DropRule.bags(35, bags, "Prefabs/Zone*_Encounters_Tier1").resolve();
        BsonDocument lot = rule.lot();
        assertEquals("Choice", lot.getString("Type").getValue());
        assertEquals(35, lot.getDouble("Weight").getValue());
        assertEquals(2, lot.getArray("Containers").size());
        BsonDocument first = lot.getArray("Containers").get(0).asDocument();
        assertEquals("ObolLootbag", first.getString("Type").getValue());
        assertEquals("Common", first.getString("Rarity").getValue());
        assertEquals(70, first.getDouble("Weight").getValue());
        assertEquals(35, rule.chance());
        assertEquals("35% one of common 70, rare 5", rule.text());
        assertEquals("Prefabs/Zone*_Encounters_Tier1", rule.patterns());
    }

    @Test
    void rarityBecomesOneLootbagWithItsLaw() throws Exception {
        LootLaw law = LootLaw.uniform(Coins.parse("20g"), Coins.parse("50g"));
        DropRule.Resolved rule = DropRule.rarity(100, Rarity.Legendary, law, "NPCs/Boss/*").resolve();
        BsonDocument lot = rule.lot();
        assertEquals("ObolLootbag", lot.getString("Type").getValue());
        assertEquals("Legendary", lot.getString("Rarity").getValue());
        assertEquals(100, lot.getDouble("Weight").getValue());
        assertEquals("Uniform", lot.getString("Distribution").getValue());
        assertEquals("20g", lot.getString("Min").getValue());
        assertEquals("50g", lot.getString("Max").getValue());
        assertTrue(rule.text().startsWith("100% a legendary bag of "));
    }

    @Test
    void droplistBecomesAnInclusion() {
        DropRule rule = new DropRule();
        rule.droplists = new String[] {"Drop_Skeleton_*"};
        rule.chance = 8.5;
        rule.droplist = " Obol_Lootbag_Tier1 ";
        DropRule.Resolved resolved = rule.resolve();
        assertEquals("Droplist", resolved.lot().getString("Type").getValue());
        assertEquals("Obol_Lootbag_Tier1", resolved.lot().getString("DroplistId").getValue());
        assertEquals(8.5, resolved.lot().getDouble("Weight").getValue());
        assertEquals("8.5% the table Obol_Lootbag_Tier1", resolved.text());
        assertEquals("Obol_Lootbag_Tier1", rule.droplist());
    }

    @Test
    void chanceDefaultsToAlways() {
        DropRule rule = new DropRule();
        rule.droplists = new String[] {"X"};
        rule.rarity = "epic";
        DropRule.Resolved resolved = rule.resolve();
        assertEquals(100, resolved.chance());
        assertEquals("Epic", resolved.lot().getString("Rarity").getValue());
    }

    @Test
    void aRuleThatDoesNotHoldSaysWhy() {
        assertReason("Droplists", ruleWith(null, 10.0, null, "Epic", null));
        assertReason("Droplists", ruleWith(new String[] {" "}, 10.0, null, "Epic", null));
        assertReason("Chance", ruleWith(new String[] {"X"}, 101.0, null, "Epic", null));
        assertReason("Chance", ruleWith(new String[] {"X"}, -1.0, null, "Epic", null));
        assertReason("one of", ruleWith(new String[] {"X"}, 10.0, null, null, null));
        assertReason("only one", ruleWith(new String[] {"X"}, 10.0, null, "Epic", "T"));
        assertReason("not a rarity", ruleWith(new String[] {"X"}, 10.0, null, "Mythic", null));
        assertReason("not a rarity", ruleWith(new String[] {"X"}, 10.0, Map.of("Mythic", 1.0), null, null));
        assertReason("above zero", ruleWith(new String[] {"X"}, 10.0, Map.of("Epic", 0.0), null, null));
        assertReason("no rarity", ruleWith(new String[] {"X"}, 10.0, Map.of(), null, null));
        assertReason("blank", ruleWith(new String[] {"X"}, 10.0, null, null, " "));
        DropRule lawWithoutRarity = ruleWith(new String[] {"X"}, 10.0, null, null, "T");
        lawWithoutRarity.law.min = "1g";
        assertReason("Rarity only", lawWithoutRarity);
        DropRule badLaw = ruleWith(new String[] {"X"}, 10.0, null, "Epic", null);
        badLaw.law.min = "5g";
        badLaw.law.max = "1g";
        assertThrows(IllegalArgumentException.class, badLaw::resolve);
    }

    private static DropRule ruleWith(String[] droplists, Double chance, Map<String, Double> bags, String rarity,
                                     String droplist) {
        DropRule rule = new DropRule();
        rule.droplists = droplists;
        rule.chance = chance;
        rule.bags = bags == null ? null : new LinkedHashMap<>(bags);
        rule.rarity = rarity;
        rule.droplist = droplist;
        return rule;
    }

    private static void assertReason(String fragment, DropRule rule) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, rule::resolve);
        assertTrue(e.getMessage().contains(fragment), fragment + " not in: " + e.getMessage());
    }
}
