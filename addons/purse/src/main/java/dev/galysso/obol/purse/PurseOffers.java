package dev.galysso.obol.purse;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.purse.ui.AcceptPopup;
import dev.galysso.obol.purse.ui.GivePopup;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The offers in flight: a giver has confirmed handing a purse's content
 * to a receiver, and the receiver has not answered yet.
 *
 * <p>Nobody receives coins without having said yes. An offer is opened
 * from the giver's confirmation ({@link GivePopup}), shown to the receiver
 * as {@link AcceptPopup}, and settled once, whatever settles it first:
 * Accept, Decline, Cancel, either page dismissed, the timeout, or a player
 * leaving. The settlement then runs on each player's world thread: the
 * transfer on the giver's (it is the giver's container), the messages,
 * and the closing of the other page if it is still the one open.</p>
 *
 * <p>One offer per receiver and per giver at a time, and after a decline
 * or a timeout the same giver waits the timeout again before offering to
 * the same receiver: a popup on someone else's screen is not to be
 * repeated at will.</p>
 */
public final class PurseOffers {

    /** How an offer ended. */
    public enum Result {
        /** The receiver accepted. */
        ACCEPTED,
        /** The receiver declined, or dismissed the popup. */
        DECLINED,
        /** The giver cancelled, or dismissed the popup. */
        CANCELLED,
        /** Nobody answered in time. */
        EXPIRED,
        /** The receiver already had a page open. */
        BUSY,
        /** The receiver was no longer in a world. */
        GONE
    }

    private final PurseOps ops;
    private final HytaleLogger logger;
    private final int timeoutSeconds;
    private final ConcurrentHashMap<UUID, Offer> byGiver = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Offer> byReceiver = new ConcurrentHashMap<>();
    /** Giver and receiver pair to the time (ms) before which a new offer is refused. */
    private final ConcurrentHashMap<String, Long> graceUntil = new ConcurrentHashMap<>();

