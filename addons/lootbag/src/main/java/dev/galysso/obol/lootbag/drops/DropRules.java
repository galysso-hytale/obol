package dev.galysso.obol.lootbag.drops;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.MultipleItemDropContainer;
import dev.galysso.obol.lootbag.DropRule;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies the rules of {@code drops.json} to the game's
 * drop tables as they load.
 *
 * <p>For each table a rule names, the table's container is replaced by a
 * {@code Multiple} of one roll holding the original and, after it, the
 * bags of each rule with {@code Weight} set to the rule's {@code Chance}.
 * A child of a {@code Multiple} is kept when its weight beats a roll of
 * 0 to 100, so the original goes on giving what it gave (its weight is
 * put to 100 if a table author wrote less, the root weight being read by
 * nothing else), and each rule is an independent chance on top. The
 * bags themselves are decoded once per rule from the container the rule
 * describes, in the game's own grammar, and shared by the tables.</p>
 *
 * <p>The replacement is done on the loaded object, by reflection on the
 * table's {@code container} field, the way the {@code Pickup} entry is put
 * on the items. Nothing is written and no asset is loaded: a tool that
 * patches the JSON files (Patchly, a pack) does its work first, the
 * server decodes the result, and the rule wraps whatever came out. A
 * reload of the table gives a fresh object, wrapped again. The tables of
 * a lower pack, hidden by a higher one, are left alone.</p>
 *
 * <p>Registered for {@code LoadedAssetsEvent} of {@link ItemDropList}, and
 * {@link #reportUnmatched} once the packs are loaded, to say which rules
 * name no table.</p>
 */
public final class DropRules {

    private static final String FIELD = "container";
    private static final String WEIGHT_FIELD = "weight";

    private final HytaleLogger logger;
    private final List<DropRule.Resolved> rules;
    /** The bags of each rule that decoded, in the order of the rules. */
    private final Map<DropRule.Resolved, ItemDropContainer> lots = new LinkedHashMap<>();
    /** The tables wrapped so far, and by which rules. */
    private final Map<String, List<DropRule.Resolved>> applied = new LinkedHashMap<>();

    public DropRules(List<DropRule.Resolved> rules, HytaleLogger logger) {
        this.rules = List.copyOf(rules);
        this.logger = Objects.requireNonNull(logger, "logger");
        for (DropRule.Resolved rule : this.rules) {
            try {
                ItemDropContainer lot = ItemDropContainer.CODEC.decode(rule.lot(), new ExtraInfo());
                lots.put(rule, Objects.requireNonNull(lot, "decoded to nothing"));
            } catch (RuntimeException e) {
                logger.atSevere().withCause(e).log("drops.json: the rule on %s cannot be built (%s), "
                        + "rule skipped", rule.patterns(), rule.text());
            }
        }
    }

    /** The listener to register for {@code LoadedAssetsEvent} of {@link ItemDropList}. */
    public synchronized void onTablesLoaded(LoadedAssetsEvent<String, ItemDropList, ?> event) {
        AssetMap<String, ItemDropList> map = event.getAssetMap();
        int wrapped = 0;
        for (Map.Entry<String, ItemDropList> entry : event.getLoadedAssets().entrySet()) {
            String id = entry.getKey();
            ItemDropList table = entry.getValue();
            if (map.getAsset(id) != table) {
                // A pack under another: the table the game rolls is the
                // other one, wrapped when it loaded.
                continue;
            }
            String path = relativePath(map.getPath(id));
            List<DropRule.Resolved> matched = new ArrayList<>();
            for (DropRule.Resolved rule : lots.keySet()) {
                if (rule.matcher().matches(id, path)) {
                    matched.add(rule);
                }
            }
            if (matched.isEmpty()) {
                applied.remove(id);
                continue;
            }
            for (DropRule.Resolved rule : matched) {
                checkIncluded(rule, id);
            }
            if (wrap(table, matched)) {
                applied.put(id, List.copyOf(matched));
                wrapped++;
            }
        }
        if (wrapped > 0) {
            logger.atInfo().log("Lootbag drop rules applied to %d table(s)", wrapped);
        }
    }

    /**
     * Says in the log which rules named no table. To run once the packs
     * are loaded ({@code LoadAssetEvent}, late).
     */
    public synchronized void reportUnmatched() {
        for (DropRule.Resolved rule : lots.keySet()) {
            boolean matched = applied.values().stream().anyMatch(list -> list.contains(rule));
            if (!matched) {
                logger.atWarning().log("drops.json: no drop table matches %s (%s)",
                        rule.patterns(), rule.text());
            }
        }
    }

    /** {@return the tables wrapped so far, by id, with the rules on each} */
    public synchronized Map<String, List<DropRule.Resolved>> applied() {
        Map<String, List<DropRule.Resolved>> copy = new LinkedHashMap<>();
        applied.forEach((id, list) -> copy.put(id, List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }

    /** {@return the rules whose bags decoded, in their order} */
    public List<DropRule.Resolved> rules() {
        return List.copyOf(lots.keySet());
    }

    /**
     * {@return the folder and name of a table under {@code Server/Drops},
     * {@code /} separated and without extension, or {@code null}}
     */
    public static String relativePath(Path path) {
        if (path == null) {
            return null;
        }
        String text = path.toString().replace('\\', '/');
        String marker = "/" + ItemDropList.getAssetStore().getPath() + "/";
        int at = text.indexOf(marker);
        if (at < 0) {
            return null;
        }
        String relative = text.substring(at + marker.length());
        int dot = relative.lastIndexOf('.');
        return dot > relative.lastIndexOf('/') ? relative.substring(0, dot) : relative;
    }

    private boolean wrap(ItemDropList table, List<DropRule.Resolved> matched) {
        ItemDropContainer original = table.getContainer();
        List<ItemDropContainer> children = new ArrayList<>();
        if (original instanceof Wrapped already) {
            // Wrapped once already (the event came twice for one object):
            // start again from what the table had.
            original = already.original;
        }
        if (original != null) {
            if (original.getWeight() < 100 && !set(original, ItemDropContainer.class, WEIGHT_FIELD, 100.0)) {
                return false;
            }
            children.add(original);
        }
        for (DropRule.Resolved rule : matched) {
            children.add(lots.get(rule));
        }
        Wrapped wrapped = new Wrapped(original, children.toArray(ItemDropContainer.EMPTY_ARRAY));
        return set(table, ItemDropList.class, FIELD, wrapped);
    }

    private boolean set(Object target, Class<?> type, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.atSevere().withCause(e).log("Cannot set %s.%s: lootbag drop rules not applied", type.getSimpleName(), name);
            return false;
        }
    }

    private void checkIncluded(DropRule.Resolved rule, String on) {
        String included = includedTable(rule);
        if (included != null && ItemDropList.getAssetMap().getAsset(included) == null) {
            logger.atWarning().log("drops.json: the rule on %s includes the table %s, which does not exist "
                    + "(yet): nothing will drop from it in %s until it does", rule.patterns(), included, on);
        }
    }

    private static String includedTable(DropRule.Resolved rule) {
        return rule.lot().containsKey("DroplistId") ? rule.lot().getString("DroplistId").getValue() : null;
    }

    /** The container a wrapped table gets: the original first, then the bags of each rule. */
    private static final class Wrapped extends MultipleItemDropContainer {

        final ItemDropContainer original;

        Wrapped(ItemDropContainer original, ItemDropContainer[] children) {
            super(children, 100, 1, 1);
            this.original = original;
        }
    }
}
