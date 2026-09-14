package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.event.CoinsChangedEvent;

/**
 * One overlay as the server sees it: a document on a player's screen whose
 * amount and placement can change.
 *
 * <p>The seam between {@link CoinsDisplayImpl}, which is plain JDK code and
 * unit-tested, and {@link CoinsHud}, which talks to the server. Calls may
 * come from any thread and must be applied in the order they are made.</p>
 */
public interface OverlayHud {

    /** Puts the document on the screen. Called once, first. */
    void show(ScreenPosition position, Coins coins);

    /** Changes the amount shown. */
    void setCoins(Coins coins);

    /**
     * Adds a change of the followed wallet to the overlay's feed of recent
     * changes. Only a tracking overlay calls it, after {@link #setCoins}.
     */
    void log(CoinsChangedEvent change);

    /** Moves the document. */
    void move(ScreenPosition position);

    /** Takes the document off the screen. Nothing is called afterwards. */
    void hide();
}
