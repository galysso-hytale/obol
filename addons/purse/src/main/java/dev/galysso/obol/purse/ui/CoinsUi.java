package dev.galysso.obol.purse.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;

import java.util.EnumMap;

/**
 * Puts an amount into a page: one tier document per tier, largest first,
 * appended into a group of the page.
 *
 * <p>The documents are this add-on's ({@code ObolPurse/<Tier>.ui}), each
 * showing Obol's coin image next to a count. The convention is that of
 * fixed-base currencies: tiers above the largest one that has coins are
 * left out, every tier below it is shown even at zero, so {@code 2g 0s 5c}
 * reads as such. Zero is the copper tier alone.</p>
 */
final class CoinsUi {

    private static final String DIR = "ObolPurse/";

    private CoinsUi() {
    }

    /**
     * Appends the tiers of {@code coins} into the element at {@code selector}.
     */
    static void coins(UICommandBuilder builder, String selector, Coins coins) {
        EnumMap<Denomination, Long> parts = coins.breakdown();
        Denomination[] all = Denomination.values();
        boolean started = false;
        for (int i = all.length - 1; i >= 0; i--) {
            long count = parts.get(all[i]);
            if (count != 0 || started || i == 0) {
                started = true;
                String name = name(all[i]);
                builder.append(selector, DIR + name + ".ui");
                builder.set(selector + " #" + name + " #Count.Text", Long.toString(count));
            }
        }
    }

    /**
     * {@return the name of the tier as the documents spell it, e.g.
     * {@code Gold}: the stem of {@link Denomination#texture()}}
     */
    private static String name(Denomination denomination) {
        String texture = denomination.texture();
        return texture.substring(texture.lastIndexOf('/') + 1, texture.length() - ".png".length());
    }
}
