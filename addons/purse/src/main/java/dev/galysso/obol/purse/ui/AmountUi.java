package dev.galysso.obol.purse.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.purse.api.PurseItem;

import java.util.Map;

/**
 * Shows an amount as coins in a page: {@code Amount.ui} (for each tier
 * the count then the coin, in the coin's colour, laid out like the HUD's
 * balance pill) appended into a group, the tiers at zero removed.
 */
final class AmountUi {

    private static final String DOCUMENT = "ObolPurse/Amount.ui";

    private AmountUi() {
    }

    /**
     * Fills {@code selector} (an empty group of the page) with
     * {@code amount}.
     */
    static void show(UICommandBuilder commands, String selector, Coins amount) {
        commands.append(selector, DOCUMENT);
        Map<Denomination, Long> counts = amount.breakdown();
        for (Denomination tier : Denomination.values()) {
            long count = counts.get(tier);
            String row = selector + " #" + PurseItem.stateName(tier);
            if (count > 0) {
                commands.set(row + " #Count.Text", Long.toString(count));
            } else {
                // Hidden, the group would keep its room in the row.
                commands.remove(row);
            }
        }
    }
}
