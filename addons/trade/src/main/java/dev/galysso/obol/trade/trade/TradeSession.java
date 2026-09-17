package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsParseException;
import dev.galysso.obol.api.Obol;
import dev.galysso.obol.trade.TradeConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One trade between two players, from the request to its end.
 *
 * <p>A session lives on the thread of the world both players were in when
 * it was requested: page events, the timers and the leave hooks all
 * reach it there ({@link TradeSessions} dispatches what comes
 * from elsewhere), so it needs no lock. It goes {@code REQUESTED} (the
 * actor waits, the target has the popup), then {@code OPEN}, then
 * {@code DONE} or {@code CANCELLED}, once, whatever ends it first: a page
 * closed, a timer, a player leaving or walking away.</p>
 *
 * <p>While open, each side has the trade page ({@link TradePage}) and
 * an escrow, {@link Side#offer}, that the page moves stacks into and out
 * of on the player's clicks ({@link #pick} then {@link #move}, or
 * {@link #moveWhole} straight away). Every change of either offer takes both acceptances back and locks
 * Accept for {@code AcceptDelaySeconds}, so that nobody swaps a stack
 * under the other's click. Whatever ends the trade gives each escrow back
 * to its owner.</p>
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
        /** The two walked out of reach of each other. */
        APART,
        /** The plugin is stopping. */
        SHUTDOWN
    }

    /** One player's half of the trade. */
    static final class Side {

        final PlayerRef player;
        /** The page this side is expected to have open, or null before the first one. */
        SessionPage<?> page;
        /** The stacks offered, in escrow. Null until the trade opens. */
        SimpleItemContainer offer;
        EventRegistration<?, ?> offerHook;
        /** Change hooks on the inventory sections the page shows, to redraw them. */
        final List<EventRegistration<?, ?>> inventoryHooks = new ArrayList<>();
        /** The coins offered. */
        Coins coins = Coins.ZERO;
        /** What the player last typed in the amount field, shown back on rebuild. */
        String amountText = "";
        /** A word about the last refused action, shown until the next action. */
        String notice;
        /** The stack the quantity popup is open on, null when it is closed. */
        Pick pick;
        /** Where each offer slot's stack was taken from, by offer slot, to put it back there. */
        final Map<Short, Origin> origins = new HashMap<>();
        boolean accepted;

        Side(PlayerRef player) {
            this.player = player;
        }

        String name() {
            return player.getUsername();
        }

        boolean offersNothing() {
            return (offer == null || offer.isEmpty()) && coins.equals(Coins.ZERO);
        }
    }

    /**
     * A stack double-clicked, waiting for a quantity: the grid it sits in,
     * its slot there, and the stack as it was, to notice a change before
     * moving.
     */
    /** A slot of one of the inventory grids of a page ({@code TradePage.STORAGE} and the like). */
    record Origin(String grid, short slot) {
    }

    record Pick(String grid, short slot, ItemStack stack) {
    }

    /** Stacks each side may offer: three rows of nine on the page. */
    static final short OFFER_SLOTS = 27;

    private final TradeSessions sessions;
    private final TradeConfig config;
    private final World world;
    private final Store<EntityStore> store;
    /** The one who asked. */
    final Side actor;
    /** The one who was asked. */
    final Side target;
    private State state = State.REQUESTED;
    private ScheduledFuture<?> requestTimer;
    /** When Accept unlocks again (ms), after the last change of an offer. */
    private long acceptableAt;
    private ScheduledFuture<?> unlockTimer;

    TradeSession(TradeSessions sessions, TradeConfig config, World world, Store<EntityStore> store,
                 PlayerRef actor, PlayerRef target) {
        this.sessions = sessions;
        this.config = config;
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

    // ---- The request --------------------------------------------------

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
    void acceptRequest() {
        if (state != State.REQUESTED) {
            return;
        }
        state = State.OPEN;
        cancelTimers();
        // Both escrows first: each page draws the other's offer too.
        for (Side side : sides()) {
            escrow(side);
        }
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
            openTrade(side, ref, player.getPageManager());
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

    // ---- The trade page -------------------------------------------------

    /** TEMPORARY: traces what reaches the server while the grids are being tuned. */
    void debug(String format, Object... args) {
        sessions.debug(format, args);
    }

    /** Gives {@code side} its escrow, watched for changes. */
    private void escrow(Side side) {
        side.offer = new SimpleItemContainer(OFFER_SLOTS);
        side.offerHook = side.offer.registerChangeEvent(event -> {
            // Fired by the player's moves, on this world thread, and by our
            // own emptying at the end, which must not count.
            debug("offer of %s changed: %s", side.name(), event.transaction());
            if (state == State.OPEN) {
                changed();
            }
        });
    }

    /**
     * Opens the trade page. It replaces whatever page the side had, and
     * that page's {@code onDismiss} is ignored because {@code side.page}
     * already names the new one.
     */
    private void openTrade(Side side, Ref<EntityStore> ref, PageManager pages) {
        TradePage page = new TradePage(side, this);
        side.page = page;
        pages.openCustomPage(ref, store, page);
        // The page draws the inventory itself: it must hear it change,
        // and not only through the page (a pickup, a drop).
        for (int section : new int[] {InventoryComponent.STORAGE_SECTION_ID, InventoryComponent.HOTBAR_SECTION_ID,
                InventoryComponent.BACKPACK_SECTION_ID}) {
            ItemContainer container = InventoryUtils.getSectionById(ref, section, store);
            if (container != null) {
                side.inventoryHooks.add(container.registerChangeEvent(event -> {
                    debug("section %d of %s changed: %s", section, side.name(), event.transaction());
                    if (state == State.OPEN) {
                        refresh(side);
                    }
                }));
            }
        }
    }

    /**
     * Whether an event of {@code side} may act: the trade is open, the
     * player is still in this world and the two are still within reach.
     * Otherwise the trade ends here and the answer is false.
     */
    private boolean live(Side side, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (state != State.OPEN) {
            return false;
        }
        if (store != this.store || ref == null || !ref.isValid()) {
            end(Reason.GONE, side);
            return false;
        }
        Side other = other(side);
        Ref<EntityStore> otherRef = other.player.getReference();
        if (otherRef == null || !otherRef.isValid() || otherRef.getStore() != store) {
            end(Reason.GONE, other);
            return false;
        }
        if (!sessions.withinReach(ref, otherRef, store)) {
            end(Reason.APART, null);
            return false;
        }
        return true;
    }

    /**
     * The amount field of {@code side} reads {@code text}. Empty is
     * nothing. Unreadable, or more than the balance, is nothing too, with
     * a word under the page. The offer only changes when the amount does.
     */
    void coins(Side side, Ref<EntityStore> ref, Store<EntityStore> store, String text) {
        if (!live(side, ref, store)) {
            return;
        }
        side.notice = null;
        side.amountText = text;
        Coins wanted = Coins.ZERO;
        String typed = text.strip();
        if (!typed.isEmpty()) {
            try {
                wanted = Coins.parse(typed);
            } catch (CoinsParseException e) {
                side.notice = "Unreadable amount: write it like 2g 35s.";
            }
        }
        if (side.notice == null && !wanted.equals(Coins.ZERO)) {
            Coins balance = Obol.playerWallet(side.player.getUuid()).balance();
            if (!balance.covers(wanted)) {
                side.notice = "Not enough: you have " + balance + ".";
                wanted = Coins.ZERO;
            }
        }
        if (wanted.equals(side.coins)) {
            refresh(side);
            return;
        }
        side.coins = wanted;
        changed();
    }

    /**
     * {@code side} clicked {@code slot} of {@code grid}: the quantity
     * popup opens on that stack, unless it is a single item, which moves
     * at once. An empty slot closes the popup. The page is redrawn either
     * way, the client waits for that.
     */
    void pick(Side side, Ref<EntityStore> ref, Store<EntityStore> store, String grid, int slot) {
        if (!live(side, ref, store)) {
            return;
        }
        side.notice = null;
        ItemStack stack = stackAt(side, grid, slot, ref, store);
        if (!ItemStack.isEmpty(stack) && stack.getQuantity() == 1) {
            side.pick = null;
            move(side, ref, store, grid, (short) slot, 1);
            return;
        }
        side.pick = ItemStack.isEmpty(stack) ? null : new Pick(grid, (short) slot, stack);
        refresh(side);
    }

    /**
     * {@code side} right-clicked {@code slot} of {@code grid}: the whole
     * stack moves, to the offer or back from it. Any popup closes.
     */
    void moveWhole(Side side, Ref<EntityStore> ref, Store<EntityStore> store, String grid, int slot) {
        if (!live(side, ref, store)) {
            return;
        }
        side.notice = null;
        side.pick = null;
        ItemStack stack = stackAt(side, grid, slot, ref, store);
        if (ItemStack.isEmpty(stack)) {
            refresh(side);
            return;
        }
        move(side, ref, store, grid, (short) slot, stack.getQuantity());
    }

    /** The stack in {@code slot} of {@code grid}, null when there is none or the slot does not exist. */
    private ItemStack stackAt(Side side, String grid, int slot, Ref<EntityStore> ref, Store<EntityStore> store) {
        ItemContainer container = gridContainer(side, grid, ref, store);
        return container != null && slot >= 0 && slot < container.getCapacity()
                ? container.getItemStack((short) slot) : null;
    }

    /**
     * {@code side} confirmed the quantity popup: {@code quantity} of the
     * picked stack goes to the offer, or back to the inventory when the
     * stack was picked in the offer. Whatever happens the popup closes,
     * and the page is redrawn: the client waits for that after any event.
     */
    void move(Side side, Ref<EntityStore> ref, Store<EntityStore> store, int quantity) {
        if (!live(side, ref, store)) {
            return;
        }
        Pick pick = side.pick;
        side.pick = null;
        side.notice = null;
        if (pick == null) {
            refresh(side);
            return;
        }
        ItemContainer from = gridContainer(side, pick.grid(), ref, store);
        ItemStack now = from == null ? null : from.getItemStack(pick.slot());
        if (ItemStack.isEmpty(now) || !now.getItemId().equals(pick.stack().getItemId())) {
            side.notice = "That stack moved meanwhile.";
            refresh(side);
            return;
        }
        move(side, ref, store, pick.grid(), pick.slot(), Math.min(quantity, now.getQuantity()));
    }

    /**
     * Moves {@code count} (at least one) out of {@code slot} of
     * {@code grid}. Into the escrow from the inventory: onto what was
     * already taken from that very slot when it has room, else into an
     * empty offer slot, so that each offer slot comes from one inventory
     * slot, which is remembered. (The same item taken from two slots
     * makes two offer slots.) Back from the escrow: into that remembered
     * slot when it is empty or holds the same item with room, else
     * wherever the inventory has room (hotbar, storage, then backpack).
     * The change hooks redraw
     * both pages; the last refresh closes the popup even when nothing
     * moved.
     */
    private void move(Side side, Ref<EntityStore> ref, Store<EntityStore> store, String grid, short slot, int count) {
        count = Math.max(1, count);
        ItemContainer from = gridContainer(side, grid, ref, store);
        boolean moved;
        if (TradePage.MY_OFFER.equals(grid)) {
            moved = takeBack(side, ref, store, slot, count);
            if (ItemStack.isEmpty(side.offer.getItemStack(slot))) {
                side.origins.remove(slot);
            }
        } else {
            Origin origin = new Origin(grid, slot);
            short target = offerSlotFor(side, origin, from.getItemStack(slot), count);
            moved = target >= 0 && from.moveItemStackFromSlotToSlot(slot, count, side.offer, target).succeeded();
            if (moved) {
                side.origins.put(target, origin);
            }
        }
        if (!moved) {
            side.notice = TradePage.MY_OFFER.equals(grid) ? "No room in your inventory." : "No room in your offer.";
        }
        refresh(side);
    }

    /**
     * {@code count} of offer slot {@code slot} back into the inventory,
     * where it came from when that still fits, else anywhere.
     */
    private boolean takeBack(Side side, Ref<EntityStore> ref, Store<EntityStore> store, short slot, int count) {
        ItemStack stack = side.offer.getItemStack(slot);
        Origin origin = side.origins.get(slot);
        if (origin != null) {
            ItemContainer home = gridContainer(side, origin.grid(), ref, store);
            if (home != null && origin.slot() < home.getCapacity() && fits(home, origin.slot(), stack, count)
                    && side.offer.moveItemStackFromSlotToSlot(slot, count, home, origin.slot()).succeeded()) {
                return true;
            }
        }
        ItemContainer inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        return side.offer.moveItemStackFromSlot(slot, count, inventory).succeeded();
    }

    /**
     * The offer slot for {@code count} of {@code stack} taken from
     * {@code origin}: the one already holding what came from there, when
     * it has room, else the first empty one, else -1.
     */
    private static short offerSlotFor(Side side, Origin origin, ItemStack stack, int count) {
        ItemContainer offer = side.offer;
        short empty = -1;
        for (short slot = 0; slot < offer.getCapacity(); slot++) {
            if (ItemStack.isEmpty(offer.getItemStack(slot))) {
                if (empty < 0) {
                    empty = slot;
                }
            } else if (origin.equals(side.origins.get(slot)) && fits(offer, slot, stack, count)) {
                return slot;
            }
        }
        return empty;
    }

    /** Whether {@code count} of {@code stack} can join {@code slot} of {@code container} whole: empty, or the same item with room. */
    private static boolean fits(ItemContainer container, short slot, ItemStack stack, int count) {
        ItemStack in = container.getItemStack(slot);
        if (ItemStack.isEmpty(in)) {
            return true;
        }
        return in.isStackableWith(stack) && in.getQuantity() + count <= in.getItem().getMaxStack();
    }

    /** {@code side} cancelled the quantity popup. */
    void unpick(Side side, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!live(side, ref, store)) {
            return;
        }
        side.pick = null;
        refresh(side);
    }

    /** The container behind one of the grids of {@code side}'s page, null for an unknown grid. */
    private ItemContainer gridContainer(Side side, String grid, Ref<EntityStore> ref, Store<EntityStore> store) {
        return switch (grid) {
            case TradePage.STORAGE -> InventoryUtils.getSectionById(ref, InventoryComponent.STORAGE_SECTION_ID, store);
            case TradePage.HOTBAR -> InventoryUtils.getSectionById(ref, InventoryComponent.HOTBAR_SECTION_ID, store);
            case TradePage.BACKPACK -> InventoryUtils.getSectionById(ref, InventoryComponent.BACKPACK_SECTION_ID, store);
            case TradePage.MY_OFFER -> side.offer;
            default -> null;
        };
    }

    /** {@code side} clicked Accept. When both have, the trade completes. */
    void acceptOffer(Side side, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!live(side, ref, store)) {
            return;
        }
        side.notice = null;
        if (side.accepted) {
            refresh(side);
            return;
        }
        if (locked()) {
            side.notice = "The offer just changed: wait a moment.";
            refresh(side);
            return;
        }
        if (nothingToTrade()) {
            side.notice = "Nothing to trade yet.";
            refresh(side);
            return;
        }
        side.accepted = true;
        if (other(side).accepted) {
            complete();
            return;
        }
        rebuildBoth();
    }

    /** Whether Accept is still locked after the last change of an offer. */
    boolean locked() {
        return System.currentTimeMillis() < acceptableAt;
    }

    /** Whether both offers are empty. */
    boolean nothingToTrade() {
        return actor.offersNothing() && target.offersNothing();
    }

    /**
     * An offer changed: both acceptances are void, Accept locks for the
     * configured delay and both pages show the new state.
     */
    private void changed() {
        actor.accepted = false;
        target.accepted = false;
        int delay = config.acceptDelaySeconds();
        acceptableAt = System.currentTimeMillis() + delay * 1000L;
        if (unlockTimer != null) {
            unlockTimer.cancel(false);
            unlockTimer = null;
        }
        if (delay > 0) {
            unlockTimer = world.scheduleAfter(() -> sessions.onWorld(world, this::unlocked), delay, TimeUnit.SECONDS);
        }
        rebuildBoth();
    }

    /** The delay after a change has passed: Accept comes back on both pages. */
    private void unlocked() {
        unlockTimer = null;
        if (state == State.OPEN) {
            rebuildBoth();
        }
    }

    /**
     * Both said yes to the same offers. The exchange itself, checks then
     * coins then stacks, comes with the next increment: for now both
     * pages just show the two acceptances.
     */
    private void complete() {
        rebuildBoth();
    }

    /**
     * Brings the trade page of {@code side} up to date, in place: the
     * page is not rebuilt, a rebuild would cut a drag short.
     */
    private void refresh(Side side) {
        if (side.page instanceof TradePage page) {
            page.update();
        }
    }

    /**
     * Updates both pages. Both players are in this world, so both pages
     * can be written from this thread. One who is not anymore ends the
     * trade instead.
     */
    private void rebuildBoth() {
        for (Side side : sides()) {
            if (over()) {
                return;
            }
            Ref<EntityStore> ref = side.player.getReference();
            if (ref == null || !ref.isValid() || ref.getStore() != store) {
                end(Reason.GONE, side);
                return;
            }
            refresh(side);
        }
    }

    // ---- The end ------------------------------------------------------

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
     * {@code uuid} is being taken out of this world, {@code holder} is what
     * remains of their entity: their escrow goes back into it, before the
     * holder moves on or is saved. On this world thread.
     */
    void playerGone(UUID uuid, Holder<EntityStore> holder) {
        Side side = sideOf(uuid);
        if (side == null) {
            return;
        }
        giveBack(side, holder);
        end(Reason.GONE, side);
    }

    /**
     * Ends the trade with {@code reason}, unless it already ended. Tells
     * the side that did not cause it, closes what is still open, gives
     * each escrow back, forgets the session.
     *
     * @param by the side that caused it, or null when nobody did
     */
    void end(Reason reason, Side by) {
        if (over()) {
            return;
        }
        State before = state;
        state = State.CANCELLED;
        cancelTimers();
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
            boolean closing = reason == Reason.CLOSED && side == by;
            SessionPage<?> page = side.page;
            sessions.onWorldThread(side.player, (ref, store) -> {
                if (!closing && page != null) {
                    // Not inside that page's onDismiss: it is still to close.
                    page.closeIfCurrent(ref, store);
                }
                unhook(side);
                giveBack(side, ref, store);
            }, () -> {
                unhook(side);
                lost(side);
            });
        }
    }

    /** Stops listening to {@code side}'s inventory. */
    private void unhook(Side side) {
        for (EventRegistration<?, ?> hook : side.inventoryHooks) {
            hook.unregister();
        }
        side.inventoryHooks.clear();
    }

    /**
     * Everything still in {@code side}'s escrow goes back to their
     * inventory: each stack to the slot it was taken from when that
     * still fits, the rest wherever there is room, the surplus at their
     * feet.
     */
    private void giveBack(Side side, Ref<EntityStore> ref, Store<EntityStore> store) {
        for (Map.Entry<Short, Origin> entry : side.origins.entrySet()) {
            short slot = entry.getKey();
            ItemStack stack = side.offer.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            takeBack(side, ref, store, slot, stack.getQuantity());
        }
        ItemContainer inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        for (ItemStack stack : drain(side)) {
            ItemStack rest = inventory.addItemStack(stack).getRemainder();
            if (!ItemStack.isEmpty(rest)) {
                ItemUtils.dropItem(ref, rest, store);
            }
        }
    }

    /**
     * The same into an entity that has left the store: its inventory
     * containers, hotbar, storage, then backpack, and what fits nowhere is
     * lost, there is no ground to drop it on.
     */
    private void giveBack(Side side, Holder<EntityStore> holder) {
        for (ItemStack stack : drain(side)) {
            ItemStack rest = stack;
            for (ComponentType<EntityStore, ? extends InventoryComponent> type : InventoryComponent.HOTBAR_STORAGE_BACKPACK) {
                InventoryComponent component = holder.getComponent(type);
                if (component == null) {
                    continue;
                }
                rest = component.getInventory().addItemStack(rest).getRemainder();
                if (ItemStack.isEmpty(rest)) {
                    break;
                }
            }
            if (!ItemStack.isEmpty(rest)) {
                sessions.lostEscrow(side.name(), List.of(rest));
            }
        }
    }

    /** Takes everything out of {@code side}'s escrow, forgets where it came from and stops watching it. */
    private List<ItemStack> drain(Side side) {
        SimpleItemContainer offer = side.offer;
        side.origins.clear();
        if (offer == null) {
            return List.of();
        }
        if (side.offerHook != null) {
            side.offerHook.unregister();
            side.offerHook = null;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (short slot = 0; slot < offer.getCapacity(); slot++) {
            ItemStack stack = offer.getItemStack(slot);
            if (!ItemStack.isEmpty(stack) && offer.removeItemStackFromSlot(slot, stack.getQuantity()).succeeded()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    /** {@code side} has no entity to give the escrow back to. */
    private void lost(Side side) {
        List<ItemStack> stacks = drain(side);
        if (!stacks.isEmpty()) {
            sessions.lostEscrow(side.name(), stacks);
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
            case APART -> "Too far from " + other + ", trade cancelled.";
            case SHUTDOWN -> "Trade cancelled: trading is shutting down.";
        };
    }

    private void cancelTimers() {
        if (requestTimer != null) {
            requestTimer.cancel(false);
            requestTimer = null;
        }
        if (unlockTimer != null) {
            unlockTimer.cancel(false);
            unlockTimer = null;
        }
    }
}
