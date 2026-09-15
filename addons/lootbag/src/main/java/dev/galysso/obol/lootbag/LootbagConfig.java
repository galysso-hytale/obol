package dev.galysso.obol.lootbag;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The on-disk shape of {@code lootbag.json}, in the plugin's data directory.
 *
 * <p>A mutable bag of fields, as {@link BuilderCodec} wants. Written with
 * its defaults at first start so that an admin finds it. {@code Rarities}
 * is the amount law of each rarity, what a drop container that only names
 * a rarity writes into the bag and what a bag without a law rolls.
 * {@code Drops} (the rules that put lootbags into the game's own drop
 * lists) joins it with its class.</p>
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
            .append(new KeyedCodec<>("Rarities", new MapCodec<>(LootLawSpec.CODEC, LinkedHashMap::new)),
                    (c, v) -> c.rarities = v, c -> c.rarities)
            .documentation("The amount law of each rarity (Common, Uncommon, Rare, Epic, Legendary): "
                    + "Distribution (Uniform, Triangular, LogUniform or Fixed), Min, Max, Mode for Triangular, "
                    + "Amount for Fixed, and an optional Step. A rarity left out or written wrong keeps its default, "
                    + "with a line in the log.")
            .add()
            .build();

    OpenOn openOn = OpenOn.Use;
    boolean revealAmount = false;
    Map<String, LootLawSpec> rarities = defaultRarities();

    /** Resolved once by {@link #resolve}, {@code null} until then. */
    private Map<Rarity, LootLaw> laws;

    public LootbagConfig() {
    }

    private static Map<String, LootLawSpec> defaultRarities() {
        Map<String, LootLawSpec> specs = new LinkedHashMap<>();
        for (Rarity rarity : Rarity.values()) {
            specs.put(rarity.name(), LootLawSpec.of(LootLaw.defaultFor(rarity)));
        }
        return specs;
    }

    /**
     * Reads {@code Rarities} into laws, once. A rarity that is missing or
     * whose law does not hold keeps its default, and {@code problem} is
     * told why. A key that is no rarity is reported too, and ignored.
     */
    public void resolve(BiConsumer<String, String> problem) {
        Map<Rarity, LootLawSpec> specs = new EnumMap<>(Rarity.class);
        for (Map.Entry<String, LootLawSpec> entry : (rarities == null ? Map.<String, LootLawSpec>of() : rarities).entrySet()) {
            Rarity.parse(entry.getKey()).ifPresentOrElse(
                    rarity -> specs.put(rarity, entry.getValue()),
                    () -> problem.accept(entry.getKey(), "not a rarity, ignored"));
        }
        Map<Rarity, LootLaw> resolved = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            LootLawSpec spec = specs.get(rarity);
            LootLaw law = LootLaw.defaultFor(rarity);
            if (spec == null) {
                problem.accept(rarity.name(), "missing, using " + law);
            } else {
                try {
                    law = spec.toLaw();
                } catch (IllegalArgumentException e) {
                    problem.accept(rarity.name(), e.getMessage() + ", using " + law);
                }
            }
            resolved.put(rarity, law);
        }
        laws = resolved;
    }

    /**
     * {@return the amount law of {@code rarity}, from {@code Rarities} or
     * its default}
     *
     * @throws IllegalStateException before {@link #resolve} has run
     */
    public LootLaw law(Rarity rarity) {
        if (laws == null) {
            throw new IllegalStateException("Rarities not resolved yet");
        }
        return laws.get(rarity);
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
