package dev.galysso.obol.api;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.galysso.obol.api.internal.ObolBackendHolder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Coins drawn inside a page of your own, the way the HUD draws them: for
 * each tier, largest first, the count then the coin, in the coin's colour.
 * And, for the places that hold text alone, coins written in words in the
 * same colours ({@link #message}).
 *
 * <p>A page in Hytale is a tree of groups built by sending commands to a
 * {@link UICommandBuilder}; a mod's {@code CustomUIPage.build} receives
 * one. Give this class the builder and the selector of an empty group of
 * your document, and it fills the group:</p>
 *
 * <pre>{@code
 * public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
 *     commands.append("MyShop/Page.ui");
 *     for (Article article : articles) {
 *         ObolUi.show(commands, "#" + article.id() + " #Price", article.price());
 *     }
 * }
 * }</pre>
 *
 * <p>The group sizes itself to its content in width; its height is yours
 * (the coins are 24 pixels high). The tiers shown follow the HUD's rule:
 * tiers above the largest one holding coins are left out, every tier below
 * it is shown even at zero ({@code 2 [gold] 0 [silver] 5 [copper]} for
 * {@code 2g 5c}), and zero is {@code 0 [copper]}. The pictures and colours
 * are Obol's, so a price looks the same in every mod and in the HUD.</p>
 *
 * <p>The group is filled once; a page rebuilt from scratch calls this
 * again. To change an amount in place, {@link UICommandBuilder#clear clear}
 * the group first. The methods only queue commands and may be called from
 * wherever the builder may be used.</p>
 *
 * <p>This is the one class of the API that names a server type. The others
 * compile against the JDK alone.</p>
 */
public final class ObolUi {

    /** The id of the row put into the caller's group. */
    private static final String ROW = "#ObolCoins";
    /** Directory of the tier documents in Obol's asset pack, under {@code Common/UI/Custom/}. */
    private static final String DOCUMENTS = "Obol/Coins/";
    /** Digits a count column holds without growing. */
    private static final int COLUMN_DIGITS = 2;
    /** Width of one digit at the counts' font size, in UI pixels. */
    private static final int DIGIT_WIDTH = 15;

    private ObolUi() {
    }

    /**
     * Fills {@code selector} with {@code coins}, centred.
     *
     * @param builder  the page's command builder
     * @param selector an empty group of the page, e.g. {@code "#Row2 #Cell3 #Price"}
     * @param coins    the amount to show
     * @throws NullPointerException  if an argument is {@code null}
     * @throws IllegalStateException if Obol is not loaded
     */
    public static void show(UICommandBuilder builder, String selector, Coins coins) {
        show(builder, selector, coins, CoinsStyle.DEFAULT);
    }

    /**
     * Fills {@code selector} with {@code coins}, laid out as {@code style}
     * says.
     *
     * @throws NullPointerException  if an argument is {@code null}
     * @throws IllegalStateException if Obol is not loaded
     */
    public static void show(UICommandBuilder builder, String selector, Coins coins, CoinsStyle style) {
        Objects.requireNonNull(coins, "coins");
        String row = row(builder, selector, style);
        EnumMap<Denomination, Long> parts = coins.breakdown();
        Denomination[] all = Denomination.values();
        boolean started = false;
        for (int i = all.length - 1; i >= 0; i--) {
            long count = parts.get(all[i]);
            if (count != 0 || started || i == 0) {
                tier(builder, row, all[i], count);
                started = true;
            }
        }
    }

    /**
     * Fills {@code selector} with one tier, {@code count} coins of it,
     * centred: {@code 35 [silver]}. Shown even at zero. For a page that
     * lays tiers out itself, one per line or per column.
     *
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if {@code count} is negative
     * @throws IllegalStateException    if Obol is not loaded
     */
    public static void show(UICommandBuilder builder, String selector, Denomination tier, long count) {
        show(builder, selector, tier, count, CoinsStyle.DEFAULT);
    }

    /**
     * Fills {@code selector} with one tier, laid out as {@code style} says.
     *
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if {@code count} is negative
     * @throws IllegalStateException    if Obol is not loaded
     */
    public static void show(UICommandBuilder builder, String selector, Denomination tier, long count, CoinsStyle style) {
        Objects.requireNonNull(tier, "tier");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        tier(builder, row(builder, selector, style), tier, count);
    }

    /**
     * {@code coins} in words, for a place that holds text alone (a tooltip,
     * a chat line, a notification): for each tier, largest first, the count
     * and the tier's name in the tier's colour, {@code 2 gold 35 silver} for
     * {@code 2g 35s}. What {@link Coins#toString()} writes, the name in
     * place of the letter: tiers with no coins are left out, zero is
     * {@code 0 copper}. Hytale draws no picture inside a text, so this is
     * how an amount looks where {@link #show} cannot go.
     *
     * <p>A {@link Message} goes wherever the server takes one: a chat line,
     * a notification, the {@code TextSpans} of a label, a parameter of a
     * translated sentence ({@code "Costs {price} per piece"}). Each tier is
     * its own coloured span, so the colour of the sentence around it stays
     * on the words around it.</p>
     *
     * @param coins the amount to write
     * @throws NullPointerException if {@code coins} is {@code null}
     */
    public static Message message(Coins coins) {
        Objects.requireNonNull(coins, "coins");
        List<Message> parts = new ArrayList<>();
        EnumMap<Denomination, Long> breakdown = coins.breakdown();
        Denomination[] all = Denomination.values();
        for (int i = all.length - 1; i >= 0; i--) {
            long count = breakdown.get(all[i]);
            if (count == 0 && !(i == 0 && parts.isEmpty())) {
                continue;
            }
            String words = (parts.isEmpty() ? "" : " ") + count + " " + all[i].name().toLowerCase(Locale.ROOT);
            parts.add(Message.raw(words).color(all[i].color()));
        }
        return Message.join(parts.toArray(Message[]::new));
    }

    /** Puts the row into the caller's group and returns its selector. */
    private static String row(UICommandBuilder builder, String selector, CoinsStyle style) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(style, "style");
        ObolBackendHolder.require();
        String layout = switch (style.alignment()) {
            case START -> "Left";
            case CENTER -> "Center";
            case END -> "Right";
        };
        builder.appendInline(selector, "Group " + ROW + " { LayoutMode: " + layout + "; }");
        return selector + " " + ROW;
    }

    /** Appends one tier's document into the row and sets its count. */
    private static void tier(UICommandBuilder builder, String row, Denomination tier, long count) {
        builder.append(row, DOCUMENTS + tier.fileName() + ".ui");
        String countSelector = row + " #" + tier.fileName() + " #Count";
        String digits = Long.toString(count);
        builder.set(countSelector + ".Text", digits);
        if (digits.length() > COLUMN_DIGITS) {
            // The document's column is sized for two digits; widen it to fit.
            Anchor anchor = new Anchor();
            anchor.setWidth(Value.of(digits.length() * DIGIT_WIDTH));
            builder.setObject(countSelector + ".Anchor", anchor);
        }
    }
}
