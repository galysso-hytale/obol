package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.economy.api.Balance;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.ObolUi;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * What one or more wallets held when Aetherhaven asked, as Obol coins: not
 * through the {@link Rate}, not rounded to a coin. Drawn and written as any
 * Obol amount, "3 gold 25 silver 4 copper" for 3g 25s 4c.
 */
record ObolBalance(Coins coins) implements Balance {

    ObolBalance {
        Objects.requireNonNull(coins, "coins");
    }

    @Override
    public void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, int fontSize) {
        ObolUi.show(builder, selector, coins, ObolGoldProvider.DRAWN.withFontSize(fontSize));
    }

    @Nonnull
    @Override
    public Message message() {
        return ObolUi.message(coins);
    }
}
