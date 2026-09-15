package dev.galysso.obol.lootbag.drops;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ItemDropContainer;
import dev.galysso.obol.lootbag.LootLaw;
import dev.galysso.obol.lootbag.LootLawSpec;
import dev.galysso.obol.lootbag.LootbagConfig;
import dev.galysso.obol.lootbag.Rarity;
import dev.galysso.obol.lootbag.api.LootbagItem;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * The {@code ObolLootbag} entry of a drop table: one lootbag of a rarity.
 *
 * <pre>
 * { "Type": "ObolLootbag", "Weight": 30, "Rarity": "Rare" }
 * { "Type": "ObolLootbag", "Rarity": "Epic", "Distribution": "LogUniform", "Min": "1g", "Max": "9g" }
 * </pre>
 *
 * <p>{@code Rarity} picks the item and, by default, the law the rarity has
 * in {@code lootbag.json}. A law written inline replaces it, and is
 * checked when the table loads, like any other mistake in a table. The
 * container rolls nothing: it writes the law into the bag, and the bag is
 * rolled when opened, so that bags of one rarity from any table, chest or
 * creature are the same stack. With {@code RevealAmount} on, it rolls with
 * the table's own random source and writes the amount as a fixed law.</p>
 *
 * <p>Built by {@link #codec}, which captures the config: a container is
 * decoded for each table that names one, and reads the config when the
 * table drops.</p>
 */
public final class LootbagDropContainer extends ItemDropContainer {

    /** The value of {@code Type} that names this container. */
    public static final String TYPE = "ObolLootbag";

    private final LootbagConfig config;
    private Rarity rarity;
    private final LootLawSpec inline = new LootLawSpec();
    /** The inline law, resolved when the table loads. {@code null} for the rarity's own. */
    private LootLaw inlineLaw;

    private LootbagDropContainer(LootbagConfig config) {
        this.config = config;
    }

    /** {@return the codec of this container, reading {@code config} when a table drops} */
    @SuppressWarnings("deprecation") // validator(): deprecated, but the one hook that fails a table
                                     // with a message and a location, and what ItemDropList itself uses.
    public static BuilderCodec<LootbagDropContainer> codec(LootbagConfig config) {
        BuilderCodec.Builder<LootbagDropContainer> builder = BuilderCodec
                .builder(LootbagDropContainer.class, () -> new LootbagDropContainer(config), ItemDropContainer.DEFAULT_CODEC)
                .append(new KeyedCodec<>("Rarity", new EnumCodec<>(Rarity.class)),
                        (c, v) -> c.rarity = v, c -> c.rarity)
                .addValidator(Validators.nonNull())
                .documentation("Common, Uncommon, Rare, Epic or Legendary: the bag, and its law unless one is written here.")
                .add();
        return LootLawSpec.appendTo(builder, c -> c.inline)
                .validator((c, results) -> {
                    if (c.inline.isEmpty()) {
                        return;
                    }
                    try {
                        c.inlineLaw = c.inline.toLaw();
                    } catch (IllegalArgumentException e) {
                        results.fail("Loot law: " + e.getMessage());
                    }
                })
                .build();
    }

    /** {@return the rarity of the bag this container drops} */
    public Rarity getRarity() {
        return rarity;
    }

    /** {@return the law the bag will carry, inline or the rarity's} */
    public LootLaw law() {
        return inlineLaw != null ? inlineLaw : config.law(rarity);
    }

    @Override
    protected void populateDrops(List<ItemDrop> drops, DoubleSupplier random, Set<String> visited) {
        if (rarity == null) {
            // Refused at load for the missing Rarity: nothing to drop.
            return;
        }
        LootLaw law = law();
        if (config.revealAmount()) {
            // Rolled here, with the table's own source: the bag says its amount.
            law = LootLaw.fixed(law.roll(random));
        }
        drops.add(drop(law));
    }

    @Override
    public List<ItemDrop> getAllDrops(List<ItemDrop> drops) {
        // What a listing shows, and what the table's own validator asks
        // for: the bag as it drops without RevealAmount.
        if (rarity != null) {
            drops.add(drop(law()));
        }
        return drops;
    }

    private ItemDrop drop(LootLaw law) {
        return new ItemDrop(LootbagItem.itemId(rarity), LootbagItem.metadata(law, config.openOn()), 1, 1);
    }

    @Override
    public String toString() {
        return "LootbagDropContainer{rarity=" + rarity + ", law=" + (inlineLaw != null ? inlineLaw : "<rarity>")
                + ", weight=" + weight + '}';
    }
}
