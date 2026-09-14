package dev.galysso.obol.purse.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.purse.PurseOps;

import java.util.Objects;

/**
 * "Give 2g 35s to X? Yes / No": the confirmation asked when a purse is
 * right-clicked on a player.
 *
 * <p>Not negotiable: without it, the right-click meant to open one's own
 * purse would send its content to whoever walks into the crosshair. Yes
 * drains the purse into the receiver's balance (the purse stays in the
 * giver's hand, empty) and tells both players. The amount shown is the
 * one read when the popup opened; what is handed over is what the purse
 * holds when Yes is clicked, which is the same unless a second key to the
 * wallet was used meanwhile, and the message says what actually moved.</p>
 */
public final class GivePopup extends InteractiveCustomUIPage<GivePopup.Event> {

    private static final String DOCUMENT = "ObolPurse/Give.ui";

    private final PurseOps ops;
    private final ItemContainer container;
    private final short slot;
    private final PlayerRef receiver;
    private final Coins amount;

    /**
     * @param container where the purse is
     * @param slot      its slot in {@code container}
     * @param receiver  the player in the crosshair
     * @param amount    what the purse holds now, for the question
     */
    public GivePopup(PlayerRef playerRef, PurseOps ops, ItemContainer container, short slot,
                     PlayerRef receiver, Coins amount) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Event.CODEC);
        this.ops = Objects.requireNonNull(ops, "ops");
        this.container = Objects.requireNonNull(container, "container");
        this.slot = slot;
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.amount = Objects.requireNonNull(amount, "amount");
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(DOCUMENT);
        commands.set("#Question.Text", "Give " + amount + " to " + receiver.getUsername() + "?");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#YesButton", EventData.of(Event.ACTION, Event.YES));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NoButton", EventData.of(Event.ACTION, Event.NO));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Event event) {
        if (Event.YES.equals(event.action)) {
            PurseOps.Outcome outcome = ops.give(receiver.getUuid(), receiver.getUsername(), container, slot);
            playerRef.sendMessage(Message.raw(outcome.message()));
            if (outcome.ok()) {
                receiver.sendMessage(Message.raw(playerRef.getUsername() + " handed you " + outcome.amount() + "."));
            }
        }
        close();
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
