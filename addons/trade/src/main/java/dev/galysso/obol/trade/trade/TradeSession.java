package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One trade between two players, from the request to its end.
 *
 * <p>A session lives on the thread of the world both players were in when
 * it was requested: page events, the request timer and the leave hooks all
 * reach it there ({@link TradeSessions} dispatches what comes from
 * elsewhere), so it needs no lock. It goes {@code REQUESTED} (the actor
 * waits, the target has the popup), then {@code OPEN} (both have the trade
 * page), then {@code DONE} or {@code CANCELLED}, once, whatever ends it
 * first: a page closed, the timer, a player leaving.</p>
 *
 * <p>Opening a page over another one dismisses the first, and closing a
 * page dismisses it too, so every {@code onDismiss} reaches
 * {@link #pageClosed} and the session ignores the ones that are not about
 * the page it currently expects on that side.</p>
 */
public final class TradeSession {

    /** Where the trade stands. */
    public enum State { REQUESTED, OPEN, DONE, CANCELLED }

    /** Why a trade ended before completion. */
    public enum Reason {
        /** A side closed its page: Decline, Cancel, Escape, another page. */
        CLOSED,
        /** Nobody answered the request in time. */
        EXPIRED,
        /** The target already had a page open when the request came. */
        BUSY,
        /** A side left the world, or the server. */
        GONE,
        /** The plugin is stopping. */
        SHUTDOWN
    }

    /** One player's half of the trade. */
    static final class Side {

        final PlayerRef player;
        /** The page this side is expected to have open, or null before the first one. */
        SessionPage<?> page;

        Side(PlayerRef player) {
            this.player = player;
        }

        String name() {
            return player.getUsername();
        }
    }

    private final TradeSessions sessions;
    private final World world;
    private final Store<EntityStore> store;
    /** The one who asked. */
    final Side actor;
    /** The one who was asked. */
    final Side target;
    private State state = State.REQUESTED;
    private ScheduledFuture<?> requestTimer;

    TradeSession(TradeSessions sessions, World world, Store<EntityStore> store, PlayerRef actor, PlayerRef target) {
        this.sessions = sessions;
        this.world = world;
        this.store = store;
        this.actor = new Side(actor);
        this.target = new Side(target);
    }

    /** {@return the world thread this session lives on} */
    World world() {
        return world;
    }

    /** {@return where the trade stands} */
    public State state() {
        return state;
    }

    /** {@return the two sides, actor first} */
    List<Side> sides() {
        return List.of(actor, target);
    }

    /** {@return the side of {@code uuid}, or null when the player is not in this trade} */
    Side sideOf(UUID uuid) {
        if (actor.player.getUuid().equals(uuid)) {
            return actor;
        }
        return target.player.getUuid().equals(uuid) ? target : null;
    }

    /** {@return the other side} */
    Side other(Side side) {
        return side == actor ? target : actor;
    }

    private boolean over() {
        return state == State.DONE || state == State.CANCELLED;
    }

    /**
     * Shows the wait to the actor and the request to the target, and arms
     * the timer. On the world thread, both entities in {@link #store}.
     */
    void start(Ref<EntityStore> actorRef, Ref<EntityStore> targetRef, int timeoutSeconds) {
        open(actor, actorRef, new WaitingPage(actor, this));
        if (over()) {
            return;
        }
        open(target, targetRef, new RequestPage(target, this));
        if (over()) {
            return;
        }
        requestTimer = world.scheduleAfter(
                () -> sessions.onWorld(world, () -> end(Reason.EXPIRED, null)),
                timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * The target said yes. Both sides get the trade page in place of what
     * they had, unless one of them has since opened something else, which
     * has already ended the session through {@link #pageClosed}.
     */
    void accept() {
        if (state != State.REQUESTED) {
            return;
        }
        state = State.OPEN;
        cancelTimer();
        for (Side side : sides()) {
            if (over()) {
                // The first side's opening ended it.
                return;
            }
            Ref<EntityStore> ref = side.player.getReference();
            if (ref == null || !ref.isValid() || ref.getStore() != store) {
                end(Reason.GONE, side);
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null || player.getPageManager().getCustomPage() != side.page) {
                end(Reason.CLOSED, side);
                return;
            }
            open(side, ref, new TradePage(side, this));
        }
    }

    /**
     * Replaces the page of {@code side}. The old page's {@code onDismiss}
     * runs inside {@code openCustomPage}, and is ignored because
     * {@code side.page} already names the new one.
     */
    private void open(Side side, Ref<EntityStore> ref, SessionPage<?> page) {
        side.page = page;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            end(Reason.GONE, side);
            return;
        }
        PageManager pages = player.getPageManager();
        if (page instanceof RequestPage && pages.getCustomPage() != null) {
            end(Reason.BUSY, side);
            return;
        }
        pages.openCustomPage(ref, store, page);
    }

    /** A page of this session was dismissed, by the player or by a replacement. */
    void pageClosed(SessionPage<?> page) {
        if (over()) {
            return;
        }
        for (Side side : sides()) {
            if (side.page == page) {
                end(Reason.CLOSED, side);
                return;
            }
        }
    }

    /** {@code uuid} left the world or the server. */
    void playerGone(UUID uuid) {
        Side side = sideOf(uuid);
        if (side != null) {
            end(Reason.GONE, side);
        }
    }

    /**
     * Ends the trade with {@code reason}, unless it already ended. Tells
     * the side that did not cause it, closes what is still open, forgets
     * the session. Nothing has moved yet, so there is nothing to undo.
     *
     * @param by the side that caused it, or null when nobody did
     */
    void end(Reason reason, Side by) {
        if (over()) {
            return;
        }
        State before = state;
        state = State.CANCELLED;
        cancelTimer();
        sessions.forget(this);
        if (before == State.REQUESTED && (reason == Reason.EXPIRED || (reason == Reason.CLOSED && by == target))) {
            // A popup on someone's screen is not to be repeated at will.
            sessions.grace(actor.player.getUuid(), target.player.getUuid());
        }
        for (Side side : sides()) {
            String message = messageFor(side, before, reason, by);
            if (message != null) {
                side.player.sendMessage(Message.raw(message));
            }
            if (reason == Reason.CLOSED && side == by) {
                // We are inside that page's onDismiss: nothing left to close.
                continue;
            }
            SessionPage<?> page = side.page;
            if (page != null) {
                sessions.onWorldThread(side.player, page::closeIfCurrent, () -> { });
            }
        }
    }

    /** What {@code side} is told, or null when it needs no telling. */
    private String messageFor(Side side, State before, Reason reason, Side by) {
        String other = other(side).name();
        boolean mine = side == by;
        return switch (reason) {
            case CLOSED -> {
                if (mine) {
                    yield null;
                }
                if (before == State.OPEN) {
                    yield other + " cancelled the trade.";
                }
                yield by == target ? other + " declined." : other + " withdrew the trade request.";
            }
            case EXPIRED -> side == actor ? other + " did not answer." : "The trade request from " + other + " expired.";
            case BUSY -> mine ? null : other + " is busy.";
            case GONE -> mine ? null : other + " left" + (before == State.OPEN ? ", trade cancelled." : ".");
            case SHUTDOWN -> "Trade cancelled: trading is shutting down.";
        };
    }

    private void cancelTimer() {
        ScheduledFuture<?> armed = requestTimer;
        if (armed != null) {
            armed.cancel(false);
            requestTimer = null;
        }
    }
}
