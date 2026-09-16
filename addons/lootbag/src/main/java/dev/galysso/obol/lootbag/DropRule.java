package dev.galysso.obol.lootbag;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * One entry of {@code Rules} in {@code drops.json}: which drop tables of
 * the game get lootbags, how often, and which bags.
 *
 * <pre>
 * { "Droplists": ["Prefabs/Zone*_Encounters_Tier2"], "Chance": 35, "Bags": { "Common": 40, "Uncommon": 40, "Rare": 17, "Epic": 3 } }
 * { "Droplists": ["NPCs/Boss/*"], "Chance": 100, "Rarity": "Legendary", "Min": "20g", "Max": "50g" }
 * { "Droplists": ["Drop_Skeleton_*"], "Chance": 8, "Droplist": "Obol_Lootbag_Tier1" }
 * </pre>
 *
 * <p>{@code Droplists} names tables by pattern. A pattern with a
 * {@code /} is matched against the table's folder and name under
 * {@code Server/Drops} ({@code NPCs/Undead/Drop_Skeleton}), which is how
 * the game sorts its tables by kind of creature. A pattern without one is
 * matched against the id alone. {@code *} stands for anything but a
 * {@code /}, {@code **} for anything, and case does not count, as it does
 * not for asset ids.</p>
 *
 * <p>{@code Chance} is the percentage of drops of a matched table that get
 * the rule's bags, on top of what the table gives: the {@code Weight} of a
 * child of a vanilla {@code Multiple}. The bags come in one of three forms.
 * {@code Bags}, a rarity to weight map, gives one bag drawn at the weights.
 * {@code Rarity}, with an optional law written next to it (the keys of
 * {@code Rarities}), gives one bag of that rarity. {@code Droplist} gives
 * whatever that table gives, one of the four shipped or any other.</p>
 *
 * <p>A mutable bag of fields, as {@link BuilderCodec} wants. {@link #resolve}
 * checks it and turns it into what the listener applies: a matcher and the
 * bags as a drop container written in the game's own grammar.</p>
 */
public final class DropRule {

    /** A number read as a double, written without a decimal when it has none. */
    private static final Codec<Double> NUMBER = new Codec<>() {
        @Override
        public Double decode(BsonValue value, ExtraInfo extraInfo) {
            return Codec.DOUBLE.decode(value, extraInfo);
        }

        @Override
        public BsonValue encode(Double value, ExtraInfo extraInfo) {
            return value == Math.rint(value) && Math.abs(value) < Integer.MAX_VALUE
                    ? new BsonInt32(value.intValue()) : new BsonDouble(value);
        }

        @Nonnull
        @Override
        public Schema toSchema(@Nonnull SchemaContext context) {
            return Codec.DOUBLE.toSchema(context);
        }
    };

    public static final BuilderCodec<DropRule> CODEC = LootLawSpec.appendTo(BuilderCodec
            .builder(DropRule.class, DropRule::new)
            .append(new KeyedCodec<>("Droplists", Codec.STRING_ARRAY),
                    (r, v) -> r.droplists = v, r -> r.droplists)
            .documentation("The drop tables the rule applies to. A pattern with a / is matched against the folder "
                    + "and name of the table under Server/Drops (NPCs/Undead/*), one without against its id "
                    + "(Zone*_Encounters_Tier2). * matches anything but a /, ** anything.")
            .add()
            .append(new KeyedCodec<>("Chance", NUMBER),
                    (r, v) -> r.chance = v, r -> r.chance)
            .documentation("The percentage of drops of a matched table that get the rule's bags, "
                    + "on top of what the table gives. 100 if left out.")
            .add()
            .append(new KeyedCodec<>("Bags", new MapCodec<>(NUMBER, LinkedHashMap::new)),
                    (r, v) -> r.bags = v, r -> r.bags)
            .documentation("One bag drawn among rarities at these relative weights, as a vanilla Choice does. "
                    + "One of Bags, Rarity or Droplist.")
            .add()
            .append(new KeyedCodec<>("Rarity", Codec.STRING),
                    (r, v) -> r.rarity = v, r -> r.rarity)
            .documentation("One bag of this rarity, with the rarity's law unless one is written next to it "
                    + "(Distribution, Min, Max, Mode, Step, Amount). One of Bags, Rarity or Droplist.")
            .add()
            .append(new KeyedCodec<>("Droplist", Codec.STRING),
                    (r, v) -> r.droplist = v, r -> r.droplist)
            .documentation("Whatever this drop table gives: Obol_Lootbag_Tier1 to Tier4, or a table of yours. "
                    + "One of Bags, Rarity or Droplist.")
            .add(), r -> r.law)
            .build();

    String[] droplists;
    Double chance;
    Map<String, Double> bags;
    String rarity;
    LootLawSpec law = new LootLawSpec();
    String droplist;

    public DropRule() {
    }

    /** {@return a rule giving one bag drawn among {@code bags}, for writing defaults} */
    public static DropRule bags(double chance, Map<Rarity, Integer> bags, String... droplists) {
        DropRule rule = new DropRule();
        rule.droplists = droplists;
        rule.chance = chance;
        rule.bags = new LinkedHashMap<>();
        // In the order of the rarities, whatever the map's.
        new TreeMap<>(bags).forEach((rarity, weight) -> rule.bags.put(rarity.name(), (double) weight));
        return rule;
    }

    /** {@return a rule giving one bag of {@code rarity} with {@code law}, for writing defaults} */
    public static DropRule rarity(double chance, Rarity rarity, LootLaw law, String... droplists) {
        DropRule rule = new DropRule();
        rule.droplists = droplists;
        rule.chance = chance;
        rule.rarity = rarity.name();
        rule.law = LootLawSpec.of(law);
        return rule;
    }

    /**
     * A rule as the listener applies it.
     *
     * @param matcher the tables it names
     * @param chance  the percentage, 0 to 100
     * @param lot     the bags, a drop container in the game's grammar with
     *                {@code Weight} set to {@code chance}, for
     *                {@code ItemDropContainer.CODEC} to decode
     * @param text    the rule in one line, for logs and the command
     */
    public record Resolved(Matcher matcher, double chance, BsonDocument lot, String text) {

        /** {@return the tables named by {@code Droplists}, for messages} */
        public String patterns() {
            return matcher.text();
        }
    }

    /** The patterns of {@code Droplists}, compiled. */
    public static final class Matcher {

        private final List<String> texts;
        private final List<Pattern> byId = new ArrayList<>();
        private final List<Pattern> byPath = new ArrayList<>();

        private Matcher(List<String> texts) {
            this.texts = List.copyOf(texts);
            for (String text : texts) {
                (text.indexOf('/') >= 0 ? byPath : byId).add(compile(text));
            }
        }

        /** {@return the matcher of these glob patterns} */
        public static Matcher of(String... patterns) {
            return new Matcher(List.of(patterns));
        }

        /**
         * {@return whether a table is named}
         *
         * @param id   the table's id
         * @param path the table's folder and name under {@code Server/Drops},
         *             {@code /} separated and without extension, or
         *             {@code null} when unknown
         */
        public boolean matches(String id, String path) {
            for (Pattern pattern : byId) {
                if (pattern.matcher(id).matches()) {
                    return true;
                }
            }
            if (path != null) {
                for (Pattern pattern : byPath) {
                    if (pattern.matcher(path).matches()) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** {@return the patterns as written, comma separated} */
        public String text() {
            return String.join(", ", texts);
        }

        private static Pattern compile(String glob) {
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '*') {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++;
                    } else {
                        regex.append("[^/]*");
                    }
                } else {
                    regex.append(Pattern.quote(String.valueOf(c)));
                }
            }
            return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
        }
    }

    /**
     * {@return this rule, checked}
     *
     * @throws IllegalArgumentException with the reason when it does not
     *                                  hold: no pattern, a chance outside
     *                                  0 to 100, none or several of the
     *                                  three forms, an unknown rarity, a
     *                                  weight at or below zero, a law that
     *                                  does not hold
     */
    public Resolved resolve() {
        List<String> patterns = new ArrayList<>();
        if (droplists != null) {
            for (String pattern : droplists) {
                if (pattern != null && !pattern.isBlank()) {
                    patterns.add(pattern.trim());
                }
            }
        }
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("Droplists names no table");
        }
        double percent = chance == null ? 100 : chance;
        if (!(percent >= 0 && percent <= 100)) {
            throw new IllegalArgumentException("Chance must be between 0 and 100, not " + chance);
        }
        int forms = (bags != null ? 1 : 0) + (rarity != null ? 1 : 0) + (droplist != null ? 1 : 0);
        if (forms != 1) {
            throw new IllegalArgumentException(forms == 0
                    ? "one of Bags, Rarity or Droplist is needed"
                    : "only one of Bags, Rarity or Droplist, not " + forms);
        }
        if (!law.isEmpty() && rarity == null) {
            throw new IllegalArgumentException("a loot law goes with Rarity only");
        }
        BsonDocument lot;
        String text;
        if (bags != null) {
            lot = choice(percent);
            text = "one of " + bagsText();
        } else if (rarity != null) {
            Rarity wanted = Rarity.parse(rarity)
                    .orElseThrow(() -> new IllegalArgumentException("not a rarity: " + rarity));
            LootLaw inline = law.isEmpty() ? null : law.toLaw();
            lot = bag(wanted, inline, percent);
            text = "a " + wanted.lower() + " bag" + (inline == null ? "" : " of " + inline);
        } else {
            if (droplist.isBlank()) {
                throw new IllegalArgumentException("Droplist is blank");
            }
            lot = new BsonDocument("Type", new BsonString("Droplist"))
                    .append("Weight", new BsonDouble(percent))
                    .append("DroplistId", new BsonString(droplist.trim()));
            text = "the table " + droplist.trim();
        }
        return new Resolved(new Matcher(patterns), percent, lot, format(percent) + "% " + text);
    }

    /** {@return the {@code Droplist} this rule includes, or {@code null}} */
    public String droplist() {
        return droplist == null ? null : droplist.trim();
    }

    private BsonDocument choice(double percent) {
        BsonArray containers = new BsonArray();
        for (Map.Entry<String, Double> entry : bags.entrySet()) {
            Rarity wanted = Rarity.parse(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Bags: not a rarity: " + entry.getKey()));
            Double weight = entry.getValue();
            if (weight == null || !(weight > 0)) {
                throw new IllegalArgumentException("Bags." + entry.getKey() + ": the weight must be above zero");
            }
            containers.add(bag(wanted, null, weight));
        }
        if (containers.isEmpty()) {
            throw new IllegalArgumentException("Bags names no rarity");
        }
        return new BsonDocument("Type", new BsonString("Choice"))
                .append("Weight", new BsonDouble(percent))
                .append("Containers", containers);
    }

    private static BsonDocument bag(Rarity rarity, LootLaw inline, double weight) {
        BsonDocument bag = new BsonDocument("Type", new BsonString("ObolLootbag"))
                .append("Weight", new BsonDouble(weight))
                .append("Rarity", new BsonString(rarity.name()));
        if (inline != null) {
            inline.toEntries().forEach((key, value) -> bag.append(key, new BsonString(value)));
        }
        return bag;
    }

    private String bagsText() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Double> entry : bags.entrySet()) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(entry.getKey().toLowerCase(Locale.ROOT)).append(' ').append(format(entry.getValue()));
        }
        return text.toString();
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
