package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * What the asker sees while the other decides: "Waiting for X...", Cancel.
 * Keeping the asker in a page means they cannot ask again or open
 * something else meanwhile. Cancel, or closing the page any other way,
 * withdraws the request.
 */
final class WaitingPage extends SessionPage<ActionEvent> {

    private static final String DOCUMENT = "ObolTrade/Waiting.ui";

    WaitingPage(TradeSession.Side side, TradeSession session) {
        super(side, session, ActionEvent.CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(DOCUMENT);
        commands.set("#Question.Text", "Waiting for " + otherName() + " to answer...");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
                EventData.of(ActionEvent.ACTION, ActionEvent.CANCEL));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, ActionEvent event) {
        // Cancelling is closing: the session hears it through onDismiss.
        close();
    }
}
