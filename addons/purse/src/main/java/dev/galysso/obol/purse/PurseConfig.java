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

    public static final BuilderCodec<PurseConfig> CODEC = BuilderCodec
            .builder(PurseConfig.class, PurseConfig::new)
            .append(new KeyedCodec<>("DirectGive", Codec.BOOLEAN),
                    (c, v) -> c.directGive = v, c -> c.directGive)
            .documentation("Whether right-clicking a player with a full purse offers to hand its content over. "
                    + "Off, coins only change hands through the item itself (drop it, chest it).")
            .add()
            .build();

    boolean directGive = true;

    public PurseConfig() {
    }

    /** {@return whether the right-click on a player opens the confirmation popup} */
    public boolean directGive() {
        return directGive;
    }
}
