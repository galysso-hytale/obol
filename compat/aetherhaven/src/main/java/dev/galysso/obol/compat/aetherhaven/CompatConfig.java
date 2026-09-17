package dev.galysso.obol.compat.aetherhaven;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsParseException;

import java.util.function.Consumer;

/**
 * The on-disk shape of {@code config.json}, in the plugin's data directory.
 * Written with its defaults at first start so that an admin finds it.
 * Read once at startup: changing the rate changes every price in the world,
 * so it is not reloaded while the server runs.
 */
public final class CompatConfig {

    public static final BuilderCodec<CompatConfig> CODEC = BuilderCodec
            .builder(CompatConfig.class, CompatConfig::new)
            .append(new KeyedCodec<>("Coin", Codec.STRING), (c, v) -> c.coin = v, c -> c.coin)
            .documentation("What one Aetherhaven gold coin is worth, in Obol coins (\"5s\", \"1g\", \"2g 50s\"). "
                    + "Every Aetherhaven price, cost and loot roll is multiplied by it.")
            .add()
            .build();

    String coin = Rate.DEFAULT.coin().toString();

    public CompatConfig() {
    }

    /** The rate {@code Coin} names, or the default with {@code problem} told why. */
    public Rate rate(Consumer<String> problem) {
        try {
            return new Rate(Coins.parse(coin));
        } catch (CoinsParseException | IllegalArgumentException e) {
            problem.accept("Coin \"" + coin + "\": " + e.getMessage() + ", using " + Rate.DEFAULT.coin());
            return Rate.DEFAULT;
        }
    }
}
