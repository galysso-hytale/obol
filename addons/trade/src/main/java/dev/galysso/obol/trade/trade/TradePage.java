package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
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
 * offer, my coins to type, my inventory and my backpack, Accept, Cancel.
 *
 * <p>Two gestures, on my offer, my inventory and my backpack: a click
 * on a stack opens the quantity popup ({@code #Pick}) on it, a slider
 * and Cancel, and the chosen count moves to the offer from the
 * inventory, or back; a right-click moves the whole stack at once, as
 * does a click on a single item. The moves are the server's, on the
 * escrow and the inventory directly. The grids are groups of cells
 * ({@code Slot.ui}), one per slot, each a button bound once when the
 * page is built and filled from the server, for the other's offer as
 * for mine, my inventory and my backpack, whose grid takes the height of
 * the backpack carried (up to 54 slots) and is hidden with its column
 * when there is none. The session refreshes the cells in place on every
 * change ({@link #update()}), sending only those that changed. The
 * client waits for such an update after every event it sends, so each
 * one is answered. (An {@code ItemGrid} would draw the same thing, but
 * on a custom page it reports no plain click, only a drop after picking
 * the stack up in hand.)</p>
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
    static final String BACKPACK = "#Backpack";
    static final String MY_OFFER = "#MyOffer";
    private static final String THEIR_OFFER = "#TheirOffer";
    private static final String[] MINE = {STORAGE, HOTBAR, BACKPACK, MY_OFFER};
    /** The column the backpack sits in, hidden when there is no backpack. */
    private static final String BACKPACK_COLUMN = "#BackpackColumn";
    /** Cells per row of a grid, and the side of a cell, as Slot.ui draws it. */
    private static final int COLUMNS = 9;
    private static final int CELL_SIZE = 46;
    /** The coin rows read from their label: pack left. */
    private static final CoinsStyle LEFT = CoinsStyle.DEFAULT.withAlignment(CoinsStyle.Alignment.START);

    /** What each cell last showed, by its selector, so that an update sends only what changed. */
    private final Map<String, String> shown = new HashMap<>();
    /** How many cells the backpack grid was built with: another capacity means a rebuild. */
    private int backpackCells;

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

    /**
     * Sends what changes during the trade, without rebuilding the page,
     * unless the backpack changed size, which changes the cells. On the
     * player's world thread.
     */
    void update() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (capacity(container(BACKPACK, ref, store)) != backpackCells) {
            rebuild();
            return;
        }
        UICommandBuilder commands = new UICommandBuilder();
        state(commands, ref, store);
        sendUpdate(commands);
    }

    /**
     * Appends one cell per slot to each grid, and binds the cells of my
     * grids: a click and a right-click both send the grid and the slot.
     * Their cells are left disabled. The backpack grid takes the height
     * of its rows, and its column goes when there is no backpack. Done
     * once, when the page is built.
     */
    private void cells(UICommandBuilder commands, UIEventBuilder events, Ref<EntityStore> ref, Store<EntityStore> store) {
        shown.clear();
        backpackCells = capacity(container(BACKPACK, ref, store));
        commands.set(BACKPACK_COLUMN + ".Visible", backpackCells > 0);
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(COLUMNS * CELL_SIZE));
        anchor.setHeight(Value.of((backpackCells + COLUMNS - 1) / COLUMNS * CELL_SIZE));
        commands.setObject(BACKPACK + ".Anchor", anchor);
        for (String grid : MINE) {
            int capacity = capacity(container(grid, ref, store));
            for (int slot = 0; slot < capacity; slot++) {
                commands.append(grid, CELL);
                if (BACKPACK.equals(grid)) {
                    commands.set(cell(grid, slot) + " #Tint.Visible", true);
                }
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

    /** The container behind one of my grids, null when the player has no such section. */
    private ItemContainer container(String grid, Ref<EntityStore> ref, Store<EntityStore> store) {
        return switch (grid) {
            case STORAGE -> InventoryUtils.getSectionById(ref, InventoryComponent.STORAGE_SECTION_ID, store);
            case HOTBAR -> InventoryUtils.getSectionById(ref, InventoryComponent.HOTBAR_SECTION_ID, store);
            case BACKPACK -> InventoryUtils.getSectionById(ref, InventoryComponent.BACKPACK_SECTION_ID, store);
            default -> side.offer;
        };
    }

    /** {@return the slots of {@code container}, 0 for none} */
    private static int capacity(ItemContainer container) {
        return container == null ? 0 : container.getCapacity();
    }

    /**
     * What moves during the trade: the five grids, the offered coins,
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


    /**
     * Sends the cells of {@code grid} that no longer show what
     * {@code container} holds. In my offer, a stack taken from the
     * backpack carries the backpack's tint and tag.
     */
    private void fill(UICommandBuilder commands, String grid, ItemContainer container) {
        int capacity = capacity(container);
        boolean offer = MY_OFFER.equals(grid);
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            boolean empty = ItemStack.isEmpty(stack);
            TradeSession.Origin origin = offer && !empty ? side.origins.get(slot) : null;
            boolean fromBackpack = origin != null && BACKPACK.equals(origin.grid());
            String cell = cell(grid, slot);
            String now = empty ? "" : stack.getItemId() + " x" + stack.getQuantity() + (fromBackpack ? " backpack" : "");
            if (now.equals(shown.put(cell, now))) {
                continue;
            }
            String item = cell + " #Item";
            String count = cell + " #Count";
            commands.set(item + ".Visible", !empty);
            commands.set(count + ".Visible", !empty && stack.getQuantity() > 1);
            if (!empty) {
                commands.set(item + ".ItemId", stack.getItemId());
                commands.set(count + ".Text", Integer.toString(stack.getQuantity()));
            }
            if (offer) {
                commands.set(cell + " #Tint.Visible", fromBackpack);
                commands.set(cell + " #Tag.Visible", fromBackpack);
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
