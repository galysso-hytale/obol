package dev.galysso.obol.lootbag;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The on-disk shape of {@code drops.json}, next to {@code lootbag.json}
 * in the plugin's data directory: where lootbags fall.
 *
 * <p>Its own file, apart from the laws and the opening mode: it is the
 * one an admin edits often, it grows with the server's content, and a
 * server that places its bags itself (Patchly, its own packs) empties it
 * without touching the rest. {@code Rules} is a list of {@link DropRule},
 * written with a first setting at first start.</p>
 */
public final class DropsConfig {

    public static final BuilderCodec<DropsConfig> CODEC = BuilderCodec
            .builder(DropsConfig.class, DropsConfig::new)
            .append(new KeyedCodec<>("Rules", new ArrayCodec<>(DropRule.CODEC, DropRule[]::new)),
                    (c, v) -> c.rules = v, c -> c.rules)
            .documentation("The rules that put lootbags into the game's drop tables, on top of what they give: "
                    + "which tables (Droplists), how often (Chance, a percentage) and which bags "
                    + "(Bags, Rarity or Droplist). A rule written wrong is skipped, with a line in the log. "
                    + "An empty list puts no bag anywhere, for a server that places its bags itself, "
                    + "with Patchly or in its own packs.")
            .add()
            .build();

    DropRule[] rules = defaultRules();

    /** Resolved once by {@link #resolve}, {@code null} until then. */
    private List<DropRule.Resolved> resolved;

    public DropsConfig() {
    }

    /**
     * The first setting: every dungeon chest through the
     * {@code Zone<Z>_Encounters_Tier<T>} table each of them includes, the
     * bags going up with the tier, the hostile humanoids now and then
     * (the undead, goblins, trorks and outlanders: people who would carry
     * a purse, not beasts, and not the merchants, kweebecs and ferans one
     * is not meant to kill), the boss always. The tables of the shipped
     * pack say the same rarities per tier.
     */
    private static DropRule[] defaultRules() {
        return new DropRule[] {
                DropRule.bags(30, Map.of(Rarity.Common, 70, Rarity.Uncommon, 25, Rarity.Rare, 5),
                        "Prefabs/Zone*_Encounters_Tier1"),
                DropRule.bags(35, Map.of(Rarity.Common, 40, Rarity.Uncommon, 40, Rarity.Rare, 17, Rarity.Epic, 3),
                        "Prefabs/Zone*_Encounters_Tier2"),
                DropRule.bags(40, Map.of(Rarity.Uncommon, 40, Rarity.Rare, 40, Rarity.Epic, 17, Rarity.Legendary, 3),
                        "Prefabs/Zone*_Encounters_Tier3"),
                DropRule.bags(50, Map.of(Rarity.Rare, 45, Rarity.Epic, 40, Rarity.Legendary, 15),
                        "Prefabs/Zone*_Encounters_Tier4"),
                DropRule.bags(8, Map.of(Rarity.Common, 85, Rarity.Uncommon, 15),
                        "NPCs/Undead/*", "NPCs/Intelligent/Goblin/*", "NPCs/Intelligent/Trork/*",
                        "NPCs/Intelligent/Outlander/*"),
                DropRule.rarity(100, Rarity.Legendary, LootLaw.uniform(Coins.of(Denomination.GOLD, 20), Coins.of(Denomination.GOLD, 50)),
                        "NPCs/Boss/*"),
        };
    }

    /**
     * Reads {@code Rules} into rules, once. A rule that does not hold is
     * skipped, and {@code problem} is told why, with its key.
     */
    public void resolve(BiConsumer<String, String> problem) {
        List<DropRule.Resolved> accepted = new ArrayList<>();
        DropRule[] written = rules == null ? new DropRule[0] : rules;
        for (int i = 0; i < written.length; i++) {
            if (written[i] == null) {
                continue;
            }
            try {
                accepted.add(written[i].resolve());
            } catch (IllegalArgumentException e) {
                problem.accept("Rules[" + i + "]", e.getMessage() + ", rule skipped");
            }
        }
        resolved = List.copyOf(accepted);
    }

    /**
     * {@return the rules that hold, in their order}
     *
     * @throws IllegalStateException before {@link #resolve} has run
     */
    public List<DropRule.Resolved> rules() {
        if (resolved == null) {
            throw new IllegalStateException("Rules not resolved yet");
        }
        return resolved;
    }
}
