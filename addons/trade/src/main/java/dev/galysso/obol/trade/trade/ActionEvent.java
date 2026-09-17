package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * What the client sends back from a page of the trade, as the server
 * bound it: the action, the grid and the slot for a click on a cell, and
 * the {@code @} keys the client fills from a field at the moment of the
 * event. A mutable bag of
 * fields, as {@link BuilderCodec} wants.
 */
public final class ActionEvent {

    static final String ACTION = "Action";
    /** Filled by the client from {@code #Amount.Value} when the field changes. */
    static final String AMOUNT = "@Amount";

    static final String ACCEPT = "Accept";
    static final String DECLINE = "Decline";
    static final String CANCEL = "Cancel";
    /** The amount field changed. */
    static final String COINS = "Coins";
    /** A slot of one of my grids was clicked: pick a quantity from it. */
    static final String PICK = "Pick";
    /** A slot of one of my grids was right-clicked: the whole stack moves. */
    static final String WHOLE = "Whole";
    /** The quantity popup confirmed. */
    static final String MOVE = "Move";
    /** The quantity popup cancelled. */
    static final String UNPICK = "Unpick";
    /** Which grid, for {@link #PICK} and {@link #WHOLE}. */
    static final String GRID = "Grid";
    /** Which slot of it, as the cell was bound. */
    static final String SLOT = "Slot";
    /** Filled by the client from {@code #PickQuantity.Value} on {@link #MOVE}. */
    static final String QUANTITY = "@Quantity";

    static final BuilderCodec<ActionEvent> CODEC = BuilderCodec
            .builder(ActionEvent.class, ActionEvent::new)
            .append(new KeyedCodec<>(ACTION, Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
            .append(new KeyedCodec<>(AMOUNT, Codec.STRING), (e, v) -> e.amount = v, e -> e.amount).add()
            .append(new KeyedCodec<>(GRID, Codec.STRING), (e, v) -> e.grid = v, e -> e.grid).add()
            .append(new KeyedCodec<>(SLOT, Codec.STRING), (e, v) -> e.slot = v, e -> e.slot).add()
            .append(new KeyedCodec<>(QUANTITY, Codec.INTEGER), (e, v) -> e.quantity = v, e -> e.quantity).add()
            .build();

    String action;
    String amount;
    String grid;
    String slot;
    Integer quantity;

    public ActionEvent() {
    }

    boolean is(String expected) {
        return expected.equals(action);
    }

    /** {@return the text of the amount field, never null} */
    String amount() {
        return amount == null ? "" : amount;
    }

    /** {@return the grid clicked, never null} */
    String grid() {
        return grid == null ? "" : grid;
    }

    /** {@return the slot clicked, -1 when absent or unreadable} */
    int slot() {
        try {
            return slot == null ? -1 : Integer.parseInt(slot);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** {@return the quantity chosen, 0 when absent} */
    int quantity() {
        return quantity == null ? 0 : quantity;
    }
}
