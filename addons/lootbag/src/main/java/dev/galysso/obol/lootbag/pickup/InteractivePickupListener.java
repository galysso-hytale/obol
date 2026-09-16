package dev.galysso.obol.lootbag.pickup;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.lootbag.LootbagOps;
import dev.galysso.obol.lootbag.api.LootbagItem;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * The harvest: a lootbag that a block hands straight to the player.
 *
 * <p>{@code ItemUtils.interactivelyPickupItem} is what a harvested block
 * (a crop, a bush: {@code BlockHarvestUtils}, {@code FarmingUtil}) does
 * with its drops: no item entity, the stack goes from the drop list to
 * {@code Player.giveItem}, after an {@link InteractivelyPickupItemEvent}
 * that can be cancelled. A bag among those drops is credited here and
 * the event cancelled, so the stack is never given and never exists on
 * the ground. Nothing to remove.</p>
 */
public final class InteractivePickupListener extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    private final LootbagOps ops;

    public InteractivePickupListener(LootbagOps ops) {
        super(InteractivelyPickupItemEvent.class);
        this.ops = Objects.requireNonNull(ops, "ops");
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InteractivelyPickupItemEvent event) {
        ItemStack stack = event.getItemStack();
        if (event.isCancelled() || !LootbagItem.isLootbag(stack)) {
            return;
        }
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        LootbagOps.Outcome outcome = ops.credit(player.getUuid(), stack, stack.getQuantity());
        if (outcome.ok()) {
            event.setCancelled(true);
            LootbagOps.chime(player);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
