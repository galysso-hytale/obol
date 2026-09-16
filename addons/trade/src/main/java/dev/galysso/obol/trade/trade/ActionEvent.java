package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * A button's action, as the client sends it back: the value the server
 * bound to the button. A mutable bag of fields, as {@link BuilderCodec}
 * wants.
 */
public final class ActionEvent {

    static final String ACTION = "Action";
    static final String ACCEPT = "Accept";
    static final String DECLINE = "Decline";
    static final String CANCEL = "Cancel";

    static final BuilderCodec<ActionEvent> CODEC = BuilderCodec
            .builder(ActionEvent.class, ActionEvent::new)
            .append(new KeyedCodec<>(ACTION, Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
            .build();

    String action;

    public ActionEvent() {
    }

    boolean is(String expected) {
        return expected.equals(action);
    }
}
