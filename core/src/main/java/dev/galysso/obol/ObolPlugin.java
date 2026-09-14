package dev.galysso.obol;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.galysso.obol.api.internal.ObolApiHolder;
import dev.galysso.obol.internal.ObolApiImpl;

import javax.annotation.Nonnull;

/**
 * Entry point declared as {@code Main} in {@code manifest.json}.
 */
public class ObolPlugin extends JavaPlugin {

    private final ObolApiImpl api = new ObolApiImpl();

    public ObolPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // Published from the constructor, not setup(): dependent plugins may
        // already be resolving the API by the time our own setup() runs.
        ObolApiHolder.install(api);
    }

    @Override
    protected void setup() {
    }
}
