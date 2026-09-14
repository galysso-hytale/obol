package dev.galysso.obol.purse.api;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.WalletId;
import org.bson.BsonDocument;
import org.bson.BsonString;

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
 * The stack also carries a copy of the balance in its display metadata
 * (name and description, read by the client for the tooltip), rewritten
 * after each operation by this add-on, which is the only writer of purse
 * wallets.</p>
 *
 * <p>An empty purse carries nothing at all: {@link #emptied} returns a
 * stack with no metadata, the way the crafting bench makes one, so that
 * every empty purse stacks with every other. Its wallet, if it had one,
 * is expected to have been {@link dev.galysso.obol.api.Wallet#clear()}ed
 * by the caller.</p>
 *
 * <p>Public so that a later add-on (a consumable loot purse) can read and
 * write purses the same way.</p>
 */
public final class PurseItem {

    /** The item id of the purse, as {@code Server/Item/Items/Obol/Obol_Purse.json} declares it. */
    public static final String ITEM_ID = "Obol_Purse";

    /** The {@link WalletId#kind()} of purse wallets in Obol's store. */
    public static final String WALLET_KIND = "purse";

    /** Metadata key holding the {@link WalletId#key()} of the purse's wallet. */
    public static final String METADATA_KEY = "obol.purse";

    private static final String NAME = "Purse";

    private PurseItem() {
    }

    /**
     * {@return whether the stack is a purse (of any content)}
     */
    public static boolean isPurse(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ITEM_ID.equals(stack.getItemId());
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
     * {@return {@code stack} opening wallet {@code id}, with a tooltip
     * saying {@code balance}}
     *
     * <p>{@code balance} is what the tooltip shows, a copy of the wallet's
     * balance at the time of writing: pass the balance read after the
     * transfer.</p>
     */
    public static ItemStack withWallet(ItemStack stack, WalletId id, Coins balance) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(balance, "balance");
        if (!WALLET_KIND.equals(id.kind())) {
            throw new IllegalArgumentException("Not a purse wallet: " + id);
        }
        ItemDisplayMetadata display = new ItemDisplayMetadata(
                Message.raw(NAME + " (" + balance + ")"),
                Message.raw("Holds " + balance + ". Right-click to open it, right-click a player to hand it over."));
        return stack
                .withMetadata(METADATA_KEY, new BsonString(id.key()))
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC, display);
    }

    /**
     * {@return {@code stack} as an empty purse: same item, quantity and
     * quality, no metadata at all}
     *
     * <p>No metadata rather than empty metadata: {@code isStackableWith}
     * compares the two documents, and a crafted stack has none.</p>
     */
    public static ItemStack emptied(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return stack.withMetadata((BsonDocument) null);
    }
}
