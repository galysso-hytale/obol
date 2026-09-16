package dev.galysso.obol.lootbag.api;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsParseException;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.lootbag.LootLaw;
import dev.galysso.obol.lootbag.LootbagConfig.OpenOn;
import dev.galysso.obol.lootbag.Rarity;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a lootbag is, as an item: how to recognise one, how to read its
 * rarity and its law, and how to write a stack of them.
 *
 * <p>A lootbag is one of five items, {@code Obol_Lootbag_<Rarity>}, all
 * children of the {@code Obol_Lootbag} template that is never handed out.
 * The coins are not in the stack: it carries a {@link LootLaw} under
 * {@link #METADATA_KEY}, and the amount is rolled from it when the bag is
 * opened and credited straight to the balance. Nothing is stored on Obol's
 * side for a bag, so a bag that burns in lava leaves no trace.</p>
 *
 * <p>The stack also carries the tooltip the client shows, as display
 * metadata: the name, and a description that says the range of the law in
 * words ("Holds 5 to 50 silver.") followed by how the bag opens on this
 * server. Both documents are written once, by {@link #metadata}, and
 * deterministically: two bags of the same law and mode are the same
 * stack.</p>
 *
 * <p>A bag with no law at all (an item made by hand, or a {@code Single}
 * drop that names a lootbag item without going through the
 * {@code ObolLootbag} container) is a fallback bag: it rolls the law its
 * rarity has in {@code lootbag.json} and shows the pack's fallback text.</p>
 *
 * <p>Public so that another mod can hand out lootbags (a quest reward) the
 * same way the drop tables do.</p>
 */
public final class LootbagItem {

    /** What every lootbag's item id starts with. */
    public static final String ITEM_ID_PREFIX = Rarity.ITEM_ID_PREFIX;

    /**
     * Metadata key holding the bag's law: a document of the law's entries
     * ({@link LootLaw#toEntries()}), or a single string with the amount for
     * a fixed law ({@link Coins#toString()}).
     */
    public static final String METADATA_KEY = "obol.lootbag";

    private static final String NAME = "Lootbag";
    private static final String HINT_USE_STACK =
            "Right-click to open one bag, crouch and right-click to open the whole stack.";
    private static final String HINT_USE_ALL =
            "Right-click to open it, crouch and right-click to open every lootbag you carry.";
    private static final String HINT_PICKUP = "Goes straight into your balance when you take it.";

    private LootbagItem() {
    }

    /**
     * {@return whether a crouched click on a bag of {@code law} opens every
     * bag carried rather than the held stack}
     * A fixed amount is what {@code RevealAmount} writes, and such bags
     * only stack with bags of the same amount: "the stack" would often be
     * one bag, so the crouch opens the inventory instead. A range is what
     * stacks by rarity, and the crouch opens that stack. Written in the
     * tooltip, read at the click: the rule travels with the bag.
     */
    public static boolean crouchOpensAll(LootLaw law) {
        return Objects.requireNonNull(law, "law").isFixed();
    }

    /** {@return whether the stack is a lootbag of any rarity} */
    public static boolean isLootbag(ItemStack stack) {
        return rarity(stack).isPresent();
    }

    /** {@return the rarity of the stack, or empty when it is not a lootbag} */
    public static Optional<Rarity> rarity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Rarity.ofItemId(stack.getItemId());
    }

    /**
     * {@return the law the stack carries, or empty for a fallback bag, a
     * stack that is not a lootbag, or a law that does not read (edited
     * data), which is treated as absent}
     */
    @SuppressWarnings("deprecation")
    public static Optional<LootLaw> law(ItemStack stack) {
        if (!isLootbag(stack)) {
            return Optional.empty();
        }
        BsonDocument metadata = stack.getMetadata();
        BsonValue value = metadata == null ? null : metadata.get(METADATA_KEY);
        if (value == null) {
            return Optional.empty();
        }
        try {
            if (value.isString()) {
                return Optional.of(LootLaw.fixed(Coins.parse(value.asString().getValue())));
            }
            if (value.isDocument()) {
                Map<String, String> entries = new LinkedHashMap<>();
                for (Map.Entry<String, BsonValue> entry : value.asDocument().entrySet()) {
                    entries.put(entry.getKey(), entry.getValue().isString()
                            ? entry.getValue().asString().getValue() : entry.getValue().toString());
                }
                return Optional.of(LootLaw.of(entries));
            }
            return Optional.empty();
        } catch (RuntimeException | CoinsParseException e) {
            return Optional.empty();
        }
    }

    /** {@return the item id of the bag of that rarity} */
    public static String itemId(Rarity rarity) {
        return Objects.requireNonNull(rarity, "rarity").itemId();
    }

    /**
     * {@return a stack of {@code quantity} fallback bags of that rarity,
     * with no metadata at all}
     * When opened, such a bag rolls the law its rarity has in
     * {@code lootbag.json}, and its tooltip is the pack's fallback text.
     * The drop tables never make one: see {@link #stack(Rarity, LootLaw, int, OpenOn)}.
     *
     * @param quantity 1 to the item's {@code MaxStack}
     */
    public static ItemStack stack(Rarity rarity, int quantity) {
        return new ItemStack(itemId(rarity), checkQuantity(quantity));
    }

    /**
     * {@return a stack of {@code quantity} bags of that rarity carrying
     * {@code law}, with the tooltip that says it}
     *
     * @param law      what a bag gives when opened
     * @param quantity 1 to the item's {@code MaxStack}
     * @param openOn   how bags open on this server, for the tooltip's last
     *                 sentence: the value of {@code lootbag.json}
     */
    public static ItemStack stack(Rarity rarity, LootLaw law, int quantity, OpenOn openOn) {
        return new ItemStack(itemId(rarity), checkQuantity(quantity), metadata(law, openOn));
    }

    /**
     * {@return the metadata of a bag of {@code law}: the law under
     * {@link #METADATA_KEY} and the display metadata of its tooltip}
     *
     * <p>Deterministic: same law and mode, same document, key for key and
     * byte for byte. What the {@code ObolLootbag} drop container hands to
     * its {@code ItemDrop}.</p>
     *
     * <p>The tooltip's last sentence says what a crouched click does, and
     * that follows the bag, not the server's settings, so that it stays
     * true after a change of {@code lootbag.json}: a bag with a fixed
     * amount ({@link #crouchOpensAll}) opens every bag carried, a bag with
     * a range opens its stack.</p>
     */
    public static BsonDocument metadata(LootLaw law, OpenOn openOn) {
        Objects.requireNonNull(law, "law");
        Objects.requireNonNull(openOn, "openOn");
        String hint = openOn == OpenOn.Pickup ? HINT_PICKUP : crouchOpensAll(law) ? HINT_USE_ALL : HINT_USE_STACK;
        ItemDisplayMetadata display = new ItemDisplayMetadata(Message.raw(NAME),
                Message.join(holds(law), Message.raw(". " + hint)));
        BsonDocument metadata = new BsonDocument();
        metadata.put(METADATA_KEY, lawValue(law));
        ItemDisplayMetadata.KEYED_CODEC.put(metadata, display);
        return metadata;
    }

    /** The law as the metadata carries it: a string for a fixed amount, else a document. */
    private static BsonValue lawValue(LootLaw law) {
        if (law.isFixed()) {
            return new BsonString(law.min().toString());
        }
        BsonDocument document = new BsonDocument();
        for (Map.Entry<String, String> entry : law.toEntries().entrySet()) {
            document.put(entry.getKey(), new BsonString(entry.getValue()));
        }
        return document;
    }

    /**
     * {@return what the bag holds, in words: "Holds 5 to 50 silver",
     * "Holds 1 gold, 20 silver to 5 gold", or "Holds 2 gold, 35 silver" for
     * a fixed law}
     */
    public static Message holds(LootLaw law) {
        Objects.requireNonNull(law, "law");
        if (law.isFixed()) {
            return Message.join(Message.raw("Holds "), amountWords(law.min()));
        }
        Optional<Denomination> tier = singleTier(law.min());
        if (tier.isPresent() && tier.equals(singleTier(law.max()))) {
            // Both bounds are whole coins of one tier: name it once.
            long from = law.min().breakdown().get(tier.get());
            long to = law.max().breakdown().get(tier.get());
            return Message.join(Message.raw("Holds "),
                    Message.raw(from + " to " + to + " " + tierName(tier.get())).color(tier.get().color()));
        }
        return Message.join(Message.raw("Holds "), amountWords(law.min()), Message.raw(" to "),
                amountWords(law.max()));
    }

    /**
     * {@return the amount in words, each tier in its coin's colour, largest
     * first, zero tiers left out: "2 gold, 35 silver, 4 copper"}
     */
    public static Message amountWords(Coins coins) {
        Objects.requireNonNull(coins, "coins");
        Map<Denomination, Long> parts = coins.breakdown();
        Denomination[] all = Denomination.values();
        List<Message> words = new ArrayList<>();
        for (int i = all.length - 1; i >= 0; i--) {
            long count = parts.get(all[i]);
            if (count != 0) {
                if (!words.isEmpty()) {
                    words.add(Message.raw(", "));
                }
                words.add(Message.raw(count + " " + tierName(all[i])).color(all[i].color()));
            }
        }
        if (words.isEmpty()) {
            return Message.raw("nothing");
        }
        return Message.join(words.toArray(Message[]::new));
    }

    /** {@return the one tier {@code coins} is whole coins of, if it is} */
    private static Optional<Denomination> singleTier(Coins coins) {
        Denomination found = null;
        for (Map.Entry<Denomination, Long> part : coins.breakdown().entrySet()) {
            if (part.getValue() != 0) {
                if (found != null) {
                    return Optional.empty();
                }
                found = part.getKey();
            }
        }
        return Optional.ofNullable(found);
    }

    private static String tierName(Denomination tier) {
        return tier.name().toLowerCase(Locale.ROOT);
    }

    private static int checkQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1, got " + quantity);
        }
        return quantity;
    }
}
