package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.trade.TradeConfig;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The trades in flight, and the one entry point both hooks call when a
 * player asks another for a trade: Hail's menu entry ({@code HailBridge})
 * and, without Hail, the F step on a player ({@code TradeUseInteraction}).
 *
 * <p>One trade per player at a time, in either role, and nobody disturbed
 * who already has a page open. After a decline or an expiry the same asker
 * waits the request timeout before asking the same player again. Each
 * {@link TradeSession} then lives on its world thread; what reaches it from
 * elsewhere (a disconnect, the plugin stopping) is dispatched there.</p>
 */
public final class TradeSessions {

    private final HytaleLogger logger;
    private final TradeConfig config;
    /** Both players of a session point to it. */
    private final ConcurrentHashMap<UUID, TradeSession> byPlayer = new ConcurrentHashMap<>();
    /** Asker and asked to the time (ms) before which a new request is refused. */
    private final ConcurrentHashMap<Pair, Long> graceUntil = new ConcurrentHashMap<>();

    private record Pair(UUID actor, UUID target) {
    }

    public TradeSessions(HytaleLogger logger, TradeConfig config) {
        this.logger = logger;
        this.config = config;
    }

    /**
     * Why {@code actor} may not ask {@code target} right now, or empty when
     * they may. Cheap and side-effect free: Hail calls it when it builds its
     * menu and again on the click. Distance and presence are not checked
     * here, the caller does that before {@link #request}.
     */
    public Optional<String> refusal(UUID actor, UUID target) {
        if (byPlayer.containsKey(actor)) {
            return Optional.of("You are already trading.");
        }
        PlayerRef other = Universe.get().getPlayer(target);
        String name = other != null ? other.getUsername() : "That player";
        if (byPlayer.containsKey(target)) {
            return Optional.of(name + " is already trading.");
        }
        long remaining = graceRemaining(actor, target);
        if (remaining > 0) {
            return Optional.of("Ask " + name + " again in " + remaining + " s.");
        }
        return Optional.empty();
    }

    /**
     * {@code actor} asks {@code target} for a trade. On the actor's world
     * thread, both online. The first check that fails sends its sentence
     * to the actor and nothing opens.
     */
    public void request(PlayerRef actor, PlayerRef target) {
        Ref<EntityStore> actorRef = actor.getReference();
        Ref<EntityStore> targetRef = target.getReference();
        if (actorRef == null || !actorRef.isValid()) {
            return;
        }
        Store<EntityStore> store = actorRef.getStore();
        String refusal = refusalFor(actor, target, actorRef, targetRef, store);
        if (refusal != null) {
            actor.sendMessage(Message.raw(refusal));
            return;
        }
        World world = store.getExternalData().getWorld();
        TradeSession session = new TradeSession(this, config, world, store, actor, target);
        if (byPlayer.putIfAbsent(actor.getUuid(), session) != null) {
            actor.sendMessage(Message.raw("You are already trading."));
            return;
        }
        if (byPlayer.putIfAbsent(target.getUuid(), session) != null) {
            byPlayer.remove(actor.getUuid(), session);
            actor.sendMessage(Message.raw(target.getUsername() + " is already trading."));
            return;
        }
        logger.atInfo().log("%s asks %s for a trade", actor.getUsername(), target.getUsername());
        session.start(actorRef, targetRef, config.requestTimeoutSeconds());
    }

    /** The five checks of the plan's §5, in order, or null when all pass. */
    private String refusalFor(PlayerRef actor, PlayerRef target, Ref<EntityStore> actorRef,
                              Ref<EntityStore> targetRef, Store<EntityStore> store) {
        String name = target.getUsername();
        if (targetRef == null || !targetRef.isValid() || targetRef.getStore() != store) {
            return name + " is not here anymore.";
        }
        Optional<String> busy = refusal(actor.getUuid(), target.getUuid());
        if (busy.isPresent()) {
            return busy.get();
        }
        // Same store, so the target's pages can be read from this thread.
        Player other = store.getComponent(targetRef, Player.getComponentType());
        if (other == null || other.getPageManager().getCustomPage() != null) {
            return name + " is busy.";
        }
        if (!withinReach(actorRef, targetRef, store)) {
            return name + " is too far away.";
        }
        return null;
    }

    /** Whether the two entities are within {@code MaxDistance} of each other. */
    boolean withinReach(Ref<EntityStore> a, Ref<EntityStore> b, Store<EntityStore> store) {
        TransformComponent ta = store.getComponent(a, TransformComponent.getComponentType());
        TransformComponent tb = store.getComponent(b, TransformComponent.getComponentType());
        if (ta == null || tb == null) {
            return false;
        }
        Vector3d pa = ta.getPosition();
        Vector3d pb = tb.getPosition();
        double max = config.maxDistance();
        return pa.distanceSquared(pb) <= max * max;
    }

