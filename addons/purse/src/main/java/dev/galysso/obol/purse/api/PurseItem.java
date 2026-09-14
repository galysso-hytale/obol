package dev.galysso.obol.purse.api;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.api.WalletId;
import org.bson.BsonString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a purse is, as an item: how to recognise one, how to read the wallet
 * it opens, how to write a stack that opens a wallet, and how to write an
 * empty one.
 *
 * <p>A purse is a <em>key</em>, not the money. The coins live in Obol's
 * store under a {@link WalletId} of kind {@link #WALLET_KIND}, and the stack
 * only carries that id's key in its metadata ({@link #METADATA_KEY}). A
 * cloned stack is a second key to the same wallet, never a second wallet.
 * The stack also carries a copy of the balance in two places the client
 * reads: its display metadata (the description of the tooltip) and its
 * item state ({@code Copper}, {@code Silver}, {@code Gold} or
 * {@code Mythril}, the largest tier it holds, which picks the icon, the
 * model, the quality, so the frames of the slot and the tooltip, and the
 * halo on the ground). Both are rewritten after each operation by this
 * add-on, the only writer of purse wallets.</p>
 *
 * <p>An empty purse is the plain item, {@link #ITEM_ID}, with no metadata
 * at all: {@link #emptied} returns the stack the crafting bench makes, so
 * that every empty purse stacks with every other. Its wallet, if it had
 * one, is expected to have been {@link dev.galysso.obol.api.Wallet#clear()}ed
 * by the caller.</p>
 *
 * <p>Public so that a later add-on (a consumable loot purse) can read and
 * write purses the same way.</p>
 */
public final class PurseItem {

    /**
     * The item id of an empty purse, as {@code Server/Item/Items/Obol/Obol_Purse.json}
     * declares it. A purse holding coins is one of its states, whose ids the
     * server derives from this one.
     */
    public static final String ITEM_ID = "Obol_Purse";

    /** The {@link WalletId#kind()} of purse wallets in Obol's store. */
    public static final String WALLET_KIND = "purse";

    /** Metadata key holding the {@link WalletId#key()} of the purse's wallet. */
    public static final String METADATA_KEY = "obol.purse";

    private static final String NAME = "Purse";
    private static final String HINT = "Right-click to open it, right-click a player to hand it over.";

    private PurseItem() {
    }

    /**
     * {@return whether the stack is a purse, empty or of any content}
     */
    public static boolean isPurse(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String id = stack.getItemId();
        return ITEM_ID.equals(id) || state(id) != null;
    }

    /**
     * {@return the wallet the stack opens, or empty for an empty purse or
     * a stack that is not a purse}
     *
     * <p>A key that does not parse as a {@link WalletId} (edited data) is
     * treated as absent: the purse reads as empty and is rewritten as
     * such at its next use.</p>
     */
    public static Optional<WalletId> walletId(ItemStack stack) {
        if (!isPurse(stack)) {
            return Optional.empty();
        }
        try {
            String key = stack.getFromMetadataOrNull(METADATA_KEY, Codec.STRING);
            return key == null ? Optional.empty() : Optional.of(new WalletId(WALLET_KIND, key));
        } catch (RuntimeException e) {
            // Not a string, or not a valid key.
            return Optional.empty();
        }
    }

    /**
     * {@return a fresh wallet id for a purse being filled for the first time}
     */
    public static WalletId newWalletId() {
        return new WalletId(WALLET_KIND, UUID.randomUUID().toString());
    }

    /**
     * {@return {@code stack} opening wallet {@code id}, in the state of
     * {@code balance} and with a tooltip saying it}
     *
     * <p>{@code balance} is a copy of the wallet's balance at the time of
     * writing: pass the balance read after the transfer. Zero gives the
     * empty purse's look with a key inside, which only happens between
     * keying a purse and filling it.</p>
     */
    public static ItemStack withWallet(ItemStack stack, WalletId id, Coins balance) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(balance, "balance");
        if (!WALLET_KIND.equals(id.kind())) {
            throw new IllegalArgumentException("Not a purse wallet: " + id);
        }
        // The name stays "Purse": the description says the amount, the
        // quality label under the name says the tier.
        ItemDisplayMetadata display = new ItemDisplayMetadata(
                Message.raw(NAME),
                Message.join(Message.raw("Holds "), amount(balance), Message.raw(". " + HINT)));
        return new ItemStack(itemId(largestTier(balance)), stack.getQuantity())
                .withMetadata(METADATA_KEY, new BsonString(id.key()))
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC, display);
    }

    /**
     * {@return an empty purse of the same quantity as {@code stack}: the
     * plain item, no metadata at all}
     *
     * <p>No metadata rather than empty metadata: {@code isStackableWith}
     * compares the two documents, and a crafted stack has none.</p>
     */
    public static ItemStack emptied(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return new ItemStack(ITEM_ID, stack.getQuantity());
    }

    /**
     * {@return the state name of a tier, as {@code Obol_Purse.json} spells
     * its states: {@code Gold}}
     */
    public static String stateName(Denomination tier) {
        String name = tier.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * {@return the largest tier {@code coins} has at least one coin of, or
     * empty for zero}
     */
    private static Optional<Denomination> largestTier(Coins coins) {
        Denomination[] all = Denomination.values();
        Map<Denomination, Long> parts = coins.breakdown();
        for (int i = all.length - 1; i >= 0; i--) {
            if (parts.get(all[i]) != 0) {
                return Optional.of(all[i]);
            }
        }
        return Optional.empty();
    }

    /**
     * The item id of the purse in the state of {@code tier}: the plain item
     * for an empty purse. If the assets do not declare the state, the plain
     * item, so that a purse always works even when it does not show.
     */
    private static String itemId(Optional<Denomination> tier) {
        if (tier.isEmpty()) {
            return ITEM_ID;
        }
        Item base = Item.getAssetMap().getAsset(ITEM_ID);
        String id = base == null ? null : base.getItemIdForState(stateName(tier.get()));
        return id == null ? ITEM_ID : id;
    }

    /** {@return the state name of item {@code id}, or {@code null} if it is not a state of the purse} */
    private static String state(String id) {
        Item base = Item.getAssetMap().getAsset(ITEM_ID);
        return base == null ? null : base.getStateForItem(id);
    }

    /**
     * The amount in words, each tier in its coin's colour, largest first,
     * zero tiers left out: {@code 2 gold, 35 silver, 4 copper}.
     */
    private static Message amount(Coins coins) {
        Map<Denomination, Long> parts = coins.breakdown();
        Denomination[] all = Denomination.values();
        List<Message> words = new ArrayList<>();
        for (int i = all.length - 1; i >= 0; i--) {
            long count = parts.get(all[i]);
            if (count != 0) {
                if (!words.isEmpty()) {
                    words.add(Message.raw(", "));
                }
                words.add(Message.raw(count + " " + all[i].name().toLowerCase(Locale.ROOT)).color(all[i].color()));
            }
        }
        if (words.isEmpty()) {
            return Message.raw("nothing");
        }
        return Message.join(words.toArray(Message[]::new));
    }
}
