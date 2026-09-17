package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.CoinsStyle;
import dev.galysso.obol.api.Obol;
import dev.galysso.obol.api.ObolUi;

import java.util.HashMap;
import java.util.Map;

/**
 * The trade, one page, the same on both sides: my offer, the other's
 * offer, my coins to type, my inventory, Accept, Cancel.
 *
 * <p>Two gestures, on my offer and my inventory: a click on a stack
 * opens the quantity popup ({@code #Pick}) on it, a slider and Cancel,
 * and the chosen count moves to the offer from the inventory, or back; a
 * right-click moves the whole stack at once, as does a click on a single
 * item. The moves are the server's, on the escrow and the inventory
 * directly. The grids are groups of cells ({@code Slot.ui}), one per
 * slot, each a button bound once when the page is built and filled from
 * the server, for the other's offer as for mine and my inventory. The
 * session refreshes them in place on every change ({@link #update()}),
 * sending only the cells that changed. The client waits for such an
 * update after every event it sends, so each one is answered. (An
 * {@code ItemGrid} would draw the same thing, but on a custom page it
 * reports no plain click, only a drop after picking the stack up in
 * hand.)</p>
 *
 * <p>Every amount is drawn by Obol ({@link ObolUi}): the page never draws
 * a coin itself. Accept is disabled while a change is fresh, once clicked,
 * and while there is nothing to trade. Cancel, or closing the page any
 * other way, cancels the trade for both.</p>
 */
final class TradePage extends SessionPage<ActionEvent> {

    private static final String DOCUMENT = "ObolTrade/Trade.ui";
    private static final String CELL = "ObolTrade/Slot.ui";
    /** The grids the player acts on, by their id in the document. */
    static final String STORAGE = "#Storage";
    static final String HOTBAR = "#Hotbar";
    static final String MY_OFFER = "#MyOffer";
    private static final String THEIR_OFFER = "#TheirOffer";
    private static final String[] MINE = {STORAGE, HOTBAR, MY_OFFER};
    /** The coin rows read from their label: pack left. */
    private static final CoinsStyle LEFT = CoinsStyle.DEFAULT.withAlignment(CoinsStyle.Alignment.START);

    /** What each cell last showed, by its selector, so that an update sends only what changed. */
    private final Map<String, String> shown = new HashMap<>();

    TradePage(TradeSession.Side side, TradeSession session) {
        super(side, session, ActionEvent.CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        String other = otherName();
        commands.append(DOCUMENT);
        commands.set("#Heading.Text", "Trade with " + other);
        commands.set("#Theirs #Caption.Text", other + "'s offer");
        commands.set("#Amount.Value", side.amountText);
        ObolUi.show(commands, "#Balance", Obol.playerWallet(playerRef.getUuid()).balance(), LEFT);
        cells(commands, events, ref, store);
        state(commands, ref, store);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Amount",
                EventData.of(ActionEvent.ACTION, ActionEvent.COINS).append(ActionEvent.AMOUNT, "#Amount.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AcceptButton",
                EventData.of(ActionEvent.ACTION, ActionEvent.ACCEPT));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
                EventData.of(ActionEvent.ACTION, ActionEvent.CANCEL));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PickMove",
                EventData.of(ActionEvent.ACTION, ActionEvent.MOVE).append(ActionEvent.QUANTITY, "#PickQuantity.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PickCancel",
                EventData.of(ActionEvent.ACTION, ActionEvent.UNPICK));
    }

