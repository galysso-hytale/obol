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
import dev.galysso.obol.api.CoinsStyle;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.api.Obol;
import dev.galysso.obol.api.ObolUi;
import dev.galysso.obol.purse.PurseOps;
import dev.galysso.obol.purse.api.PurseItem;

import java.util.Map;
import java.util.Objects;

/**
 * The page of a purse held in hand: two panels, the player's balance and
 * the purse's content, each with its total and one row per coin tier, and
 * on each row buttons sending 1 or 10 coins of that tier across to the
 * other panel. Two more buttons move everything either way. Every amount
 * is drawn by Obol ({@link ObolUi}): the page never draws a coin itself.
 *
 * <p>The page remembers where the purse was when it opened (container and
 * slot) and reads the slot again at each click, so a purse moved meanwhile
 * is noticed, not overwritten. After each action the page is rebuilt from
 * the store, and the last message, if the action was refused, is shown
 * under the rows. The HUD feed of Obol shows the debit or credit on its
 * own.</p>
 *
 * <p>The client sends back {@link Event}: the button's action, and for the
 * per-tier buttons the tier and the count, all decided server side when
 * the buttons were bound.</p>
 */
public final class PursePage extends InteractiveCustomUIPage<PursePage.Event> {

    private static final String DOCUMENT = "ObolPurse/Purse.ui";
    private static final String MINE = "#Mine";
    private static final String PURSE = "#Purse";
    private static final int[] STEPS = {1, 10};
    /** On the balance side the buttons are on the right: the coins pack against them. */
    private static final CoinsStyle BUTTONS_ON_RIGHT = CoinsStyle.DEFAULT.withAlignment(CoinsStyle.Alignment.END);
    /** On the purse side the buttons are on the left. */
    private static final CoinsStyle BUTTONS_ON_LEFT = CoinsStyle.DEFAULT.withAlignment(CoinsStyle.Alignment.START);

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
        ObolUi.show(commands, MINE + " #Total", balance);
        ObolUi.show(commands, PURSE + " #Total", content);
        Map<Denomination, Long> yours = balance.breakdown();
        Map<Denomination, Long> purse = content.breakdown();
        for (Denomination tier : Denomination.values()) {
            String row = " #" + PurseItem.stateName(tier);
            ObolUi.show(commands, MINE + row + " #Coins", tier, yours.get(tier), BUTTONS_ON_RIGHT);
            ObolUi.show(commands, PURSE + row + " #Coins", tier, purse.get(tier), BUTTONS_ON_LEFT);
            for (int step : STEPS) {
                Coins coins = Coins.of(tier, step);
                bind(commands, events, MINE + row + " #Put" + step, Action.PUT, tier, step, held && balance.covers(coins));
                bind(commands, events, PURSE + row + " #Take" + step, Action.TAKE, tier, step, held && content.covers(coins));
            }
        }
        if (notice != null) {
            commands.set("#Notice.Text", notice);
            commands.set("#Notice.Visible", true);
        }
        commands.set("#PutAllButton.Disabled", !held || balance.equals(Coins.ZERO));
        commands.set("#TakeAllButton.Disabled", !held || content.equals(Coins.ZERO));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PutAllButton",
                EventData.of(Event.ACTION, Action.PUT_ALL.name()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TakeAllButton",
                EventData.of(Event.ACTION, Action.TAKE_ALL.name()));
    }

    private static void bind(UICommandBuilder commands, UIEventBuilder events, String selector,
                             Action action, Denomination tier, int count, boolean enabled) {
        commands.set(selector + ".Disabled", !enabled);
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of(Event.ACTION, action.name())
                        .append(Event.TIER, tier.name())
                        .append(Event.COUNT, Integer.toString(count)));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Event event) {
        Action action = Action.of(event.action);
        if (action == null) {
            return;
        }
        PurseOps.Outcome outcome = switch (action) {
            case PUT -> ops.put(playerRef.getUuid(), container, slot, inventory, event.coins());
            case TAKE -> ops.take(playerRef.getUuid(), container, slot, event.coins());
            case PUT_ALL -> ops.putAll(playerRef.getUuid(), container, slot, inventory);
            case TAKE_ALL -> ops.takeAll(playerRef.getUuid(), container, slot);
        };
        // The rows say what moved. Only a refusal needs words.
        notice = outcome.ok() ? null : outcome.message();
        rebuild();
    }

    /** The buttons: per tier and count, or everything. */
    enum Action {
        PUT, TAKE, PUT_ALL, TAKE_ALL;

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
     * What the client sends back on a click, as it was bound: the action,
     * and for {@link Action#PUT} and {@link Action#TAKE} the tier and the
     * count. A mutable bag of fields, as {@link BuilderCodec} wants.
     */
    public static final class Event {

        static final String ACTION = "Action";
        static final String TIER = "Tier";
        static final String COUNT = "Count";

        static final BuilderCodec<Event> CODEC = BuilderCodec
                .builder(Event.class, Event::new)
                .append(new KeyedCodec<>(ACTION, Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
                .append(new KeyedCodec<>(TIER, Codec.STRING), (e, v) -> e.tier = v, e -> e.tier).add()
                .append(new KeyedCodec<>(COUNT, Codec.STRING), (e, v) -> e.count = v, e -> e.count).add()
                .build();

        String action;
        String tier;
        String count;

        public Event() {
        }

        /**
         * {@return the amount the button stands for, {@link Coins#ZERO} if
         * the fields do not read as one (the operation then refuses it)}
         */
        Coins coins() {
            try {
                return Coins.of(Denomination.valueOf(tier), Long.parseLong(count));
            } catch (RuntimeException e) {
                return Coins.ZERO;
            }
        }
    }
}
