package dev.galysso.obol.lootbag;

import com.hypixel.hytale.server.core.asset.type.item.config.container.ItemDropContainer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import dev.galysso.obol.lootbag.command.LootbagCommand;
import dev.galysso.obol.lootbag.drops.LootbagDropContainer;

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
        // Before the assets are read: the tables that name it are decoded
        // with the rest of the pack.
        getCodecRegistry(ItemDropContainer.CODEC)
                .register(LootbagDropContainer.TYPE, LootbagDropContainer.class, LootbagDropContainer.codec(config));
        getCommandRegistry().registerCommand(new LootbagCommand(config));
        getLogger().atInfo().log("Lootbag ready (open on %s, amounts %s)",
                config.openOn().name().toLowerCase(),
                config.revealAmount() ? "revealed" : "hidden until opened");
    }
}
