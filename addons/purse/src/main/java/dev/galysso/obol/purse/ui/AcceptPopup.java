package dev.galysso.obol.purse.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.purse.PurseOffers;

import java.util.Objects;

/**
 * The receiver's side of a handed purse: "X offers you", the amount as
 * coins, Accept / Decline. Either button, or closing the page any other way, settles the
 * offer ({@link PurseOffers}); the settlement sends the messages and
 * closes this page.
 */
public final class AcceptPopup extends InteractiveCustomUIPage<AcceptPopup.Event> {

    private static final String DOCUMENT = "ObolPurse/Offer.ui";

    private final PurseOffers.Offer offer;

    public AcceptPopup(PlayerRef playerRef, PurseOffers.Offer offer) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.offer = Objects.requireNonNull(offer, "offer");
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(DOCUMENT);
        commands.set("#Question.Text", offer.giver().getUsername() + " offers you");
        AmountUi.show(commands, "#Amount", offer.amount());
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AcceptButton", EventData.of(Event.ACTION, Event.ACCEPT));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DeclineButton", EventData.of(Event.ACTION, Event.DECLINE));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Event event) {
        offer.settle(Event.ACCEPT.equals(event.action) ? PurseOffers.Result.ACCEPTED : PurseOffers.Result.DECLINED);
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        super.onDismiss(ref, store);
        offer.settle(PurseOffers.Result.DECLINED);
    }

    /**
     * Closes this page if it is still the one the player has open. On the
     * player's world thread.
     */
    public void closeIfCurrent(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && player.getPageManager().getCustomPage() == this) {
            close();
        }
    }

    /** Accept or Decline. A mutable bag of fields, as {@link BuilderCodec} wants. */
    public static final class Event {

        static final String ACTION = "Action";
        static final String ACCEPT = "Accept";
        static final String DECLINE = "Decline";

        static final BuilderCodec<Event> CODEC = BuilderCodec
                .builder(Event.class, Event::new)
                .append(new KeyedCodec<>(ACTION, Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
                .build();

        String action;

        public Event() {
        }
    }
}
