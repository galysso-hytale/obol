package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.economy.api.GoldSource;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.lootbag.LootLaw;
import dev.galysso.obol.lootbag.Rarity;
import dev.galysso.obol.lootbag.api.LootbagItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootbagLootTest {

    private final LootbagLoot loot = new LootbagLoot(Rate.DEFAULT);

    @BeforeAll
    static void items() throws ReflectiveOperationException {
        ItemAssets.install();
    }

    @AfterAll
    static void restore() throws ReflectiveOperationException {
        ItemAssets.restore();
    }

    @Test
    void dungeonChestsAreLeftToTheLootbag() {
        assertTrue(loot.items(GoldSource.LOOT_CHEST, 7).isEmpty());
    }

    @Test
    void aBrokenPotDropsOneBagOfTheRarityItsRollIsWorth() {
        List<ItemStack> items = loot.items(GoldSource.BREAKABLE_CONTAINER, 1);
        assertEquals(1, items.size());
        ItemStack bag = items.get(0);
        assertEquals(1, bag.getQuantity());
        // One coin at five silver: 5s, an Uncommon value, and the bag rolls the server's Uncommon law.
        assertEquals(Rarity.Uncommon, LootbagItem.rarity(bag).orElseThrow());
        assertEquals(LootLaw.defaultFor(Rarity.Uncommon), LootbagItem.law(bag).orElseThrow());
    }

    @Test
    void theRarityFollowsTheValue() {
        assertEquals(Rarity.Rare, LootbagItem.rarity(loot.items(GoldSource.BREAKABLE_CONTAINER, 2).get(0)).orElseThrow());
        assertEquals(Rarity.Rare, LootbagItem.rarity(loot.items(GoldSource.BREAKABLE_CONTAINER, 10).get(0)).orElseThrow());
        assertEquals(Rarity.Epic, LootbagItem.rarity(loot.items(GoldSource.BREAKABLE_CONTAINER, 11).get(0)).orElseThrow());
    }

    @Test
    void aSalvagedTokenGivesOneBagHoldingExactlyItsRefund() {
        List<ItemStack> items = loot.items(GoldSource.RECIPE, 5);
        assertEquals(1, items.size());
        ItemStack bag = items.get(0);
        assertEquals(1, bag.getQuantity());
        // Five coins at five silver: 25s, a Rare value, and the bag says exactly that.
        assertEquals(Rarity.Rare, LootbagItem.rarity(bag).orElseThrow());
        assertEquals(LootLaw.fixed(Coins.of(Denomination.SILVER, 25)), LootbagItem.law(bag).orElseThrow());
    }

    @Test
    void nothingForNothing() {
        assertTrue(new ObolGoldProvider(Rate.DEFAULT, loot).goldItems(GoldSource.BREAKABLE_CONTAINER, "x", 0).isEmpty());
    }
}
