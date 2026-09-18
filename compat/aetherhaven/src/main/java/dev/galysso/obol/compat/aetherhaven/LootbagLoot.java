package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.economy.api.GoldSource;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.lootbag.LootLaw;
import dev.galysso.obol.lootbag.api.LootbagItem;

import java.util.List;
import java.util.Objects;

/**
 * Aetherhaven's gold as items, with the lootbag add-on loaded.
 *
 * <p>Dungeon chests are the lootbag's: its {@code drops.json} already puts a
 * bag in every one of them, so Aetherhaven's chest gold would be a second
 * bag in the same chest. Nothing for that source. A pot a player breaks is
 * Aetherhaven's alone: its roll, through the {@link Rate}, names a rarity
 * under the server's laws ({@link LootbagItem#rarityOf}) and the pot drops
 * one bag of that rarity written as the tables write theirs
 * ({@link LootbagItem#bag}), so the amount follows {@code lootbag.json}.
 * At five silver a coin, a pot of one coin gives an Uncommon bag, two a
 * Rare one. A recipe that gave coins (a plot token salvaged, five coins)
 * gives one bag holding exactly that value: a refund is exact, so the bag
 * carries a fixed law, "Holds 25 silver".</p>
 *
 * <p>The only class of this mod that names the lootbag's types. It is made
 * only when the lootbag plugin is present, and never loaded otherwise.</p>
 */
final class LootbagLoot implements ObolGoldProvider.GoldLoot {

    private final Rate rate;

    LootbagLoot(Rate rate) {
        this.rate = Objects.requireNonNull(rate, "rate");
    }

    @Override
    public List<ItemStack> items(GoldSource source, long amount) {
        Coins value = rate.toCoins(amount);
        return switch (source) {
            case LOOT_CHEST -> List.of();
            case BREAKABLE_CONTAINER -> List.of(LootbagItem.bag(LootbagItem.rarityOf(value)));
            case RECIPE -> List.of(LootbagItem.stack(LootbagItem.rarityOf(value), LootLaw.fixed(value), 1));
        };
    }
}
