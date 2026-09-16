package dev.galysso.obol.trade;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.util.Config;
import dev.galysso.obol.trade.fallback.FallbackHook;
import dev.galysso.obol.trade.hail.HailBridge;
import dev.galysso.obol.trade.trade.TradeSessions;

import javax.annotation.Nonnull;
import java.nio.file.Files;

/**
 * Entry point declared as {@code Main} in {@code manifest.json}.
 *
 * <p>Obol is a manifest dependency, so it is loaded before this plugin and
 * its API is installed by the time {@link #setup()} runs. Hail is an
 * optional one: when it is loaded, it too came first, and Trade takes a
 * line in its menu. When it is not, the add-on hooks F on a player by
 * itself. One or the other, decided once here, never both: there is a
 * single {@code Use} slot in a player's {@code Interactions}.</p>
 *
 * <p>This class names no type of Hail. Only {@link HailBridge} does, and it
 * is loaded only on the branch where Hail is known to be there.</p>
 */
public class TradePlugin extends JavaPlugin {

    private static final String CONFIG_NAME = "trade";
    private static final PluginIdentifier HAIL = new PluginIdentifier("Galysso", "hail");

    private final Config<TradeConfig> configFile;
    /** Hail's registration or the {@link FallbackHook}, whichever was installed. */
    private AutoCloseable hook;
    private boolean viaHail;

    public TradePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // withConfig() is only allowed before setup().
        configFile = withConfig(CONFIG_NAME, TradeConfig.CODEC);
    }

    @Override
    protected void setup() {
        TradeConfig config = configFile.get();
        if (!Files.exists(getDataDirectory().resolve(CONFIG_NAME + ".json"))) {
            // Written once with its defaults, so that an admin finds it.
            configFile.save();
        }
        TradeSessions sessions = new TradeSessions(getLogger(), config);
        // In both modes: the step type must exist before our pack decodes.
        FallbackHook fallback = FallbackHook.prepare(this, sessions);

        if (hailPresent()) {
            hook = HailBridge.register(sessions, getLogger());
            viaHail = hook != null;
        }
        if (viaHail) {
            getLogger().atInfo().log("Hail present, Trade sits in its menu as %s", HailBridge.ID);
        } else {
            // Hail missing, or loaded without its API: F on a player is ours.
            hook = fallback.hookPlayers();
            getLogger().atInfo().log("Hail absent, F opens the trade directly");
        }
        getLogger().atInfo().log("Trade ready (requests open %d s, max distance %.1f, %d offer slots, accept delay %d s)",
                config.requestTimeoutSeconds(), config.maxDistance(), config.offerSlots(),
                config.acceptDelaySeconds());
    }

    @Override
    protected void shutdown() {
        if (hook == null) {
            return;
        }
        try {
            hook.close();
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log(viaHail
                    ? "Could not withdraw %s from Hail's menu"
                    : "Could not remove the F hook from the players", HailBridge.ID);
        }
        hook = null;
    }

    /**
     * Whether Hail is loaded, in the version range the manifest asks for.
     * Read from the manifest rather than repeated here, so the range lives
     * in one place (gradle.properties).
     */
    private boolean hailPresent() {
        SemverRange range = getManifest().getOptionalDependencies().get(HAIL);
        if (range == null) {
            getLogger().atWarning().log("%s is not an optional dependency of this manifest, treating it as absent", HAIL);
            return false;
        }
        return PluginManager.get().hasPlugin(HAIL, range);
    }
}
