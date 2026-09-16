package dev.galysso.obol.lootbag.pickup;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.lootbag.LootbagOps;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * The net: in {@code Pickup} mode, a lootbag that reaches a player's
 * inventory by any other way is opened there.
 *
 * <p>{@link InventoryChangeEvent} is sent on the world thread for every
 * change of a player's hotbar, storage, backpack or armour (the event the
 * objectives use for "collect 10 of X"). On each, the combined inventory
 * is walked by {@link LootbagOps#openAll}, which takes every stack of
 * bags out first and makes one deposit. That is where a
 * gift, {@code /lootbag give}, a mod's {@code giveItem} or a bag that
 * escaped the ground and the chest end up. The removal queues a change
 * of its own, which finds nothing on the next tick: the events are
 * queued by the container and drained by a ticking system, not raised
 * inside the change, so there is no reentrance.</p>
 */
public final class InventorySweep extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    private final LootbagOps ops;

    public InventorySweep(LootbagOps ops) {
        super(InventoryChangeEvent.class);
        this.ops = Objects.requireNonNull(ops, "ops");
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InventoryChangeEvent event) {
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        CombinedItemContainer inventory = InventoryComponent.getCombined(commandBuffer, ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        LootbagOps.Outcome outcome = ops.openAll(inventory, player.getUuid());
        if (outcome.ok()) {
            LootbagOps.chime(player, outcome);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
