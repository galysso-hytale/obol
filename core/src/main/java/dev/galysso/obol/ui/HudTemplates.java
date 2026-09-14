package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.api.ScreenPosition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The UI documents behind each on-screen {@link CoinsFormat}, and their
 * placement.
 *
 * <p>Two layers. The root is inline UI markup sent with the HUD, the way the
 * server's own spectating HUD does it: it places an empty pill in the
 * requested corner. The pill is then filled with one document per tier that
 * has coins, loaded from this plugin's asset pack ({@code Common/UI/Custom/}
 * {@link #PACK_DIR}) so that the coin image next to each count resolves
 * relative to a real file. The count is set afterwards through a selector,
 * so no text ever needs escaping into markup.</p>
 *
 * <p>Each tier document also declares the three styles of its count
 * ({@link Tint}): the tier's own colour, and the two it takes while the
 * amount rolls towards a gain or a loss.</p>
 */
public final class HudTemplates {

    /** Selector of the pill the tier documents are appended into. */
    public static final String PILL = "#Pill";

    /** Directory of the tier documents and images, under {@code Common/UI/Custom/}. */
    public static final String PACK_DIR = "Obol";

    private static final String LAYOUT = "%LAYOUT%";
    private static final String ANCHOR = "%ANCHOR%";

    /**
     * {@link CoinsFormat#STANDARD}: a translucent pill lining up, from the
     * largest tier down, each count followed by its coin. The outer group
     * spans the screen edge and packs the pill against the requested corner;
     * the pill sizes itself to its content.
     */
    private static final String STANDARD = """
            Group {
              LayoutMode: %LAYOUT%;
              Anchor: (%ANCHOR%, Height: 36);

              Group #Pill {
                Background: #000000(0.35);
                LayoutMode: Left;
                Padding: (Left: 12, Right: 4);
              }
            }
            """;

    private static final Map<String, String> BY_FORMAT_ID = Map.of(
            CoinsFormat.STANDARD.id(), STANDARD);

    private HudTemplates() {
    }

    /**
     * {@return whether the format has an on-screen template}
     */
    public static boolean supports(CoinsFormat format) {
        return BY_FORMAT_ID.containsKey(format.id());
    }

    /**
     * {@return the root document for that format, placed at that position}
     *
     * @throws IllegalArgumentException if the format has no template; a
     *                                  text-only format like
     *                                  {@link CoinsFormat#LONG} is not an
     *                                  on-screen one
     */
    public static String document(CoinsFormat format, ScreenPosition position) {
        String template = BY_FORMAT_ID.get(format.id());
        if (template == null) {
            throw new IllegalArgumentException(
                    "Format " + format + " has no on-screen template");
        }
        return template
                .replace(LAYOUT, position.corner().isRight() ? "Right" : "Left")
                .replace(ANCHOR, anchor(position));
    }

    /**
     * {@return the tiers to append into the pill, largest first}
     *
     * <p>The convention of fixed-base currencies (gold/silver/copper, or
     * {@code 2.05}): tiers above the largest one that has coins are left out,
     * every tier below it is shown even at zero, so that the pill only
     * changes shape when the leading tier changes. Zero is the copper tier
     * alone.</p>
     */
    public static List<Tier> tiers(Coins coins) {
        List<Tier> tiers = new ArrayList<>();
        EnumMap<Denomination, Long> parts = coins.breakdown();
        Denomination[] all = Denomination.values();
        for (int i = all.length - 1; i >= 0; i--) {
            long count = parts.get(all[i]);
            if (count != 0 || !tiers.isEmpty() || i == 0) {
                tiers.add(new Tier(all[i], count));
            }
        }
        return tiers;
    }

    /**
     * The client anchors an element by its distance from the edges it names:
     * {@code TOP_RIGHT(20, 10)} is {@code Top: 10, Right: 20}.
     */
    static String anchor(ScreenPosition position) {
        ScreenPosition.Corner corner = position.corner();
        return (corner.isBottom() ? "Bottom: " : "Top: ") + position.offsetY()
                + ", " + (corner.isRight() ? "Right: " : "Left: ") + position.offsetX();
    }

    /**
     * {@return which tiers to tint while the amount rolls from
     * {@code before} to {@code after}, and in which direction}
     *
     * <p>Only what the player will see move: a tier that is shown for
     * {@code after} and whose count differs from the one it had for
     * {@code before}. The direction is that of the amount as a whole, not
     * of each count — a silver count that goes up because a gold coin was
     * broken is still a loss.</p>
     */
    public static Map<Denomination, Tint> changed(Coins before, Coins after) {
        EnumMap<Denomination, Tint> tints = new EnumMap<>(Denomination.class);
        Tint direction = after.compareTo(before) > 0 ? Tint.UP : Tint.DOWN;
        EnumMap<Denomination, Long> was = before.breakdown();
        for (Tier tier : tiers(after)) {
            if (tier.count() != was.get(tier.denomination())) {
                tints.put(tier.denomination(), direction);
            }
        }
        return tints;
    }

    /**
     * The styles a count can take, as the tier documents name them
     * ({@code @Normal}, {@code @Up}, {@code @Down}).
     */
    public enum Tint {
        /** The tier's own colour. */
        NORMAL,
        /** The amount is growing. */
        UP,
        /** The amount is shrinking. */
        DOWN;

        /** {@return the name of the style in the tier document} */
        public String styleName() {
            String lower = name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }

    /**
     * One tier of the pill: which document to append and what count to set
     * in it.
     *
     * @param denomination the tier
     * @param count        how many coins of it, {@code >= 0}
     */
    public record Tier(Denomination denomination, long count) {

        /** {@return the name of the tier as the pack spells it, e.g. {@code Gold}} */
        public String name() {
            String lower = denomination.name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }

        /** {@return the document to append, relative to {@code Common/UI/Custom/}} */
        public String document() {
            return PACK_DIR + "/" + name() + ".ui";
        }

        /** {@return the selector of the count label, for {@code set}} */
        public String countSelector() {
            return "#" + name() + " #Count.Text";
        }

        /**
         * {@return the selector of the count label's style, for {@code set}
         * with a reference to one of the {@link Tint} styles of
         * {@link #document()}}
         */
        public String styleSelector() {
            return "#" + name() + " #Count.Style";
        }

        /**
         * {@return the count as the label shows it}
         *
         * <p>Plain digits, no zero padding: the fixed-width column of the
         * label keeps the icons in place, the way game currencies are shown
         * ({@code 1g 5s 3c}, not {@code 1g 05s 03c}).</p>
         */
        public String countText() {
            return Long.toString(count);
        }
    }
}
