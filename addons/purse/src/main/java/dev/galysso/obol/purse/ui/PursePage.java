package dev.galysso.obol.purse.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsParseException;
import dev.galysso.obol.api.Obol;
import dev.galysso.obol.purse.PurseOps;
import dev.galysso.obol.purse.api.PurseItem;

import java.util.Objects;

/**
 * The page of a purse held in hand: the player's balance, the purse's
 * content, an amount field and three buttons (put that amount in, put the
 * whole balance in, take everything out).
 *
 * <p>The page remembers where the purse was when it opened (container and
 * slot) and reads the slot again at each click, so a purse moved meanwhile
 * is noticed, not overwritten. After each action the page is rebuilt from
 * the store, and the last message is shown under the two amounts. The HUD
 * feed of Obol shows the debit or credit on its own.</p>
 *
 * <p>The client sends back {@link Event}: the button's action, and for
 * "Put in" the text of the amount field, taken at click time
 * ({@code @Amount} bound to {@code #Amount.Value}).</p>
 */
public final class PursePage extends InteractiveCustomUIPage<PursePage.Event> {

    private static final String DOCUMENT = "ObolPurse/Purse.ui";

    private final PurseOps ops;
    private final ItemContainer container;
    private final short slot;
    private final ItemContainer inventory;
    private String notice;

    /**
     * @param container where the purse is
     * @param slot      its slot in {@code container}
     * @param inventory where the rest of a stack of purses goes when one is filled
     */
    public PursePage(PlayerRef playerRef, PurseOps ops, ItemContainer container, short slot, ItemContainer inventory) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Event.CODEC);
        this.ops = Objects.requireNonNull(ops, "ops");
        this.container = Objects.requireNonNull(container, "container");
        this.slot = slot;
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(DOCUMENT);
        Coins balance = Obol.playerWallet(playerRef.getUuid()).balance();
        ItemStack stack = container.getItemStack(slot);
        boolean held = PurseItem.isPurse(stack);
        Coins content = held ? ops.content(stack) : Coins.ZERO;
        CoinsUi.coins(commands, "#Balance", balance);
        CoinsUi.coins(commands, "#Purse", content);
        if (notice != null) {
            commands.set("#Notice.Text", notice);
            commands.set("#Notice.Visible", true);
        }
        boolean canPut = held && !balance.equals(Coins.ZERO);
        commands.set("#PutButton.Disabled", !canPut);
        commands.set("#PutAllButton.Disabled", !canPut);
        commands.set("#TakeAllButton.Disabled", !held || content.equals(Coins.ZERO));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PutButton",
                EventData.of(Event.ACTION, Action.PUT.name()).append(Event.AMOUNT, "#Amount.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PutAllButton",
                EventData.of(Event.ACTION, Action.PUT_ALL.name()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TakeAllButton",
                EventData.of(Event.ACTION, Action.TAKE_ALL.name()));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Event event) {
        Action action = Action.of(event.action);
        if (action == null) {
            return;
        }
        PurseOps.Outcome outcome = switch (action) {
            case PUT -> put(event.amount);
            case PUT_ALL -> ops.putAll(playerRef.getUuid(), container, slot, inventory);
            case TAKE_ALL -> ops.takeAll(playerRef.getUuid(), container, slot);
        };
        notice = outcome.message();
        rebuild();
    }

    private PurseOps.Outcome put(String text) {
        Coins amount;
        try {
            amount = Coins.parse(text == null ? "" : text.trim());
        } catch (CoinsParseException e) {
            return PurseOps.Outcome.refused(e.getMessage() + ". Example: 2g 35s");
        }
        return ops.put(playerRef.getUuid(), container, slot, inventory, amount);
    }

    /** The three buttons. */
    enum Action {
        PUT, PUT_ALL, TAKE_ALL;

        static Action of(String name) {
            for (Action action : values()) {
                if (action.name().equals(name)) {
                    return action;
                }
            }
            return null;
        }
    }

    /**
     * What the client sends back on a click. A mutable bag of fields, as
     * {@link BuilderCodec} wants. {@code amount} is only present for
     * {@link Action#PUT}.
     */
    public static final class Event {

        static final String ACTION = "Action";
        /** Leading {@code @}: the client fills it from the selector bound to it. */
        static final String AMOUNT = "@Amount";

        static final BuilderCodec<Event> CODEC = BuilderCodec
                .builder(Event.class, Event::new)
                .append(new KeyedCodec<>(ACTION, Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
                .append(new KeyedCodec<>(AMOUNT, Codec.STRING), (e, v) -> e.amount = v, e -> e.amount).add()
                .build();

        String action;
        String amount;

        public Event() {
        }
    }
}
