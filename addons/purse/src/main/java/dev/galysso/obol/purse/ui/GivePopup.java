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
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.purse.PurseOffers;

import java.util.Objects;

/**
 * The giver's side of handing a purse over. First "Hand over to X?" with
 * the amount as coins, Yes / No, then, once Yes is clicked and the offer is open, a wait
 * ("Waiting for X to answer...") with only Cancel, until the receiver
 * answers or the offer times out ({@link PurseOffers}).
 *
 * <p>The confirmation is not negotiable: without it, the right-click meant
 * to open one's own purse would send its content to whoever walks into
 * the crosshair. The wait keeps the giver in this page while the offer is
 * open, so the purse cannot be moved meanwhile, and closing the page
 * (Cancel, Escape, any other page) withdraws the offer.</p>
 */
public final class GivePopup extends InteractiveCustomUIPage<GivePopup.Event> {

    private static final String DOCUMENT = "ObolPurse/Give.ui";

    private final PurseOffers offers;
    private final ItemContainer container;
    private final short slot;
    private final PlayerRef receiver;
    private final Coins amount;
    private PurseOffers.Offer offer;

    /**
     * @param container where the purse is
     * @param slot      its slot in {@code container}
     * @param receiver  the player in the crosshair
     * @param amount    what the purse holds now, for the question
     */
    public GivePopup(PlayerRef playerRef, PurseOffers offers, ItemContainer container, short slot,
                     PlayerRef receiver, Coins amount) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.offers = Objects.requireNonNull(offers, "offers");
        this.container = Objects.requireNonNull(container, "container");
        this.slot = slot;
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.amount = Objects.requireNonNull(amount, "amount");
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(DOCUMENT);
        commands.set("#Question.Text", "Hand over to " + receiver.getUsername() + "?");
        AmountUi.show(commands, "#Amount", amount);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#YesButton", EventData.of(Event.ACTION, Event.YES));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NoButton", EventData.of(Event.ACTION, Event.NO));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Event event) {
        if (!Event.YES.equals(event.action)) {
            // No, or Cancel: closing settles the offer through onDismiss.
            close();
            return;
        }
        if (offer != null) {
            return;
        }
        offer = offers.open(this, playerRef, receiver, container, slot, amount);
        if (offer == null) {
            close();
            return;
        }
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#Question.Text", "Waiting for " + receiver.getUsername() + " to answer...");
        commands.set("#YesButton.Visible", false);
        commands.set("#NoButton.Text", "Cancel");
        sendUpdate(commands);
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        super.onDismiss(ref, store);
        if (offer != null) {
            offer.settle(PurseOffers.Result.CANCELLED);
        }
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

    /** Yes or No. A mutable bag of fields, as {@link BuilderCodec} wants. */
    public static final class Event {

        static final String ACTION = "Action";
        static final String YES = "Yes";
        static final String NO = "No";

        static final BuilderCodec<Event> CODEC = BuilderCodec
                .builder(Event.class, Event::new)
                .append(new KeyedCodec<>(ACTION, Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
                .build();

        String action;

        public Event() {
        }
    }
}
