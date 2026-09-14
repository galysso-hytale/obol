package dev.galysso.obol.purse;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import dev.galysso.obol.purse.command.PurseCommand;
import dev.galysso.obol.purse.interaction.PursePageSupplier;

import javax.annotation.Nonnull;
import java.nio.file.Files;

/**
 * Entry point declared as {@code Main} in {@code manifest.json}.
 *
 * <p>Obol is a manifest dependency, so it is loaded before this plugin and
 * its API is installed by the time {@link #setup()} runs. The item, its
 * model and the page documents come from this plugin's asset pack; the
 * only wiring done here is the page supplier the item's interaction names,
 * registered before the assets are read (the way the server's own item
 * pages are), and a testing command.</p>
 */
public class PursePlugin extends JavaPlugin {

    private static final String CONFIG_NAME = "purse";

    private final Config<PurseConfig> configFile;

    public PursePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // withConfig() is only allowed before setup().
        configFile = withConfig(CONFIG_NAME, PurseConfig.CODEC);
    }

    @Override
    protected void setup() {
        PurseConfig config = configFile.get();
        if (!Files.exists(getDataDirectory().resolve(CONFIG_NAME + ".json"))) {
            // Written once with its defaults, so that an admin finds it.
            configFile.save();
        }
        PurseOps ops = new PurseOps(getLogger());
        OpenCustomUIInteraction.registerCustomPageSupplier(this, PursePageSupplier.class,
                PursePageSupplier.PAGE_ID, new PursePageSupplier(ops, config));
        getCommandRegistry().registerCommand(new PurseCommand(ops));
        getLogger().atInfo().log("Purse ready (direct give %s)", config.directGive() ? "on" : "off");
    }
}
