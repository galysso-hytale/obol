package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * What the asked player sees: "X proposes a trade.", Accept / Decline.
 * Accept opens the trade for both. Decline, or closing the page any other
 * way, declines.
 */
final class RequestPage extends SessionPage<ActionEvent> {

    private static final String DOCUMENT = "ObolTrade/Request.ui";

    RequestPage(TradeSession.Side side, TradeSession session) {
        super(side, session, ActionEvent.CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(DOCUMENT);
        commands.set("#Question.Text", otherName() + " proposes a trade.");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AcceptButton",
                EventData.of(ActionEvent.ACTION, ActionEvent.ACCEPT));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DeclineButton",
                EventData.of(ActionEvent.ACTION, ActionEvent.DECLINE));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, ActionEvent event) {
        if (event.is(ActionEvent.ACCEPT)) {
            session.acceptRequest();
        } else {
            // Declining is closing: the session hears it through onDismiss.
            close();
        }
    }
}
