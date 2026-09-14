package dev.galysso.obol.purse.interaction;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.purse.PurseConfig;
import dev.galysso.obol.purse.PurseOps;
import dev.galysso.obol.purse.api.PurseItem;
import dev.galysso.obol.purse.ui.GivePopup;
import dev.galysso.obol.purse.ui.PursePage;

import java.util.Objects;

/**
 * What the purse's right-click opens. The item declares
 * {@code {"Type": "OpenCustomUI", "Page": {"Id": "ObolPurse"}}} and the
 * server asks this supplier, registered under {@link #PAGE_ID}, for the
 * page: the purse page, or, when a player is in the crosshair and the
 * config allows it, the confirmation to hand the content over.
 *
 * <p>One entry point for both: a second interaction would need a second
 * item or a client-side chain to tell them apart. Returning {@code null}
 * opens nothing.</p>
 */
public final class PursePageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    /** The {@code Page.Id} of the item's interaction. */
    public static final String PAGE_ID = "ObolPurse";

    private final PurseOps ops;
    private final PurseConfig config;

    public PursePageSupplier(PurseOps ops, PurseConfig config) {
        this.ops = Objects.requireNonNull(ops, "ops");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public CustomUIPage tryCreate(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor,
                                  PlayerRef playerRef, InteractionContext context) {
        ItemStack held = context.getHeldItem();
        ItemContainer container = context.getHeldItemContainer();
        if (!PurseItem.isPurse(held) || container == null) {
            return null;
        }
        short slot = context.getHeldItemSlot();
        PlayerRef target = targetedPlayer(ref, accessor, context, playerRef);
        if (target != null && config.directGive()) {
            Coins content = ops.content(held);
            if (content.equals(Coins.ZERO)) {
                playerRef.sendMessage(Message.raw("The purse is empty."));
                return null;
            }
            return new GivePopup(playerRef, ops, container, slot, target, content);
        }
        ItemContainer inventory = InventoryComponent.getCombined(accessor, ref, InventoryComponent.HOTBAR_FIRST);
        return new PursePage(playerRef, ops, container, slot, inventory);
    }

    /**
     * {@return the other player the interaction targets, or {@code null}
     * when it targets nothing, something else, or the player themself}
     */
    private static PlayerRef targetedPlayer(Ref<EntityStore> self, ComponentAccessor<EntityStore> accessor,
                                            InteractionContext context, PlayerRef playerRef) {
        Ref<EntityStore> target = context.getTargetEntity();
        if (target == null || !target.isValid() || target.getIndex() == self.getIndex()) {
            return null;
        }
        PlayerRef other = accessor.getComponent(target, PlayerRef.getComponentType());
        if (other == null || other.getUuid().equals(playerRef.getUuid())) {
            return null;
        }
        return other;
    }
}
