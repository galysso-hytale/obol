package dev.galysso.obol.lootbag.interaction;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.lootbag.LootbagOps;
import dev.galysso.obol.lootbag.api.LootbagItem;

import javax.annotation.Nonnull;

/**
 * The {@code ObolOpenLootbag} interaction: pours lootbags into the balance
 * of the player who runs it.
 *
 * <pre>
 * { "Type": "ObolOpenLootbag", "All": true, "Effects": { "LocalSoundEventId": "SFX_Obol_Lootbag_Open_Rare" } }
 * </pre>
 *
 * <p>Two ways in. From a click ({@code Secondary}, the chain of the item
 * template): one bag of the held stack or, with {@code All}, the whole
 * stack, taken out of the hand by {@link LootbagOps#open}. On a bag with
 * a fixed amount (what {@code RevealAmount} writes, bags that hardly
 * stack) {@code All} opens every bag of the inventory instead
 * ({@link LootbagOps#openAll}, {@link LootbagItem#crouchOpensAll}). The client runs
 * that chain too and plays the {@code Effects}. From the game's pickup
 * ({@code Pickup}, the {@code Obol_Lootbag_Pickup} root the plugin wires
 * in {@code Pickup} mode): the target is the item entity on the ground,
 * the whole stack is credited by {@link LootbagOps#credit}, and the game
 * removes the entity after the chain. That chain is started by the server
 * and never sent to the client, so the sound is played from here.</p>
 *
 * <p>Registered in {@code Interaction.CODEC} by the plugin before the
 * assets are read, since the item template names it. The codec captures
 * the ops, as the drop container captures the config.</p>
 */
public final class OpenLootbagInteraction extends SimpleInstantInteraction {

    /** The value of {@code Type} that names this interaction. */
    public static final String ID = "ObolOpenLootbag";

    private final LootbagOps ops;
    private boolean all;

    private OpenLootbagInteraction(LootbagOps ops) {
        this.ops = ops;
    }

    /** {@return the codec of this interaction, crediting through {@code ops}} */
    public static BuilderCodec<OpenLootbagInteraction> codec(LootbagOps ops) {
        return BuilderCodec.builder(OpenLootbagInteraction.class, () -> new OpenLootbagInteraction(ops),
                        SimpleInstantInteraction.CODEC)
                .documentation("Pours Obol lootbags into the player's balance: the held stack on a click, "
                        + "the targeted item entity when run from a Pickup chain.")
                .<Boolean>appendInherited(new KeyedCodec<>("All", Codec.BOOLEAN),
                        (i, v) -> i.all = v, i -> i.all, (i, parent) -> i.all = parent.all)
                .documentation("Whether a click opens the whole held stack rather than one bag "
                        + "(every bag of the inventory when the held bag has a fixed amount, as RevealAmount "
                        + "writes, since such bags hardly stack). A Pickup always takes the whole stack.")
                .add()
                .build();
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        // The server moves the items: the client waits for its word, as
        // it does for ModifyInventory.
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        assert commandBuffer != null;
        PlayerRef player = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            // An NPC with a bag in hand: no balance to credit.
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (type == InteractionType.Pickup) {
            pickup(context, commandBuffer, player);
        } else {
            click(context, commandBuffer, ref, player);
        }
    }

    private void click(InteractionContext context, CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> ref,
                       PlayerRef player) {
        ItemStack held = context.getHeldItem();
        ItemContainer container = context.getHeldItemContainer();
        if (held == null || container == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        short slot = context.getHeldItemSlot();
        LootbagOps.Outcome outcome;
        if (all && ops.lawOf(held).map(LootbagItem::crouchOpensAll).orElse(false)) {
            // A fixed amount (what RevealAmount writes): bags hardly stack,
            // the crouch empties the inventory of its bags, the held one
            // among them. The rule is the bag's, as its tooltip says.
            CombinedItemContainer inventory = InventoryComponent.getCombined(commandBuffer, ref,
                    InventoryComponent.HOTBAR_STORAGE_BACKPACK);
            outcome = ops.openAll(inventory, player.getUuid());
        } else {
            outcome = ops.open(container, slot, held, player.getUuid(), all ? held.getQuantity() : 1);
        }
        if (!outcome.ok()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        context.setHeldItem(container.getItemStack(slot));
    }

    private void pickup(InteractionContext context, CommandBuffer<EntityStore> commandBuffer, PlayerRef player) {
        Ref<EntityStore> target = context.getTargetEntity();
        ItemComponent item = target == null || !target.isValid() ? null
                : commandBuffer.getComponent(target, ItemComponent.getComponentType());
        if (item == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ItemStack stack = item.getItemStack();
        LootbagOps.Outcome outcome = ops.credit(player.getUuid(), stack, stack.getQuantity());
        if (!outcome.ok()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        LootbagOps.chime(player, outcome);
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                                    @Nonnull CooldownHandler cooldownHandler) {
        // The simulation (run for an entity without a client) must not
        // credit a second time.
    }

    @Override
    public String toString() {
        return "OpenLootbagInteraction{all=" + all + "} " + super.toString();
    }
}