    /** {@return the seconds before {@code actor} may ask {@code target} again, or 0} */
    private long graceRemaining(UUID actor, UUID target) {
        long now = System.currentTimeMillis();
        Long until = graceUntil.get(new Pair(actor, target));
        if (until == null) {
            return 0;
        }
        if (until <= now) {
            graceUntil.remove(new Pair(actor, target), until);
            return 0;
        }
        return Math.max(1, (until - now + 999) / 1000);
    }

    /** Refuses {@code actor} asking {@code target} again for the request timeout. */
    void grace(UUID actor, UUID target) {
        graceUntil.put(new Pair(actor, target), System.currentTimeMillis() + config.requestTimeoutSeconds() * 1000L);
    }

    /** Drops {@code session} from the index, once it has ended. */
    void forget(TradeSession session) {
        byPlayer.remove(session.actor.player.getUuid(), session);
        byPlayer.remove(session.target.player.getUuid(), session);
    }

    /**
     * {@code uuid} is disconnecting: their trade, if any, is cancelled on
     * its own thread. Safe from any thread. The disconnect event comes
     * before the entity leaves its world, and the world runs what was
     * queued first, so the escrow still finds an inventory to go back to.
     */
    public void playerLeft(UUID uuid) {
        TradeSession session = byPlayer.get(uuid);
        if (session != null) {
            onWorld(session.world(), () -> session.playerGone(uuid));
        }
    }

    /**
     * {@code uuid} is being taken out of {@code world}, {@code holder} is
     * what remains of their entity. On that world's thread: when the trade
     * lives there, its escrow goes straight back into the holder, before
     * the holder moves on or is saved.
     */
    public void playerLeft(UUID uuid, Holder<EntityStore> holder, World world) {
        TradeSession session = byPlayer.get(uuid);
        if (session == null) {
            return;
        }
        if (session.world() == world && world.isInThread()) {
            session.playerGone(uuid, holder);
        } else {
            onWorld(session.world(), () -> session.playerGone(uuid));
        }
    }

    /** Cancels every trade in flight, at shutdown. */
    public void closeAll() {
        Set<TradeSession> distinct = new HashSet<>(byPlayer.values());
        for (TradeSession session : distinct) {
            onWorld(session.world(), () -> session.end(TradeSession.Reason.SHUTDOWN, null));
        }
    }

    /**
     * Part of an escrow could not be given back: its owner has no entity
     * anymore, or no room and no ground. Logged, so that an admin can make
     * it right.
     */
    void lostEscrow(String owner, List<ItemStack> stacks) {
        StringBuilder items = new StringBuilder();
        for (ItemStack stack : stacks) {
            items.append(' ').append(stack.getQuantity()).append('x').append(stack.getItemId());
        }
        logger.atWarning().log("Escrow of %s lost, nowhere to give it back:%s", owner, items);
    }

    /** TEMPORARY: traces what reaches the server while the grids are being tuned. */
    void debug(String format, Object... args) {
        logger.atInfo().log("[trade dbg] " + String.format(format, args));
    }

    /** Runs {@code task} on {@code world}'s thread, or logs when the world refuses. */
    void onWorld(World world, Runnable task) {
        try {
            world.execute(task);
        } catch (RuntimeException e) {
            // The world refuses tasks while shutting down.
            logger.atWarning().withCause(e).log("Could not reach %s for a trade", world.getName());
        }
    }

    /** What runs with a player's entity, on the thread of its world. */
    interface WorldAction {
        void run(Ref<EntityStore> ref, Store<EntityStore> store);
    }

    /**
     * Runs {@code action} with the player's entity on its world thread,
     * right away when this is that thread, following the player if they
     * change world meanwhile, or {@code whenGone} if the player is no
     * longer in any world.
     */
    void onWorldThread(PlayerRef playerRef, WorldAction action, Runnable whenGone) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            whenGone.run();
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world.isInThread()) {
            // Queueing would put us behind whatever is already queued, such
            // as the removal of a disconnecting player's entity.
            action.run(ref, store);
            return;
        }
        try {
            world.execute(() -> {
                Ref<EntityStore> now = playerRef.getReference();
                if (now == null || !now.isValid()) {
                    whenGone.run();
                    return;
                }
                if (now.getStore() != store) {
                    // Changed world meanwhile: the entity belongs to another
                    // thread now. Follow it.
                    onWorldThread(playerRef, action, whenGone);
                    return;
                }
                action.run(now, store);
            });
        } catch (RuntimeException e) {
            logger.atWarning().withCause(e).log("Could not reach the world of %s for a trade", playerRef.getUsername());
            whenGone.run();
        }
    }
}