    // TEMPORARY: what the client sends, verbatim, to learn the keys of the grid events.
    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, String json) {
        session.debug("page event from %s: %s", side.name(), json);
        super.handleDataEvent(ref, store, json);
    }

    /** Sends what changes during the trade, without rebuilding the page. On the player's world thread. */
    void update() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        UICommandBuilder commands = new UICommandBuilder();
        state(commands, ref, ref.getStore());
        sendUpdate(commands);
    }

    /**
     * Appends one cell per slot to each grid, and binds the cells of my
     * grids: a click and a right-click both send the grid and the slot.
     * Their cells are left disabled. Done once, when the page is built.
     */
    private void cells(UICommandBuilder commands, UIEventBuilder events, Ref<EntityStore> ref, Store<EntityStore> store) {
        shown.clear();
        for (String grid : MINE) {
            ItemContainer container = container(grid, ref, store);
            int capacity = container == null ? 0 : container.getCapacity();
            for (int slot = 0; slot < capacity; slot++) {
                commands.append(grid, CELL);
                String button = cell(grid, slot) + " #Cell";
                events.addEventBinding(CustomUIEventBindingType.Activating, button,
                        EventData.of(ActionEvent.ACTION, ActionEvent.PICK)
                                .append(ActionEvent.GRID, grid)
                                .append(ActionEvent.SLOT, Integer.toString(slot)));
                events.addEventBinding(CustomUIEventBindingType.RightClicking, button,
                        EventData.of(ActionEvent.ACTION, ActionEvent.WHOLE)
                                .append(ActionEvent.GRID, grid)
                                .append(ActionEvent.SLOT, Integer.toString(slot)));
            }
        }
        for (int slot = 0; slot < session.other(side).offer.getCapacity(); slot++) {
            commands.append(THEIR_OFFER, CELL);
            commands.set(cell(THEIR_OFFER, slot) + " #Cell.Disabled", true);
        }
    }

    /** The selector of the {@code slot}th cell of {@code grid}. */
    private static String cell(String grid, int slot) {
        return grid + "[" + slot + "]";
    }

    /** The container behind one of my grids. */
    private ItemContainer container(String grid, Ref<EntityStore> ref, Store<EntityStore> store) {
        return switch (grid) {
            case STORAGE -> InventoryUtils.getSectionById(ref, InventoryComponent.STORAGE_SECTION_ID, store);
            case HOTBAR -> InventoryUtils.getSectionById(ref, InventoryComponent.HOTBAR_SECTION_ID, store);
            default -> side.offer;
        };
    }

    /**
     * What moves during the trade: the four grids, the offered coins,
     * Accept, the status, the notice, the popup.
     */
    private void state(UICommandBuilder commands, Ref<EntityStore> ref, Store<EntityStore> store) {
        TradeSession.Side theirs = session.other(side);
        String other = theirs.name();
        for (String grid : MINE) {
            fill(commands, grid, container(grid, ref, store));
        }
        fill(commands, THEIR_OFFER, theirs.offer);
        commands.clear("#MyCoins");
        ObolUi.show(commands, "#MyCoins", side.coins, LEFT);
        commands.clear("#TheirCoins");
        ObolUi.show(commands, "#TheirCoins", theirs.coins, LEFT);
        boolean canAccept = !side.accepted && !session.locked() && !session.nothingToTrade();
        commands.set("#AcceptButton.Text", side.accepted ? "Accepted" : "Accept");
        commands.set("#AcceptButton.Disabled", !canAccept);
        commands.set("#TheirStatus.Text", theirs.accepted ? other + " has accepted" : "Waiting for " + other);
        commands.set("#Notice.Text", side.notice == null ? "" : side.notice);
        commands.set("#Notice.Visible", side.notice != null);
        TradeSession.Pick pick = side.pick;
        commands.set("#Pick.Visible", pick != null);
        if (pick != null) {
            boolean back = MY_OFFER.equals(pick.grid());
            int quantity = pick.stack().getQuantity();
            commands.set("#PickTitle.Text", back ? "Take back how many?" : "Offer how many?");
            commands.set("#PickIcon.ItemId", pick.stack().getItemId());
            commands.set("#PickIcon.Quantity", quantity);
            commands.set("#PickIcon.ShowQuantity", quantity > 1);
            commands.set("#PickQuantity.Max", quantity);
            commands.set("#PickQuantity.Value", 1);
            commands.set("#PickMove.Text", back ? "Take back" : "Offer");
        }
    }


    /** Sends the cells of {@code grid} that no longer show what {@code container} holds. */
    private void fill(UICommandBuilder commands, String grid, ItemContainer container) {
        int capacity = container == null ? 0 : container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            boolean empty = ItemStack.isEmpty(stack);
            String item = cell(grid, slot) + " #Item";
            String now = empty ? "" : stack.getItemId() + " x" + stack.getQuantity();
            if (now.equals(shown.put(item, now))) {
                continue;
            }
            commands.set(item + ".Visible", !empty);
            if (!empty) {
                commands.set(item + ".ItemId", stack.getItemId());
                commands.set(item + ".Quantity", stack.getQuantity());
                commands.set(item + ".ShowQuantity", stack.getQuantity() > 1);
            }
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, ActionEvent event) {
        if (event.is(ActionEvent.COINS)) {
            session.coins(side, ref, store, event.amount());
        } else if (event.is(ActionEvent.ACCEPT)) {
            session.acceptOffer(side, ref, store);
        } else if (event.is(ActionEvent.PICK)) {
            session.pick(side, ref, store, event.grid(), event.slot());
        } else if (event.is(ActionEvent.WHOLE)) {
            session.moveWhole(side, ref, store, event.grid(), event.slot());
        } else if (event.is(ActionEvent.MOVE)) {
            session.move(side, ref, store, event.quantity());
        } else if (event.is(ActionEvent.UNPICK)) {
            session.unpick(side, ref, store);
        } else {
            // Cancelling is closing: the session hears it through onDismiss.
            close();
        }
    }
}
