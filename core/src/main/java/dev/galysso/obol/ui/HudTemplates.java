package dev.galysso.obol.ui;

import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.ScreenPosition;

import java.util.Map;

/**
 * The UI documents behind each on-screen {@link CoinsFormat}, and their
 * placement.
 *
 * <p>Documents are inline UI markup sent with the HUD, the way the server's
 * own spectating HUD does it: no asset pack to ship, nothing to load on the
 * client side. The amount is not part of the template; it is set after the
 * document is appended, through the {@code #Amount} label, so no text ever
 * needs escaping into markup.</p>
 */
public final class HudTemplates {

    /** Selector of the label that carries the amount, for {@code set}. */
    public static final String AMOUNT_TEXT = "#Amount.Text";

    private static final String LAYOUT = "%LAYOUT%";
    private static final String ANCHOR = "%ANCHOR%";

    /**
     * {@link CoinsFormat#STANDARD}: a translucent pill with the amount as
     * text. The outer group spans the screen edge and packs the pill against
     * the requested corner; the pill sizes itself to its text.
     */
    private static final String STANDARD = """
            Group {
              LayoutMode: %LAYOUT%;
              Anchor: (%ANCHOR%, Height: 36);

              Group {
                Background: #000000(0.35);
                LayoutMode: CenterMiddle;
                Padding: (Horizontal: 14);

                Label #Amount {
                  Text: "0c";
                  Style: (FontSize: 22, TextColor: #f2d16b, RenderBold: true);
                }
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
     * {@return the document for that format, placed at that position}
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
     * The client anchors an element by its distance from the edges it names:
     * {@code TOP_RIGHT(20, 10)} is {@code Top: 10, Right: 20}.
     */
    static String anchor(ScreenPosition position) {
        ScreenPosition.Corner corner = position.corner();
        return (corner.isBottom() ? "Bottom: " : "Top: ") + position.offsetY()
                + ", " + (corner.isRight() ? "Right: " : "Left: ") + position.offsetX();
    }
}
