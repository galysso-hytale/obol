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
import dev.galysso.obol.api.CoinsStyle;
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
 * amounts are written ({@link ObolUi#message}, "2 gold 35 silver" in the
 * coins' colours) and drawn ({@link ObolUi#show}) as Obol coins, and gold
 * loot is whatever {@link GoldLoot} makes of the amount: never coin items,
 * Obol has no item.
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

    /** Aetherhaven's pages are small: only the tiers holding coins are drawn. */
    private static final CoinsStyle DRAWN = CoinsStyle.DEFAULT.withZeroTiers(CoinsStyle.ZeroTiers.HIDDEN);

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
        return Transfer.moved(ObolUi.message(coins));
    }

    /** The amount through the rate, "5 silver" for one gold. */
    @Nonnull
    @Override
    public Message amount(long amount) {
        return ObolUi.message(rate.toCoins(amount));
    }

    /** The wallet's balance as is, "3 gold 25 silver 4 copper", not rounded to a coin. */
    @Nonnull
    @Override
    public Message amount(@Nonnull GoldAccount account) {
        return ObolUi.message(((ObolGoldAccount) account).wallet().balance());
    }

    /** The amount through the rate, drawn as Obol coins at the size of the line, "2 [gold] 5 [copper]" for 2g 5c. */
    @Override
    public void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, long amount, int fontSize) {
        ObolUi.show(builder, selector, rate.toCoins(amount), DRAWN.withFontSize(fontSize));
    }
}
