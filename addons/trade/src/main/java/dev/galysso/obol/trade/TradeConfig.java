package dev.galysso.obol.trade;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The on-disk shape of {@code trade.json}, in the plugin's data directory.
 *
 * <p>A mutable bag of fields, as {@link BuilderCodec} wants. Written with
 * its defaults at first start so that an admin finds it.</p>
 */
public final class TradeConfig {

    static final int MIN_TIMEOUT = 5;
    static final int MAX_OFFER_SLOTS = 12;

    public static final BuilderCodec<TradeConfig> CODEC = BuilderCodec
            .builder(TradeConfig.class, TradeConfig::new)
            .append(new KeyedCodec<>("RequestTimeoutSeconds", Codec.INTEGER),
                    (c, v) -> c.requestTimeoutSeconds = v, c -> c.requestTimeoutSeconds)
            .documentation("How long a player has to accept or decline a trade request, in seconds "
                    + "(at least " + MIN_TIMEOUT + "). Also how long an asker waits before asking again "
                    + "a player who declined.")
            .add()
            .append(new KeyedCodec<>("MaxDistance", Codec.DOUBLE),
                    (c, v) -> c.maxDistance = v, c -> c.maxDistance)
            .documentation("How far apart two players may be, in blocks, for a trade to be asked, "
                    + "kept open and completed.")
            .add()
            .append(new KeyedCodec<>("AcceptDelaySeconds", Codec.INTEGER),
                    (c, v) -> c.acceptDelaySeconds = v, c -> c.acceptDelaySeconds)
            .documentation("How long the Accept button stays disabled after either side changes "
                    + "the offer, in seconds. Guards against a last-instant swap.")
            .add()
            .append(new KeyedCodec<>("OfferSlots", Codec.INTEGER),
                    (c, v) -> c.offerSlots = v, c -> c.offerSlots)
            .documentation("How many item stacks each side may offer, 1 to " + MAX_OFFER_SLOTS + ".")
            .add()
            .build();

    int requestTimeoutSeconds = 30;
    double maxDistance = 8.0;
    int acceptDelaySeconds = 2;
    int offerSlots = MAX_OFFER_SLOTS;

    public TradeConfig() {
    }

    /** {@return how long a request stays open, in seconds, never below {@value #MIN_TIMEOUT}} */
    public int requestTimeoutSeconds() {
        return Math.max(MIN_TIMEOUT, requestTimeoutSeconds);
    }

    /** {@return the greatest distance between the two players, in blocks, at least 1} */
    public double maxDistance() {
        return Math.max(1.0, maxDistance);
    }

    /** {@return how long Accept is disabled after a change, in seconds, never negative} */
    public int acceptDelaySeconds() {
        return Math.max(0, acceptDelaySeconds);
    }

    /** {@return how many stacks each side may offer, clamped to 1..{@value #MAX_OFFER_SLOTS}} */
    public int offerSlots() {
        return Math.clamp(offerSlots, 1, MAX_OFFER_SLOTS);
    }
}
