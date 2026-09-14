package dev.galysso.obol.purse;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The on-disk shape of {@code purse.json}, in the plugin's data directory.
 *
 * <p>A mutable bag of fields, as {@link BuilderCodec} wants. Written with
 * its defaults at first start so that an admin finds it.</p>
 */
public final class PurseConfig {

    static final int MIN_TIMEOUT = 5;

    public static final BuilderCodec<PurseConfig> CODEC = BuilderCodec
            .builder(PurseConfig.class, PurseConfig::new)
            .append(new KeyedCodec<>("DirectGive", Codec.BOOLEAN),
                    (c, v) -> c.directGive = v, c -> c.directGive)
            .documentation("Whether right-clicking a player with a full purse offers to hand its content over. "
                    + "Off, coins only change hands through the item itself (drop it, chest it).")
            .add()
            .append(new KeyedCodec<>("OfferTimeoutSeconds", Codec.INTEGER),
                    (c, v) -> c.offerTimeoutSeconds = v, c -> c.offerTimeoutSeconds)
            .documentation("How long the receiver has to accept or decline a handed purse, in seconds "
                    + "(at least " + MIN_TIMEOUT + "). Also how long a giver waits before offering again "
                    + "to a player who declined.")
            .add()
            .build();

    boolean directGive = true;
    int offerTimeoutSeconds = 30;

    public PurseConfig() {
    }

    /** {@return whether the right-click on a player opens the confirmation popup} */
    public boolean directGive() {
        return directGive;
    }

    /** {@return how long an offer stays open, in seconds, never below {@value #MIN_TIMEOUT}} */
    public int offerTimeoutSeconds() {
        return Math.max(MIN_TIMEOUT, offerTimeoutSeconds);
    }
}
