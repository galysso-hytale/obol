package dev.galysso.obol.lootbag.pickup;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SmartMoveType;
import com.hypixel.hytale.protocol.packets.inventory.MoveItemStack;
import com.hypixel.hytale.protocol.packets.inventory.SmartMoveItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.io.handlers.game.InventoryPacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.lootbag.LootbagOps;
import dev.galysso.obol.lootbag.api.LootbagItem;

import java.util.Objects;

/**
 * The chest: a lootbag clicked out of a container window goes to the
 * balance, not to the inventory.
 *
 * <p>The click is a packet, {@code MoveItemStack} (a drag or a click) or
 * {@code SmartMoveItemStack} (shift-click), handled by the game's
 * {@link InventoryPacketHandler}. The game registers one consumer per
 * packet id on each player's {@link GamePacketHandler} at connection, and
 * registering again replaces it. So at {@code PlayerReadyEvent} this class
 * puts its own consumers on the two ids and keeps the game's handler to
 * hand over everything that is not a bag leaving a window: the source is
 * a section of the player's own inventory (a negative id), or the slot
 * holds no bag, or the move stays in the window. For a bag leaving a
 * window, the bag is taken out of the window's container, the player is
 * credited, and the sound plays for the player. The inventory is never
 * touched, so a full one changes nothing.</p>
 *
 * <p>Like the game's handler, the work runs on the world thread.</p>
 */
public final class ChestMoveHandler {

    private final LootbagOps ops;
    private final HytaleLogger logger;

    public ChestMoveHandler(LootbagOps ops, HytaleLogger logger) {
        this.ops = Objects.requireNonNull(ops, "ops");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Wraps the two move handlers of {@code player}. Safe to call again for the same player. */
    public void install(PlayerRef player) {
        if (!(player.getPacketHandler() instanceof GamePacketHandler handler)) {
            logger.atWarning().log("No game packet handler for %s, chest clicks on lootbags will move them",
                    player.getUuid());
            return;
        }
        InventoryPacketHandler vanilla = handler.getSubPacketHandler(InventoryPacketHandler.class);
        if (vanilla == null) {
            logger.atWarning().log("No inventory packet handler for %s, chest clicks on lootbags will move them",
                    player.getUuid());
            return;
        }
        handler.registerHandler(MoveItemStack.PACKET_ID, packet -> {
            MoveItemStack move = (MoveItemStack) packet;
            // Leaving a window (id >= 0) for the inventory (id < 0).
            if (move.fromSectionId >= 0 && move.toSectionId < 0) {
                takeFromWindow(player, move.fromSectionId, move.fromSlotId, move.quantity, () -> vanilla.handle(move));
            } else {
                vanilla.handle(move);
            }
        });
        handler.registerHandler(SmartMoveItemStack.PACKET_ID, packet -> {
            SmartMoveItemStack move = (SmartMoveItemStack) packet;
            // EquipOrMergeStack pulls stacks into the clicked slot: the
            // bag stays in the window, nothing to credit.
            if (move.fromSectionId >= 0 && move.moveType != SmartMoveType.EquipOrMergeStack) {
                takeFromWindow(player, move.fromSectionId, move.fromSlotId, move.quantity, () -> vanilla.handle(move));
            } else {
                vanilla.handle(move);
            }
        });
    }

    /**
     * On the world thread: credits the bags of the slot if it holds some,
     * else runs {@code otherwise}, the game's own handling of the packet.
     */
    private void takeFromWindow(PlayerRef player, int sectionId, int slotId, int quantity, Runnable otherwise) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            ItemContainer container = InventoryUtils.getSectionById(ref, sectionId, store);
            ItemStack stack = container == null ? null : container.getItemStack((short) slotId);
            if (!LootbagItem.isLootbag(stack)) {
                otherwise.run();
                return;
            }
            // Bags out of the window first, then the deposit: the window's
            // container notifies its windows, the client sees the bags go.
            int taken = quantity > 0 ? quantity : stack.getQuantity();
            LootbagOps.Outcome outcome = ops.open(container, (short) slotId, stack, player.getUuid(), taken);
            if (outcome.ok()) {
                LootbagOps.chime(player);
            }
        });
    }
}
