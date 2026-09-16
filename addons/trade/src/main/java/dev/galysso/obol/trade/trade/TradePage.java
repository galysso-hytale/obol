package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The trade itself, the same page on both sides: my offer, the other's
 * offer, Accept, Cancel. For now the offers are empty and only Cancel
 * works: the grids, the coins and Accept come with the next increment.
 * Cancel, or closing the page any other way, cancels the trade for both.
 */
final class TradePage extends SessionPage<ActionEvent> {

    private static final String DOCUMENT = "ObolTrade/Trade.ui";

    TradePage(TradeSession.Side side, TradeSession session) {
        super(side, session, ActionEvent.CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        String other = otherName();
        commands.append(DOCUMENT);
        commands.set("#Heading.Text", "Trade with " + other);
        commands.set("#Theirs #Caption.Text", other + "'s offer");
        commands.set("#TheirStatus.Text", "Waiting for " + other);
        // Nothing to accept yet.
        commands.set("#AcceptButton.Disabled", true);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
                EventData.of(ActionEvent.ACTION, ActionEvent.CANCEL));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, ActionEvent event) {
        // Cancelling is closing: the session hears it through onDismiss.
        close();
    }
}
