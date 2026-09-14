package dev.galysso.obol.internal;

import com.hypixel.hytale.server.core.util.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link BalancesBackend} over the server's {@link Config}: a
 * {@code balances.json} in the plugin's data directory, written atomically
 * (temp file, then move, previous copy kept as {@code .bak}) by the server
 * itself.
 *
 * <p>Only the {@code Balances} field is ours to touch: the state object the
 * config holds is mutated in place and saved whole, so any other field
 * survives a save untouched.</p>
 */
public final class ConfigBalancesBackend implements BalancesBackend {

    private final Config<BalancesState> config;

    public ConfigBalancesBackend(Config<BalancesState> config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * {@inheritDoc}
     *
     * <p>A missing file is an empty economy, not an error: the server hands
     * back the codec's default value. A file that exists but does not parse
     * surfaces as a {@link java.util.concurrent.CompletionException} from
     * the server's loader.</p>
     */
    @Override
    public Map<String, Long> load() {
        BalancesState state = config.load().join();
        return new HashMap<>(state.balances);
    }

    @Override
    public void save(Map<String, Long> balances) {
        config.get().balances = new HashMap<>(balances);
        config.save().join();
    }
}
