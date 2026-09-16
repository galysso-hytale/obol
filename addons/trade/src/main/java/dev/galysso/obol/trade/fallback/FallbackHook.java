package dev.galysso.obol.trade.fallback;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.trade.trade.TradeSessions;

/**
 * F on a player when Hail is not there: the four pieces Hail's plugin class
 * puts together, without the menu. {@link #prepare} runs in both modes,
 * {@link #hookPlayers} only without Hail, {@link #close} undoes the latter.
 *
 * <p>Never hooked alongside Hail: a player has one {@code Use} slot, the
 * last {@code AddPlayerToWorldEvent} handler would win it and each mod's
 * cleanup would strip the other's {@code Interactable}.</p>
 */
public final class FallbackHook implements AutoCloseable {

    private final JavaPlugin plugin;
    private final TradeSessions sessions;
    private final HytaleLogger logger;

    private FallbackHook(JavaPlugin plugin, TradeSessions sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.logger = plugin.getLogger();
    }

    /**
     * Registers the step type, in both modes. Must run from {@code setup()},
     * before the assets load: {@code Obol_Trade_Use.json} ships in this
     * add-on's pack whether Hail is there or not, and it names the step by
     * its type, so an unregistered type is a decode error at every start.
     * With Hail the step is simply never reached, no player carries the
     * root.
     */
    public static FallbackHook prepare(JavaPlugin plugin, TradeSessions sessions) {
        FallbackHook hook = new FallbackHook(plugin, sessions);
        plugin.getCodecRegistry(Interaction.CODEC).register(TradeUseInteraction.TYPE, TradeUseInteraction.class,
                TradeUseInteraction.codec(hook::onPlayerUse));
        return hook;
    }

    /** Registers the two world events that put the root on every player. Without Hail only. */
    public FallbackHook hookPlayers() {
        // Keyed event (String): the global registration sees every player.
        // The holder is the player's entity before it joins the world, so
        // the components are in place when the tracker shows it to others.
        plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onAddToWorld);
        // Fired from the entity-removed system, ahead of the one that saves
        // the player: what gets written to disk no longer names our root.
        plugin.getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, this::onRemovedFromWorld);
        return this;
    }

    /**
     * On the actor's world thread, from their interaction chain. Does
     * nothing when a custom page is already open, as the vanilla
     * {@code OpenCustomUI} step does.
     */
    private void onPlayerUse(PlayerRef actor, PlayerRef target, CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> ref = actor.getReference();
        Player player = ref == null ? null : buffer.getComponent(ref, Player.getComponentType());
        if (player == null || player.getPageManager().getCustomPage() != null) {
            return;
        }
        sessions.request(actor, target);
    }

    /** Never throws: an event handler that throws breaks the other handlers. */
    private void onAddToWorld(AddPlayerToWorldEvent event) {
        try {
            PlayerInteractable.apply(event.getHolder());
        } catch (RuntimeException e) {
            logger.atSevere().withCause(e).log("Could not make a player interactable in %s", event.getWorld().getName());
        }
    }

    private void onRemovedFromWorld(RemovedPlayerFromWorldEvent event) {
        try {
            PlayerInteractable.remove(event.getHolder());
        } catch (RuntimeException e) {
            logger.atSevere().withCause(e).log("Could not clean up a player leaving %s", event.getWorld().getName());
        }
    }

    /**
     * Strips the components from every connected player, so that F keeps
     * working for them once the add-on is gone and their next save is
     * clean. The event handlers are unregistered by the server itself.
     */
    @Override
    public void close() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        for (PlayerRef player : universe.getPlayers()) {
            World world = universe.getWorld(player.getWorldUuid());
            if (world == null) {
                continue;
            }
            // Components belong to the world thread; the world keeps ticking
            // while plugins shut down, ahead of the players' final save.
            world.execute(() -> {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                try {
                    PlayerInteractable.remove(ref, ref.getStore());
                } catch (RuntimeException e) {
                    logger.atSevere().withCause(e).log("Could not clean up %s on shutdown", player.getUsername());
                }
            });
        }
    }
}
