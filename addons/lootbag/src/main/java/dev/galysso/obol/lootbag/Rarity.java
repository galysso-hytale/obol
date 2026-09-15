package dev.galysso.obol.lootbag;

import java.util.Locale;
import java.util.Optional;

/**
 * The five lootbags, one per vanilla item quality. The quality is the whole
 * visual language of a bag (the frame of its slot, the frame and colour of
 * its tooltip, the label under its name, the halo on the ground) and the
 * key of the amount law an admin sets in {@code lootbag.json}.
 *
 * <p>No sixth tier: the five vanilla qualities cover every frame the game
 * ships, and a bag's rarity says nothing about the coins inside.</p>
 */
public enum Rarity {
    Common("Slack"),
    Uncommon("Full"),
    Rare("Full"),
    Epic("Tight"),
    Legendary("Tight");

    /** What an item id of a lootbag starts with, the template's id plus an underscore. */
    public static final String ITEM_ID_PREFIX = "Obol_Lootbag_";

    private final String model;

    Rarity(String model) {
        this.model = model;
    }

    /** {@return the vanilla quality's id, as {@code Server/Item/Qualities} names it} */
    public String qualityName() {
        return name();
    }

    /** {@return the item id of the bag of this rarity, {@code Obol_Lootbag_<Rarity>}} */
    public String itemId() {
        return ITEM_ID_PREFIX + name();
    }

    /**
     * {@return how full the sack is drawn: {@code Slack}, {@code Full} or
     * {@code Tight}, the three blocky models of the pack}
     */
    public String model() {
        return model;
    }

    /** {@return the rarity whose item id this is, if it is one} */
    public static Optional<Rarity> ofItemId(String itemId) {
        if (itemId == null || !itemId.startsWith(ITEM_ID_PREFIX)) {
            return Optional.empty();
        }
        return parse(itemId.substring(ITEM_ID_PREFIX.length()));
    }

    /** {@return the rarity of that name, in any case, if there is one} */
    public static Optional<Rarity> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (Rarity rarity : values()) {
            if (rarity.name().equalsIgnoreCase(name.trim())) {
                return Optional.of(rarity);
            }
        }
        return Optional.empty();
    }

    /** {@return the name in lower case, for messages and logs} */
    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
