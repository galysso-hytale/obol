package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hexvane.aetherhaven.economy.api.LootSource;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.util.List;

/**
 * Entry point declared as {@code Main} in {@code manifest.json}.
 *
 * <p>Obol and Aetherhaven are manifest dependencies, so both are set up
 * before this plugin: Obol's API is installed and Aetherhaven's registry is
 * ready to take a provider. {@link #setup()} registers Obol as the economy,
 * {@link #shutdown()} takes it back.</p>
 *
 * <p>The lootbag add-on is optional. With it, the lootbag's own tables
 * cover dungeon chests and a broken pot gives a bag of the rarity its roll
 * is worth ({@link LootbagLoot}). Without it, Aetherhaven's gold loot is
 * off: Obol has no coin item to drop.</p>
 */
public class ObolAetherhavenPlugin extends JavaPlugin {

    private static final String CONFIG_NAME = "config";
    private static final PluginIdentifier LOOTBAG = new PluginIdentifier("Galysso", "obol-lootbag");

    private final Config<CompatConfig> configFile;
    private ObolGoldProvider provider;

    public ObolAetherhavenPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // withConfig() is only allowed before setup().
        configFile = withConfig(CONFIG_NAME, CompatConfig.CODEC);
    }

    @Override
    protected void setup() {
        CompatConfig config = configFile.get();
        // Written once with its defaults, so that an admin finds it.
        if (!Files.exists(getDataDirectory().resolve(CONFIG_NAME + ".json"))) {
            configFile.save();
        }
        Rate rate = config.rate(problem -> getLogger().atWarning().log("config.json, %s", problem));
        boolean lootbag = lootbagPresent();
        provider = new ObolGoldProvider(rate, lootbag ? new LootbagLoot(rate) : ObolAetherhavenPlugin::noLoot);
        AetherhavenEconomy.register(provider);
        // Coin items the game still makes (salvage, gifts, old stock) become coins on pickup.
        getEntityStoreRegistry().registerSystem(new CoinItemAbsorbSystem(provider, rate, getLogger()));
        getLogger().atInfo().log("Aetherhaven pays in Obol coins, one gold coin = %s, %s",
                rate.coin(), lootbag ? "broken pots drop lootbags, chests are the lootbag's" : "no gold loot without the lootbag");
    }

    private static List<ItemStack> noLoot(LootSource source, long amount) {
        return List.of();
    }

    /**
     * Whether the lootbag add-on is loaded, in the version range the manifest
     * asks for. Read from the manifest rather than repeated here, so the
     * range lives in one place (gradle.properties).
     */
    private boolean lootbagPresent() {
        SemverRange range = getManifest().getOptionalDependencies().get(LOOTBAG);
        if (range == null) {
            getLogger().atWarning().log("%s is not an optional dependency of this manifest, treating it as absent", LOOTBAG);
            return false;
        }
        return PluginManager.get().hasPlugin(LOOTBAG, range);
    }

    @Override
    protected void shutdown() {
        if (provider != null) {
            AetherhavenEconomy.unregister(provider);
            provider = null;
        }
    }
}
