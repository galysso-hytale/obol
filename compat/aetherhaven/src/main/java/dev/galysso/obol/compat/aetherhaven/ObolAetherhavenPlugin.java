package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
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
 */
public class ObolAetherhavenPlugin extends JavaPlugin {

    private static final String CONFIG_NAME = "config";

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
        // Until the lootbag slice: the configured coin item, absorbed on pickup later.
        provider = new ObolGoldProvider(rate, (itemId, amount) -> List.of(new ItemStack(itemId, (int) Math.min(amount, Integer.MAX_VALUE))));
        AetherhavenEconomy.register(provider);
        getLogger().atInfo().log("Aetherhaven pays in Obol coins, one gold coin = %s", rate.coin());
    }

    @Override
    protected void shutdown() {
        if (provider != null) {
            AetherhavenEconomy.unregister(provider);
            provider = null;
        }
    }
}
