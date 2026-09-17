package dev.galysso.obol.lootbag.api;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.lootbag.Rarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootbagItemTest {

    @Test
    void rarityFollowsTheValueUnderTheDefaultLaws() {
        assertEquals(Rarity.Common, LootbagItem.rarityOf(Coins.ZERO));
        assertEquals(Rarity.Common, LootbagItem.rarityOf(Coins.ofCopper(50)));
        assertEquals(Rarity.Uncommon, LootbagItem.rarityOf(Coins.ofCopper(51)));
        assertEquals(Rarity.Uncommon, LootbagItem.rarityOf(Coins.of(Denomination.SILVER, 5)));
        assertEquals(Rarity.Rare, LootbagItem.rarityOf(Coins.of(Denomination.SILVER, 25)));
        assertEquals(Rarity.Rare, LootbagItem.rarityOf(Coins.of(Denomination.SILVER, 50)));
        assertEquals(Rarity.Epic, LootbagItem.rarityOf(Coins.of(Denomination.GOLD, 5)));
        assertEquals(Rarity.Legendary, LootbagItem.rarityOf(Coins.of(Denomination.GOLD, 50)));
        assertEquals(Rarity.Legendary, LootbagItem.rarityOf(Coins.of(Denomination.GOLD, 5_000)));
    }
}
