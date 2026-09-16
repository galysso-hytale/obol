package dev.galysso.obol.lootbag;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ItemDropContainer;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import dev.galysso.obol.lootbag.command.LootbagCommand;
import dev.galysso.obol.lootbag.drops.LootbagDropContainer;
import dev.galysso.obol.lootbag.interaction.OpenLootbagInteraction;
import dev.galysso.obol.lootbag.pickup.ChestMoveHandler;
import dev.galysso.obol.lootbag.pickup.InteractivePickupListener;
import dev.galysso.obol.lootbag.pickup.InventorySweep;
import dev.galysso.obol.lootbag.pickup.PickupWiring;

import javax.annotation.Nonnull;
import java.nio.file.Files;

/**
 * Entry point declared as {@code Main} in {@code manifest.json}.
 *
 * <p>Obol is a manifest dependency, so it is loaded before this plugin and
 * its API is installed by the time {@link #setup()} runs. The items, their
 * models and the drop tables come from this plugin's asset pack. What is
 * wired here, before the assets are read: the interaction that opens a bag,
 * the drop container that makes one, the listener that applies the drop
 * rules of {@code lootbag.json}, and in {@code Pickup} mode the hooks that
 * credit a bag the moment a player gets hold of it.</p>
 */
public class LootbagPlugin extends JavaPlugin {

    private static final String CONFIG_NAME = "lootbag";

    private final Config<LootbagConfig> configFile;

    public LootbagPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // withConfig() is only allowed before setup().
        configFile = withConfig(CONFIG_NAME, LootbagConfig.CODEC);
    }

    @Override
    protected void setup() {
        LootbagConfig config = configFile.get();
        if (!Files.exists(getDataDirectory().resolve(CONFIG_NAME + ".json"))) {
            // Written once with its defaults, so that an admin finds it.
            configFile.save();
        }
        config.resolve((rarity, problem) ->
                getLogger().atWarning().log("lootbag.json, Rarities.%s: %s", rarity, problem));
        LootbagOps ops = new LootbagOps(config, getLogger());
        // Before the assets are read: the items and tables that name them
        // are decoded with the rest of the pack.
        getCodecRegistry(Interaction.CODEC)
                .register(OpenLootbagInteraction.ID, OpenLootbagInteraction.class, OpenLootbagInteraction.codec(ops));
        getCodecRegistry(ItemDropContainer.CODEC)
                .register(LootbagDropContainer.TYPE, LootbagDropContainer.class, LootbagDropContainer.codec(config));
        if (config.openOn() == LootbagConfig.OpenOn.Pickup) {
            wirePickup(ops);
        }
        getCommandRegistry().registerCommand(new LootbagCommand(config));
        getLogger().atInfo().log("Lootbag ready (open on %s, amounts %s)",
                config.openOn().name().toLowerCase(),
                config.revealAmount() ? "revealed" : "hidden until opened");
    }

    /**
     * {@code OpenOn: Pickup}: the three ways a bag reaches a player, each
     * credited at the gesture (see the {@code pickup} package). The ground,
     * through the game's own pickup chain put on the items as they load.
     * The chest, through the move packets of each player once ready. The
     * harvest and the inventory, through two entity event systems.
     */
    private void wirePickup(LootbagOps ops) {
        PickupWiring wiring = new PickupWiring(getLogger());
        getEventRegistry().register(LoadedAssetsEvent.class, Item.class, wiring::onItemsLoaded);
        ChestMoveHandler chest = new ChestMoveHandler(ops, getLogger());
        // Keyed event (String): the global registration sees every player.
        // Fired on the world thread once the client is ready, after the
        // game's packet handlers exist.
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            PlayerRef player = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
            if (player != null) {
                chest.install(player);
            }
        });
        getEntityStoreRegistry().registerSystem(new InteractivePickupListener(ops));
        getEntityStoreRegistry().registerSystem(new InventorySweep(ops));
    }
}
