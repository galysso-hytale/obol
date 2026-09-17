package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hexvane.aetherhaven.economy.api.EconomyProvider;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Obol;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns Aetherhaven gold coin items into Obol coins the moment a player
 * holds them.
 *
 * <p>With Obol as the economy, nothing in Aetherhaven's Java gives coin
 * items any more, but the game still does: the salvage recipes of plot
 * tokens (five coins a token), villager gifts, the festival barter cart,
 * and coins a player kept from before the switch. {@link InventoryChangeEvent}
 * fires on the world thread for every change of a player's inventory. On
 * each, every stack of coins is taken out of the combined inventory, then
 * their sum is deposited once, one line in Obol's HUD feed. Coins in a
 * chest stay items until someone picks them up. If Obol's store refuses
 * the deposit, the stacks go back where they were.</p>
 *
 * <p>Idle when the active provider is not ours (Aetherhaven's config can
 * force its own coins with {@code EconomyProvider = COINS}).</p>
 */
final class CoinItemAbsorbSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    private final EconomyProvider provider;
    private final Rate rate;
    private final HytaleLogger logger;

    CoinItemAbsorbSystem(EconomyProvider provider, Rate rate, HytaleLogger logger) {
        super(InventoryChangeEvent.class);
        this.provider = Objects.requireNonNull(provider, "provider");
        this.rate = Objects.requireNonNull(rate, "rate");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InventoryChangeEvent event) {
        if (AetherhavenEconomy.provider() != provider) {
            return;
        }
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        CombinedItemContainer inventory = InventoryComponent.getCombined(commandBuffer, ref, InventoryComponent.EVERYTHING);
        if (inventory == null) {
            return;
        }
        List<Removed> removed = new ArrayList<>();
        long coins = 0L;
        for (short slot = 0; slot < inventory.getCapacity(); slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (ItemStack.isEmpty(stack) || !AetherhavenConstants.ITEM_GOLD_COIN.equals(stack.getItemId())) {
                continue;
            }
            int count = stack.getQuantity();
            if (!inventory.removeItemStackFromSlot(slot, stack, count).succeeded()) {
                continue;
            }
            removed.add(new Removed(slot, stack));
            coins += count;
        }
        if (removed.isEmpty()) {
            return;
        }
        try {
            Obol.playerWallet(player.getUuid()).deposit(rate.toCoins(coins));
        } catch (RuntimeException e) {
            for (Removed r : removed) {
                inventory.addItemStackToSlot(r.slot(), r.stack());
            }
            logger.atSevere().withCause(e).log("Could not credit %s for %d gold coin(s), coins returned", player.getUuid(), coins);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    private record Removed(short slot, ItemStack stack) {}
}
