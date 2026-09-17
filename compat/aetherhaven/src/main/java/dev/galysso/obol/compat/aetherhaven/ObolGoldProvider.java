package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.economy.api.EconomyProvider;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Obol;
import dev.galysso.obol.api.ObolUi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Obol as Aetherhaven's economy. Accounts are Obol wallets through the
 * {@link Rate}, amounts are written and drawn as Obol coins, and gold loot
 * is whatever {@link GoldLoot} makes of the value.
 */
final class ObolGoldProvider implements EconomyProvider {

    /** Aetherhaven gold worth {@code coins}, as items to place in a chest or drop. */
    interface GoldLoot {
        List<ItemStack> items(String itemId, long amount);
    }

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
    public List<ItemStack> lootItems(@Nonnull String itemId, long amount) {
        return amount <= 0 ? List.of() : loot.items(itemId, amount);
    }

    @Nonnull
    @Override
    public Message amount(long amount) {
        return Message.raw(rate.toCoins(amount).toString());
    }

    @Override
    public void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, long amount) {
        ObolUi.show(builder, selector, rate.toCoins(amount));
    }
}
