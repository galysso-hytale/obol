package dev.galysso.obol;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import dev.galysso.obol.api.internal.ObolBackendHolder;
import dev.galysso.obol.command.ObolCommand;
import dev.galysso.obol.internal.BalancesPersistence;
import dev.galysso.obol.internal.BalancesState;
import dev.galysso.obol.internal.ConfigBalancesBackend;
import dev.galysso.obol.internal.ObolBackendImpl;
import dev.galysso.obol.ui.PlayerHuds;
import dev.galysso.obol.ui.ServerHuds;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Entry point declared as {@code Main} in {@code manifest.json}.
 */
public class ObolPlugin extends JavaPlugin {

    private static final long SAVE_PERIOD_SECONDS = 30;

    private final ObolBackendImpl backend;
    private final Config<BalancesState> balancesFile;
    private final BalancesPersistence persistence;
    private final PlayerHuds playerHuds;
    private ScheduledFuture<?> periodicSave;
    private volatile boolean loaded;

    public ObolPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        backend = new ObolBackendImpl(
                (listener, event, e) -> getLogger().atSevere().withCause(e)
                        .log("Listener %s failed on %s", listener.getClass().getName(), event),
                new ServerHuds(getLogger(), HytaleServer.SCHEDULED_EXECUTOR));
        // withConfig() is only allowed before setup(): the server refuses it
        // once the plugin state has moved on.
        balancesFile = withConfig("balances", BalancesState.CODEC);
        persistence = new BalancesPersistence(backend.storedBalances(), new ConfigBalancesBackend(balancesFile));
        playerHuds = new PlayerHuds(backend);
        // Published from the constructor, not setup(): dependent plugins may
        // already be calling the API by the time our own setup() runs.
        ObolBackendHolder.install(backend);
    }

    @Override
    protected void setup() {
        int count;
        try {
            count = persistence.load();
        } catch (RuntimeException e) {
            // An economy that silently restarts from zero is worse than a
            // plugin that refuses to start: unpublish the API so dependents
            // fail loudly too, and let the server log the cause.
            ObolBackendHolder.uninstall();
            throw new IllegalStateException(
                    "Cannot read balances.json; refusing to start with an empty economy", e);
        }
        this.loaded = true;
        getLogger().atInfo().log("Loaded %d balance(s)", count);

        getCommandRegistry().registerCommand(new ObolCommand(backend));

        // Keyed event (String): the global registration sees every player.
        // Fired on the world thread, so the component lookup is legal here.
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            PlayerRef player = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
            if (player != null) {
                onReady(player.getUuid());
            }
        });
        // A disconnect is the last moment to secure a player's final trade
        // before an eventual crash between two ticks.
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            UUID player = event.getPlayerRef().getUuid();
            backend.display().onDisconnect(player);
            playerHuds.onDisconnect(player);
            saveIfDirty("player disconnect");
        });
        periodicSave = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> saveIfDirty("periodic save"),
                SAVE_PERIOD_SECONDS, SAVE_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    protected void shutdown() {
        if (periodicSave != null) {
            periodicSave.cancel(false);
        }
        // Nothing loaded (setup() refused a corrupt file) means nothing to
        // write: saving here would replace that file with an empty economy.
        if (loaded) {
            try {
                // Unconditional and synchronous: the server is going away.
                persistence.save();
            } catch (RuntimeException e) {
                getLogger().atSevere().withCause(e).log("Failed to save balances.json on shutdown");
            }
        }
        ObolBackendHolder.uninstall();
    }

    /** Never throws: an event handler that throws breaks the other handlers. */
    private void onReady(UUID player) {
        try {
            playerHuds.onReady(player);
        } catch (RuntimeException e) {
            getLogger().atWarning().withCause(e).log("Could not restore the balance HUD of %s", player);
        }
    }

    /**
     * Never throws: a scheduled task that throws is silently dropped by the
     * executor, and an event handler that throws breaks the other handlers.
     */
    private void saveIfDirty(String reason) {
        try {
            persistence.saveIfDirty();
        } catch (RuntimeException e) {
            getLogger().atSevere().withCause(e).log("Failed to save balances.json (%s)", reason);
        }
    }
}
