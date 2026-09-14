package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.api.ScreenPosition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The UI documents of an overlay, and their placement.
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

    /** Selector of the list the rows of the changes feed go into. */
    public static final String FEED = "#Feed";

    /** Selector, inside a feed row, of the chip the tier documents are appended into. */
    public static final String CHANGE = "#Change";

    /**
     * Directory of the tier documents and images, under {@code Common/UI/Custom/}:
     * the one {@link Denomination#texture()} points into.
     */
    public static final String PACK_DIR = "Obol";

    /** Directory of the smaller tier documents of the feed, under {@link #PACK_DIR}. */
    public static final String FEED_DIR = "Feed";

    /** Height of the balance pill, in UI pixels. */
    static final int PILL_HEIGHT = 36;
    /** Height of a feed row and the gap above it, in UI pixels. */
    static final int ROW_HEIGHT = 26;
    static final int ROW_GAP = 4;

    private static final String LAYOUT = "%LAYOUT%";
    private static final String STACK = "%STACK%";
    private static final String ANCHOR = "%ANCHOR%";
    private static final String HEIGHT = "%HEIGHT%";
    private static final String GAP = "%GAP%";

    /**
     * The root: a translucent pill lining up, from the largest tier down,
     * each count followed by its coin, and under it (over it, in a bottom
     * corner) the feed of recent changes. The outer group spans the screen
     * edge, tall enough for the pill and a full feed, and stacks from the
     * requested corner; each row packs its content against the corner's
     * side and sizes itself to it.
     */
    private static final String ROOT = """
            Group {
              LayoutMode: %STACK%;
              Anchor: (%ANCHOR%, Height: %HEIGHT%);

              Group {
                LayoutMode: %LAYOUT%;
                Anchor: (Height: 36);

                Group #Pill {
                  Background: #000000(0.35);
                  LayoutMode: Left;
                  Padding: (Left: 12, Right: 4);
                }
              }

              Group #Feed {
                LayoutMode: %STACK%;
              }
            }
            """;

    /**
     * One row of the feed: a chip packed against the corner's side, a gap
     * away from the previous row. Sent inline: it carries no image, the tier
     * documents appended into the chip do.
     */
    private static final String FEED_ROW = """
            Group {
              LayoutMode: %LAYOUT%;
              Anchor: (Height: 26, %GAP%);

              Group #Change {
                Background: #000000(0.35);
                LayoutMode: Left;
                Padding: (Left: 8, Right: 2);
              }
            }
            """;

    private HudTemplates() {
    }

    /**
     * {@return the root document, placed at that position}
     */
    public static String document(ScreenPosition position) {
        return ROOT
                .replace(LAYOUT, isRight(position.corner()) ? "Right" : "Left")
                .replace(STACK, isBottom(position.corner()) ? "Bottom" : "Top")
                .replace(HEIGHT, Integer.toString(PILL_HEIGHT + ChangeFeed.MAX_ROWS * (ROW_HEIGHT + ROW_GAP)))
                .replace(ANCHOR, anchor(position));
    }

    /**
     * {@return the markup of one feed row, for that position}
     */
    public static String feedRow(ScreenPosition position) {
        return FEED_ROW
                .replace(LAYOUT, isRight(position.corner()) ? "Right" : "Left")
                .replace(GAP, (isBottom(position.corner()) ? "Bottom: " : "Top: ") + ROW_GAP);
    }

    /** {@return the selector of the {@code index}th row of the feed, newest first} */
    public static String feedRow(int index) {
        return FEED + "[" + index + "]";
    }

    /**
     * {@return the tiers of a change, largest first}
     *
     * <p>An amount, not a position: zero tiers are left out, the way
     * {@link Coins#toString()} writes it ({@code +2g 50s}).</p>
     */
    public static List<Tier> feedTiers(Coins amount) {
        List<Tier> tiers = new ArrayList<>();
        EnumMap<Denomination, Long> parts = amount.breakdown();
        Denomination[] all = Denomination.values();
        for (int i = all.length - 1; i >= 0; i--) {
            long count = parts.get(all[i]);
            if (count != 0) {
                tiers.add(new Tier(all[i], count));
            }
        }
        return tiers;
    }

    /**
     * {@return {@code color} ({@code #RRGGBB}) at that opacity, as a
     * runtime patch of a colour property reads it: {@code #RRGGBBAA}}
     *
     * <p>Not the {@code #RRGGBB(0.75)} of the documents: the client's
     * converter for a patched colour takes the eight-digit form only (the
     * server's own pages send {@code #RRGGBB99}), and disconnects the
     * player on anything else.</p>
     */
    public static String withOpacity(String color, double opacity) {
        int alpha = (int) Math.round(Math.clamp(opacity, 0.0, 1.0) * 255);
        return color + String.format(Locale.ROOT, "%02X", alpha);
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
        return (isBottom(corner) ? "Bottom: " : "Top: ") + position.offsetY()
                + ", " + (isRight(corner) ? "Right: " : "Left: ") + position.offsetX();
    }

    private static boolean isRight(ScreenPosition.Corner corner) {
        return corner == ScreenPosition.Corner.TOP_RIGHT || corner == ScreenPosition.Corner.BOTTOM_RIGHT;
    }

    private static boolean isBottom(ScreenPosition.Corner corner) {
        return corner == ScreenPosition.Corner.BOTTOM_LEFT || corner == ScreenPosition.Corner.BOTTOM_RIGHT;
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

        /**
         * {@return the name of the style in a feed tier document at that
         * fade level: {@code Up} in full, then {@code Up1}, {@code Up2}…}
         */
        public String styleName(int level) {
            return level == 0 ? styleName() : styleName() + level;
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

        /**
         * {@return the name of the tier as the pack spells it, e.g. {@code Gold}}
         *
         * <p>The stem of {@link Denomination#texture()}: the documents sit next
         * to the image and are named after it.</p>
         */
        public String name() {
            String texture = denomination.texture();
            return texture.substring(texture.lastIndexOf('/') + 1, texture.length() - ".png".length());
        }

        /** {@return the document to append, relative to {@code Common/UI/Custom/}} */
        public String document() {
            return PACK_DIR + "/" + name() + ".ui";
        }

        /** {@return the smaller document of the feed, relative to {@code Common/UI/Custom/}} */
        public String feedDocument() {
            return PACK_DIR + "/" + FEED_DIR + "/" + name() + ".ui";
        }

        /** {@return the selector of the coin image, for {@code .Background.Color}} */
        public String iconSelector() {
            return "#" + name() + " #Icon";
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

        /** {@return the count with the sign of a change in front: {@code +2}, {@code -15}} */
        public String signedCountText(boolean gain) {
            return (gain ? "+" : "-") + count;
        }
    }
}
