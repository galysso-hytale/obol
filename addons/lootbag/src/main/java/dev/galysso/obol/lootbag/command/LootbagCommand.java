package dev.galysso.obol.lootbag.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.lootbag.LootLaw;
import dev.galysso.obol.lootbag.LootbagConfig;
import dev.galysso.obol.lootbag.Rarity;
import dev.galysso.obol.lootbag.api.LootbagItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /lootbag}, a testing aid, not the way players get bags (they loot
 * them), hence one permission node, {@code obol.lootbag.debug}, granted to
 * {@code hytale:Admin} by default.
 *
 * <ul>
 *   <li>{@code give <rarity> [--count=n] [--law=...]}: bags in the sender's
 *   inventory, carrying the rarity's law or the one written
 *   ({@code 5g}, {@code uniform 1g 5g}, see {@link LootLaw#parse}). With
 *   {@code RevealAmount} on, rolled here like a drop: one stack per bag.</li>
 *   <li>{@code roll <droplist> [count]}: rolls a drop table and gives what
 *   falls, to see a table's bags with their tooltip.</li>
 *   <li>{@code rarities}: the law of each rarity, as {@code lootbag.json}
 *   has it.</li>
 * </ul>
 */
public final class LootbagCommand extends CommandBase {

    static final String PERMISSION = "obol.lootbag.debug";

    public LootbagCommand(LootbagConfig config) {
        super("lootbag", "Obol Lootbag testing aid.");
        requirePermission(PERMISSION);
        setPermissionGroups("hytale:Admin");
        addSubCommand(new Give(config));
        addSubCommand(new Roll());
        addSubCommand(new Rarities(config));
    }

    @Override
    protected void executeSync(CommandContext context) {
        context.sendMessage(getUsageString(context.sender()));
    }

    /** Gives {@code stacks} to the player and says what did not fit. */
    private static void give(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                             List<ItemStack> stacks, String what) {
        int wanted = 0;
        int given = 0;
        for (ItemStack stack : stacks) {
            wanted += stack.getQuantity();
            ItemStackTransaction transaction = Player.giveItem(stack, ref, store);
            ItemStack remainder = transaction.getRemainder();
            given += stack.getQuantity() - (ItemStack.isEmpty(remainder) ? 0 : remainder.getQuantity());
        }
        if (given == 0) {
            context.sendMessage(Message.raw("No room for " + what + "."));
        } else if (given < wanted) {
            context.sendMessage(Message.raw("Gave " + given + " of " + wanted + " " + what
                    + ", no room for the other " + (wanted - given) + "."));
        } else {
            context.sendMessage(Message.raw("Gave " + given + " " + what + "."));
        }
    }

    private static final class Give extends AbstractPlayerCommand {

        private final LootbagConfig config;
        private final RequiredArg<Rarity> rarity;
        private final DefaultArg<Integer> count;
        private final OptionalArg<String> law;

        Give(LootbagConfig config) {
            super("give", "Puts lootbags of a rarity in your inventory.");
            this.config = config;
            rarity = withRequiredArg("rarity", "Common, Uncommon, Rare, Epic or Legendary.",
                    ArgTypes.forEnum("rarity", Rarity.class));
            count = withDefaultArg("count", "How many bags, 1 if left out.", ArgTypes.INTEGER, 1, "1");
            law = withOptionalArg("law", "What a bag gives, instead of the rarity's law: an amount (5g), "
                    + "or uniform|triangular|loguniform with its amounts (triangular 5s 50s 12s), "
                    + "then step <amount> if wanted.", ArgTypes.GREEDY_STRING);
            requirePermission(PERMISSION);
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef playerRef, World world) {
            // Runs on the world thread, past CommandManager's exception
            // handling: answer, do not throw.
            Rarity wanted = rarity.get(context);
            int quantity = count.get(context);
            if (quantity < 1) {
                context.sendMessage(Message.raw("Count must be at least 1."));
                return;
            }
            LootLaw chosen = config.law(wanted);
            if (law.provided(context)) {
                try {
                    chosen = LootLaw.parse(law.get(context));
                } catch (IllegalArgumentException e) {
                    context.sendMessage(Message.raw("Not a loot law: " + e.getMessage()));
                    return;
                }
            }
            List<ItemStack> bags = new ArrayList<>();
            if (config.revealAmount() && !chosen.isFixed()) {
                // As the drop container does: rolled now, each bag says
                // its amount, and so is its own stack.
                for (int i = 0; i < quantity; i++) {
                    LootLaw rolled = LootLaw.fixed(chosen.roll(ThreadLocalRandom.current()::nextDouble));
                    bags.add(LootbagItem.stack(wanted, rolled, 1, config.openOn()));
                }
            } else {
                bags.add(LootbagItem.stack(wanted, chosen, quantity, config.openOn()));
            }
            give(context, store, ref, bags, wanted.lower() + (quantity == 1 ? " lootbag" : " lootbags"));
        }
    }

    private static final class Roll extends AbstractPlayerCommand {

        private final RequiredArg<String> droplist;
        private final DefaultArg<Integer> count;

        Roll() {
            super("roll", "Rolls a drop table and gives you what falls.");
            droplist = withRequiredArg("droplist", "The table's id, the name of its file under Server/Drops.",
                    ArgTypes.STRING);
            count = withDefaultArg("count", "How many rolls, 1 if left out.", ArgTypes.INTEGER, 1, "1");
            requirePermission(PERMISSION);
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef playerRef, World world) {
            String id = droplist.get(context);
            int rolls = count.get(context);
            if (rolls < 1) {
                context.sendMessage(Message.raw("Count must be at least 1."));
                return;
            }
            if (ItemDropList.getAssetMap().getAsset(id) == null) {
                context.sendMessage(Message.raw("No drop table \"" + id + "\"."));
                return;
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < rolls; i++) {
                stacks.addAll(ItemModule.get().getRandomItemDrops(id));
            }
            if (stacks.isEmpty()) {
                context.sendMessage(Message.raw("Nothing fell from " + id + " in " + rolls
                        + (rolls == 1 ? " roll." : " rolls.")));
                return;
            }
            give(context, store, ref, stacks, "items from " + id);
        }
    }

    private static final class Rarities extends CommandBase {

        private final LootbagConfig config;

        Rarities(LootbagConfig config) {
            super("rarities", "The amount law of each rarity, as lootbag.json has it.");
            this.config = config;
            requirePermission(PERMISSION);
        }

        @Override
        protected void executeSync(CommandContext context) {
            for (Rarity rarity : Rarity.values()) {
                LootLaw law = config.law(rarity);
                context.sendMessage(Message.join(Message.raw(rarity.name() + ": "), LootbagItem.holds(law),
                        Message.raw(law.isFixed() ? "" : " (" + law + ", step " + law.effectiveStep() + ")")));
            }
        }
    }
}
