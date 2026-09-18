package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.economy.api.EconomyProvider;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hexvane.aetherhaven.economy.api.GoldSource;
import com.hexvane.aetherhaven.economy.api.Transfer;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsParseException;
import dev.galysso.obol.api.Obol;
import dev.galysso.obol.api.ObolUi;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Obol as Aetherhaven's economy. Accounts are Obol wallets through the
 * {@link Rate} (a player's own, one per town treasury, one per shop safe),
 * amounts are written and drawn as Obol coins, and gold loot is whatever
 * {@link GoldLoot} makes of the amount: never coin items, Obol has no item.
 *
 * <p>The rate converts what Aetherhaven counts in its coins: prices, the
 * tithe, loot. What is stored is Obol coins, and a transfer a player asks
 * for (a treasury deposit, a safe emptied) moves Obol coins between two
 * wallets at Obol's precision, so "4s 32c" moves 4s 32c.</p>
 */
final class ObolGoldProvider implements EconomyProvider {

    /** What {@code amount} Aetherhaven gold from {@code source} becomes, as items to place, drop or craft, or nothing. */
    interface GoldLoot {
        List<ItemStack> items(GoldSource source, long amount);
    }

    /** Wallet kinds of the town ledgers, keyed by the town's UUID, and by town and player for a safe. */
    static final String TREASURY_KIND = "aetherhaven";
    static final String SAFE_KIND = "aetherhaven-safe";

    private final Rate rate;
    private final GoldLoot loot;

    ObolGoldProvider(Rate rate, GoldLoot loot) {
        this.rate = Objects.requireNonNull(rate, "rate");
        this.loot = Objects.requireNonNull(loot, "loot");
    }

    @Nonnull
    @Override
    public String id() {
        return "galysso:obol";
    }

    @Nullable
    @Override
    public GoldAccount account(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid == null) {
            return null;
        }
        return new ObolGoldAccount(Obol.playerWallet(uuid.getUuid()), rate);
    }

    @Nonnull
    @Override
    public GoldAccount townAccount(@Nonnull TownRecord town) {
        return new ObolGoldAccount(Obol.wallet(new WalletId(TREASURY_KIND, town.getTownId().toString())), rate);
    }

    @Nonnull
    @Override
    public GoldAccount shopSafe(@Nonnull TownRecord town, @Nonnull UUID player) {
        return new ObolGoldAccount(Obol.wallet(new WalletId(SAFE_KIND, town.getTownId() + "_" + player)), rate);
    }

    @Nonnull
    @Override
    public List<ItemStack> goldItems(@Nonnull GoldSource source, @Nonnull String itemId, long amount) {
        return amount <= 0 ? List.of() : loot.items(source, amount);
    }

    /**
     * What a player typed, in Obol's notation ({@code "2g 35s"}, a bare
     * number is copper), moved as is between the two wallets. Both accounts
     * are ours: Aetherhaven only hands back what {@link #account},
     * {@link #townAccount} and {@link #shopSafe} gave it.
     */
    @Nonnull
    @Override
    public Transfer transfer(@Nonnull GoldAccount from, @Nonnull GoldAccount to, @Nullable String text) {
        Wallet source = ((ObolGoldAccount) from).wallet();
        Wallet target = ((ObolGoldAccount) to).wallet();
        Coins coins;
        if (text == null || text.isBlank()) {
            coins = source.balance();
            if (coins.equals(Coins.ZERO)) {
                return Transfer.NOT_AVAILABLE;
            }
        } else {
            try {
                coins = Coins.parse(text);
            } catch (CoinsParseException e) {
                return Transfer.NOT_AN_AMOUNT;
            }
            if (coins.equals(Coins.ZERO)) {
                return Transfer.NOT_AN_AMOUNT;
            }
        }
        if (!source.transferTo(target, coins)) {
            return Transfer.NOT_AVAILABLE;
        }
        return Transfer.moved(Message.raw(coins.toString()));
    }

    @Nonnull
    @Override
    public Message amount(long amount) {
        return Message.raw(rate.toCoins(amount).toString());
    }

    /** The wallet's balance as is, "3g 25s 4c", not rounded to a coin. */
    @Nonnull
    @Override
    public Message amount(@Nonnull GoldAccount account) {
        return Message.raw(((ObolGoldAccount) account).wallet().balance().toString());
    }

    @Override
    public void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, long amount) {
        ObolUi.show(builder, selector, rate.toCoins(amount));
    }
}
