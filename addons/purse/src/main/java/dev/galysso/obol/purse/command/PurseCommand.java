package dev.galysso.obol.purse.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsParseException;
import dev.galysso.obol.purse.PurseOps;
import dev.galysso.obol.purse.api.PurseItem;
import dev.galysso.obol.purse.ui.PursePage;

import java.util.Objects;

/**
 * {@code /purse put <amount>}, {@code /purse take}, {@code /purse open}:
 * the purse in the active hotbar slot, driven from the chat. A testing
 * aid, not the way players use purses (they right-click), hence one
 * permission node, {@code obol.purse.debug}, granted to
 * {@code hytale:Admin} by default.
 */
public final class PurseCommand extends CommandBase {

    static final String PERMISSION = "obol.purse.debug";

    public PurseCommand(PurseOps ops) {
        super("purse", "Obol Purse testing aid: acts on the purse in hand.");
        Objects.requireNonNull(ops, "ops");
        requirePermission(PERMISSION);
        setPermissionGroups("hytale:Admin");
        addSubCommand(new Put(ops));
        addSubCommand(new Take(ops));
        addSubCommand(new Open(ops));
    }

    @Override
    protected void executeSync(CommandContext context) {
        context.sendMessage(getUsageString(context.sender()));
    }

    /** Shared shape: the purse in the active hotbar slot of the sender. */
    private abstract static class Sub extends AbstractPlayerCommand {

        final PurseOps ops;

        Sub(PurseOps ops, String name, String description) {
            super(name, description);
            this.ops = ops;
            requirePermission(PERMISSION);
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef playerRef, World world) {
            InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            // Runs on the world thread, past CommandManager's exception
            // handling: answer, do not throw.
            if (hotbar == null) {
                context.sendMessage(Message.raw("No hotbar."));
                return;
            }
            ItemContainer container = hotbar.getInventory();
            short slot = hotbar.getActiveSlot();
            if (!PurseItem.isPurse(container.getItemStack(slot))) {
                context.sendMessage(Message.raw("Hold a purse first."));
                return;
            }
            ItemContainer inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
            apply(context, store, ref, playerRef, container, slot, inventory);
        }

        /**
         * @param container the hotbar, holding a purse in {@code slot}
         * @param inventory the whole inventory, for the rest of a split stack
         */
        abstract void apply(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                            PlayerRef playerRef, ItemContainer container, short slot, ItemContainer inventory);
    }

    private static final class Put extends Sub {

        private final RequiredArg<String> amount;

        Put(PurseOps ops) {
            super(ops, "put", "Puts an amount of your balance in the purse in hand.");
            amount = withRequiredArg("amount", "Amount, e.g. 2g 35s.", ArgTypes.GREEDY_STRING);
        }

        @Override
        void apply(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                   PlayerRef playerRef, ItemContainer container, short slot, ItemContainer inventory) {
            Coins coins;
            try {
                coins = Coins.parse(amount.get(context));
            } catch (CoinsParseException e) {
                context.sendMessage(Message.raw(e.getMessage() + ". Example: 2g 35s"));
                return;
            }
            PurseOps.Outcome outcome = ops.put(playerRef.getUuid(), container, slot, inventory, coins);
            context.sendMessage(Message.raw(outcome.message()));
        }
    }

    private static final class Take extends Sub {

        Take(PurseOps ops) {
            super(ops, "take", "Takes everything out of the purse in hand.");
        }

        @Override
        void apply(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                   PlayerRef playerRef, ItemContainer container, short slot, ItemContainer inventory) {
            PurseOps.Outcome outcome = ops.takeAll(playerRef.getUuid(), container, slot);
            context.sendMessage(Message.raw(outcome.message()));
        }
    }

    private static final class Open extends Sub {

        Open(PurseOps ops) {
            super(ops, "open", "Opens the page of the purse in hand, without the right-click.");
        }

        @Override
        void apply(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                   PlayerRef playerRef, ItemContainer container, short slot, ItemContainer inventory) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("No player entity."));
                return;
            }
            player.getPageManager().openCustomPage(ref, store, new PursePage(playerRef, ops, container, slot, inventory));
        }
    }
}
