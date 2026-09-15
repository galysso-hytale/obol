package dev.galysso.obol.lootbag;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;

/**
 * The on-disk shape of {@code lootbag.json}, in the plugin's data directory.
 *
 * <p>A mutable bag of fields, as {@link BuilderCodec} wants. Written with
 * its defaults at first start so that an admin finds it. {@code Rarities}
 * (the amount law of each rarity) and {@code Drops} (the rules that put
 * lootbags into the game's own drop lists) join it with their classes.</p>
 */
public final class LootbagConfig {

    /** When the coins of a lootbag reach the balance. */
    public enum OpenOn {
        /** On a right-click with the bag in hand. The bag is an item until then. */
        Use,
        /** As soon as the bag is picked up from the ground or taken out of a chest. */
        Pickup
    }

    public static final BuilderCodec<LootbagConfig> CODEC = BuilderCodec
            .builder(LootbagConfig.class, LootbagConfig::new)
            .append(new KeyedCodec<>("OpenOn", new EnumCodec<>(OpenOn.class)),
                    (c, v) -> c.openOn = v, c -> c.openOn)
            .documentation("Use: the bag is an item, opened by a right-click (crouch to open the whole stack). "
                    + "Pickup: the bag is credited the moment it is picked up or taken out of a chest, "
                    + "even with a full inventory, and never sits in an inventory.")
            .add()
            .append(new KeyedCodec<>("RevealAmount", Codec.BOOLEAN),
                    (c, v) -> c.revealAmount = v, c -> c.revealAmount)
            .documentation("Whether the exact amount is rolled when the bag drops and shown on it. "
                    + "Off, the bag shows its range and rolls when opened, and bags of a rarity stack. "
                    + "On, every bag is its own stack.")
            .add()
            .build();

    OpenOn openOn = OpenOn.Use;
    boolean revealAmount = false;

    public LootbagConfig() {
    }

    /** {@return when a lootbag is credited to the balance} */
    public OpenOn openOn() {
        return openOn == null ? OpenOn.Use : openOn;
    }

    /** {@return whether a bag carries its exact amount from the drop, at the cost of stacking} */
    public boolean revealAmount() {
        return revealAmount;
    }
}
