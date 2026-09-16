package dev.galysso.obol.lootbag.pickup;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.galysso.obol.lootbag.Rarity;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Puts the {@code Pickup} interaction on the five lootbag items, so that
 * the game's own pickup ({@code PlayerItemEntityPickupSystem}) runs the
 * {@link #ROOT_ID} chain on the player who walks over a bag instead of
 * giving the item. The chain credits the bag, the game removes the
 * entity, and the inventory is never looked at.
 *
 * <p>The entry is not in the item JSON: the pack is the same on every
 * server, and only a server in {@code Pickup} mode wants bags on the
 * ground to open themselves. So the plugin sets it when the items load
 * ({@code LoadedAssetsEvent<Item>}, initial load and reloads alike), on
 * the decoded assets. The map an item keeps is unmodifiable after
 * decoding, and there is no setter, so the field is written by
 * reflection: a copy of the map with the entry added, made unmodifiable
 * again, then the item's packet cache dropped so that the client gets
 * the entry with the item. Reflection, because the alternative, feeding
 * the store a modified copy of each item, would re-run the whole load of
 * an asset for one entry. If the server ever renames the field, the
 * plugin says so at startup and bags on the ground fall back to the
 * inventory, where the sweep opens them (see {@link InventorySweep}).</p>
 */
public final class PickupWiring {

    /** The root interaction of the pack run on pickup. */
    public static final String ROOT_ID = "Obol_Lootbag_Pickup";

    private static final String FIELD = "interactions";

    private final HytaleLogger logger;

    public PickupWiring(HytaleLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** The listener to register for {@code LoadedAssetsEvent} of {@link Item}. */
    public void onItemsLoaded(LoadedAssetsEvent<String, Item, ?> event) {
        int wired = 0;
        for (Rarity rarity : Rarity.values()) {
            Item item = event.getLoadedAssets().get(rarity.itemId());
            if (item != null && wire(item)) {
                wired++;
            }
        }
        if (wired > 0) {
            logger.atInfo().log("Pickup interaction set on %d lootbag item(s)", wired);
        }
    }

    /** {@return whether the entry was put on the item, or was there already} */
    private boolean wire(Item item) {
        if (ROOT_ID.equals(item.getInteractions().get(InteractionType.Pickup))) {
            return true;
        }
        Map<InteractionType, String> interactions = new EnumMap<>(InteractionType.class);
        interactions.putAll(item.getInteractions());
        interactions.put(InteractionType.Pickup, ROOT_ID);
        try {
            Field field = Item.class.getDeclaredField(FIELD);
            field.setAccessible(true);
            field.set(item, Collections.unmodifiableMap(interactions));
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.atSevere().withCause(e).log("Cannot set the Pickup interaction on %s: "
                    + "bags on the ground will go through the inventory instead", item.getId());
            return false;
        }
        item.invalidatePacketCache();
        return true;
    }
}
