package dev.galysso.obol.lootbag;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A {@link LootLaw} as JSON spells it, before it is checked: the six keys
 * as strings, any of them absent. What {@code lootbag.json} holds under
 * {@code Rarities} and what a drop container carries inline, next to its
 * {@code Rarity}. A mutable bag of fields, as {@link BuilderCodec} wants,
 * and {@link #toLaw()} is the check.
 *
 * <p>The fields are appended to a codec by {@link #appendTo}, once for
 * this class's own codec and once for the container's, so that the two
 * places read the same keys with the same documentation.</p>
 */
public final class LootLawSpec {

    private static final String AMOUNT_FORM = " An amount such as \"5s\", \"1g 20s\" or \"250\" (copper).";

    public static final BuilderCodec<LootLawSpec> CODEC =
            appendTo(BuilderCodec.builder(LootLawSpec.class, LootLawSpec::new), Function.identity()).build();

    String distribution;
    String min;
    String max;
    String mode;
    String step;
    String amount;

    public LootLawSpec() {
    }

    /** {@return the written form of {@code law}, for writing defaults} */
    public static LootLawSpec of(LootLaw law) {
        LootLawSpec spec = new LootLawSpec();
        Map<String, String> entries = law.toEntries();
        spec.distribution = entries.get(LootLaw.KEY_DISTRIBUTION);
        spec.min = entries.get(LootLaw.KEY_MIN);
        spec.max = entries.get(LootLaw.KEY_MAX);
        spec.mode = entries.get(LootLaw.KEY_MODE);
        spec.step = entries.get(LootLaw.KEY_STEP);
        spec.amount = entries.get(LootLaw.KEY_AMOUNT);
        return spec;
    }

    /** {@return whether no key at all was written} */
    public boolean isEmpty() {
        return distribution == null && min == null && max == null && mode == null && step == null
                && amount == null;
    }

    /**
     * {@return the law these keys describe}
     *
     * @throws IllegalArgumentException with the reason, when they do not
     *                                  describe one
     */
    public LootLaw toLaw() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(LootLaw.KEY_DISTRIBUTION, distribution);
        entries.put(LootLaw.KEY_MIN, min);
        entries.put(LootLaw.KEY_MAX, max);
        entries.put(LootLaw.KEY_MODE, mode);
        entries.put(LootLaw.KEY_STEP, step);
        entries.put(LootLaw.KEY_AMOUNT, amount);
        return LootLaw.of(entries);
    }

    /**
     * Appends the six keys to {@code builder}, reading and writing the
     * {@link LootLawSpec} that {@code spec} finds in the decoded object.
     */
    public static <T, B extends BuilderCodec.BuilderBase<T, B>> B appendTo(B builder, Function<T, LootLawSpec> spec) {
        return builder
                .append(new KeyedCodec<>(LootLaw.KEY_DISTRIBUTION, Codec.STRING),
                        (t, v) -> spec.apply(t).distribution = v, t -> spec.apply(t).distribution)
                .documentation("Uniform, Triangular, LogUniform or Fixed. May be left out: Amount alone means Fixed, "
                        + "Mode means Triangular, anything else Uniform.")
                .add()
                .append(new KeyedCodec<>(LootLaw.KEY_MIN, Codec.STRING),
                        (t, v) -> spec.apply(t).min = v, t -> spec.apply(t).min)
                .documentation("The smallest amount, included." + AMOUNT_FORM)
                .add()
                .append(new KeyedCodec<>(LootLaw.KEY_MAX, Codec.STRING),
                        (t, v) -> spec.apply(t).max = v, t -> spec.apply(t).max)
                .documentation("The largest amount, included." + AMOUNT_FORM)
                .add()
                .append(new KeyedCodec<>(LootLaw.KEY_MODE, Codec.STRING),
                        (t, v) -> spec.apply(t).mode = v, t -> spec.apply(t).mode)
                .documentation("Triangular only: the most likely amount, between Min and Max." + AMOUNT_FORM)
                .add()
                .append(new KeyedCodec<>(LootLaw.KEY_STEP, Codec.STRING),
                        (t, v) -> spec.apply(t).step = v, t -> spec.apply(t).step)
                .documentation("Optional: a roll is rounded down to a multiple of this, then kept within the range. "
                        + "Default: one coin of the tier below the largest tier of Max." + AMOUNT_FORM)
                .add()
                .append(new KeyedCodec<>(LootLaw.KEY_AMOUNT, Codec.STRING),
                        (t, v) -> spec.apply(t).amount = v, t -> spec.apply(t).amount)
                .documentation("Fixed only: the one amount the bag gives." + AMOUNT_FORM)
                .add();
    }
}