    public PurseOffers(PurseOps ops, HytaleLogger logger, int timeoutSeconds) {
        this.ops = Objects.requireNonNull(ops, "ops");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Opens an offer and shows it to the receiver. On the giver's world
     * thread, from the giver's confirmation.
     *
     * @param giverPage the giver's popup, which stays open as a wait
     * @param container where the purse is, in the giver's inventory
     * @param slot      its slot in {@code container}
     * @param amount    what the purse held when the giver confirmed
     * @return the offer, or {@code null} with a message sent to the giver
     *         when it cannot be opened now
     */
    public Offer open(GivePopup giverPage, PlayerRef giver, PlayerRef receiver,
                      ItemContainer container, short slot, Coins amount) {
        long now = System.currentTimeMillis();
        graceUntil.values().removeIf(until -> until <= now);
        if (graceUntil.containsKey(pair(giver, receiver))) {
            giver.sendMessage(Message.raw("Wait before offering to " + receiver.getUsername() + " again."));
            return null;
        }
        Offer offer = new Offer(giverPage, giver, receiver, container, slot, amount);
        if (byGiver.putIfAbsent(giver.getUuid(), offer) != null) {
            giver.sendMessage(Message.raw("You already have an offer pending."));
            return null;
        }
        if (byReceiver.putIfAbsent(receiver.getUuid(), offer) != null) {
            byGiver.remove(giver.getUuid(), offer);
            giver.sendMessage(Message.raw(receiver.getUsername() + " is already considering an offer."));
            return null;
        }
        offer.start();
        return offer;
    }

    private static String pair(PlayerRef giver, PlayerRef receiver) {
        return giver.getUuid() + "/" + receiver.getUuid();
    }

    /** What runs with a player's entity, on the thread of its world. */
    private interface WorldAction {
        void run(Ref<EntityStore> ref, Store<EntityStore> store);
    }

    /**
     * Runs {@code action} with the player's entity on its world thread,
     * following the player if they change world meanwhile, or
     * {@code whenGone} if the player is no longer in any world.
     */
    private void onWorldThread(PlayerRef playerRef, WorldAction action, Runnable whenGone) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            whenGone.run();
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        try {
            world.execute(() -> {
                Ref<EntityStore> now = playerRef.getReference();
                if (now == null) {
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
            // The world refuses tasks while shutting down.
            logger.atWarning().withCause(e).log("Could not reach the world of %s for a purse offer", playerRef.getUuid());
            whenGone.run();
        }
    }

    /** One offer, settled at most once. */
    public final class Offer {

        private final GivePopup giverPage;
        private final PlayerRef giver;
        private final PlayerRef receiver;
        private final ItemContainer container;
        private final short slot;
        private final Coins amount;
        private final AtomicReference<Result> result = new AtomicReference<>();
        private volatile AcceptPopup receiverPage;
        private volatile ScheduledFuture<?> timer;

        private Offer(GivePopup giverPage, PlayerRef giver, PlayerRef receiver,
                      ItemContainer container, short slot, Coins amount) {
            this.giverPage = giverPage;
            this.giver = giver;
            this.receiver = receiver;
            this.container = container;
            this.slot = slot;
            this.amount = amount;
        }

        /** {@return the player offering} */
        public PlayerRef giver() {
            return giver;
        }

        /** {@return the player offered to} */
        public PlayerRef receiver() {
            return receiver;
        }

        /** {@return what the purse held when the offer was made} */
        public Coins amount() {
            return amount;
        }

        /** Arms the timeout and shows the popup to the receiver. */
        private void start() {
            World world = giver.getReference().getStore().getExternalData().getWorld();
            timer = world.scheduleAfter(() -> settle(Result.EXPIRED), timeoutSeconds, TimeUnit.SECONDS);
            if (result.get() != null) {
                timer.cancel(false);
            }
            onWorldThread(receiver, (ref, store) -> {
                if (result.get() != null) {
                    return;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    settle(Result.GONE);
                    return;
                }
                PageManager pages = player.getPageManager();
                if (pages.getCustomPage() != null) {
                    settle(Result.BUSY);
                    return;
                }
                AcceptPopup page = new AcceptPopup(receiver, this);
                receiverPage = page;
                pages.openCustomPage(ref, store, page);
                if (result.get() != null) {
                    // Settled between the check above and the opening: the
                    // settlement's own closing may have run before the page
                    // existed.
                    page.closeIfCurrent(ref, store);
                }
            }, () -> settle(Result.GONE));
        }

        /**
         * Ends the offer with {@code how}, unless it already ended. Safe
         * from any thread.
         */
        public void settle(Result how) {
            if (!result.compareAndSet(null, how)) {
                return;
            }
            ScheduledFuture<?> armed = timer;
            if (armed != null) {
                armed.cancel(false);
            }
            byGiver.remove(giver.getUuid(), this);
            byReceiver.remove(receiver.getUuid(), this);
            if (how == Result.DECLINED || how == Result.EXPIRED) {
                graceUntil.put(pair(giver, receiver), System.currentTimeMillis() + timeoutSeconds * 1000L);
            }
            onWorldThread(giver, (ref, store) -> giverSide(how, ref, store), () -> {
                if (how == Result.ACCEPTED) {
                    receiver.sendMessage(Message.raw(giver.getUsername() + " left before handing anything over."));
                }
            });
            onWorldThread(receiver, (ref, store) -> receiverSide(how, ref, store), () -> { });
        }

        /** On the giver's world thread: the transfer if accepted, the message, the page. */
        private void giverSide(Result how, Ref<EntityStore> ref, Store<EntityStore> store) {
            String other = receiver.getUsername();
            switch (how) {
                case ACCEPTED -> {
                    PurseOps.Outcome outcome = ops.give(receiver.getUuid(), other, container, slot);
                    giver.sendMessage(Message.raw(outcome.message()));
                    receiver.sendMessage(Message.raw(outcome.ok()
                            ? giver.getUsername() + " handed you " + outcome.amount() + "."
                            : "Nothing was handed over: the purse was not there anymore."));
                }
                case DECLINED -> giver.sendMessage(Message.raw(other + " declined."));
                case EXPIRED -> giver.sendMessage(Message.raw(other + " did not answer."));
                case BUSY -> giver.sendMessage(Message.raw(other + " is busy."));
                case GONE -> giver.sendMessage(Message.raw(other + " left."));
                case CANCELLED -> { }
            }
            giverPage.closeIfCurrent(ref, store);
        }

        /** On the receiver's world thread: the message when the offer went away, the page. */
        private void receiverSide(Result how, Ref<EntityStore> ref, Store<EntityStore> store) {
            String other = giver.getUsername();
            switch (how) {
                case CANCELLED -> receiver.sendMessage(Message.raw(other + " withdrew the offer."));
                case EXPIRED -> receiver.sendMessage(Message.raw("The offer from " + other + " expired."));
                default -> { }
            }
            AcceptPopup page = receiverPage;
            if (page != null) {
                page.closeIfCurrent(ref, store);
            }
        }
    }
}
