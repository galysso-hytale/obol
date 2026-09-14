package dev.galysso.obol.api;

import java.util.UUID;

/**
 * Puts an amount of coins on a player's screen, as a HUD overlay.
 *
 * <p>Obtained from {@link ObolApi#display()}. A mod that wants to show a
 * price, a reward or a balance does not need to know anything about the
 * server's UI system: it gives a viewer, a position, what to show and a
 * format, and gets a {@link CoinsOverlay} back to update, move or hide
 * it.</p>
 *
 * <p>A player can have any number of overlays at once, one per call; each
 * is independent. They all vanish when the player disconnects (see
 * {@link CoinsOverlay}).</p>
 *
 * <p>Formats are a closed set because each on-screen format needs a matching
 * template inside Obol. In this version only {@link CoinsFormat#STANDARD} has
 * one; any other format is rejected.</p>
 */
public interface CoinsDisplay {

    /**
     * Shows a fixed amount. The caller changes it through the handle.
     *
     * @param viewer   the player to show it to, who must be connected and in
     *                 a world
     * @param position where to put it
     * @param coins    the amount to show
     * @param format   how to render it; must be a format with an on-screen
     *                 template
     * @return the handle on the overlay
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the viewer is not connected, or
     *                                  the format has no on-screen template;
     *                                  both are bugs of the calling code, not
     *                                  cases to handle
     */
    CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins, CoinsFormat format);

    /**
     * Shows a wallet's balance and keeps it up to date until the overlay is
     * hidden or the viewer disconnects.
     *
     * <p>Every write that goes through Obol is reflected, whatever the
     * storage of the wallet and whoever made the change: a deposit, a
     * transfer, an administrative {@link BalanceStore#set}. The balance is
     * re-read from the wallet on each change, so the overlay never shows a
     * stale value even when changes race.</p>
     *
     * <p>A tracking overlay also lists the wallet's recent changes next to
     * the balance, each for a few seconds: what moved, and in which
     * direction. A fixed overlay ({@link #show}) has no such feed.</p>
     *
     * @param viewer   the player to show it to, who must be connected and in
     *                 a world
     * @param position where to put it
     * @param wallet   the wallet to follow
     * @param format   how to render it; must be a format with an on-screen
     *                 template
     * @return the handle on the overlay
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the viewer is not connected, or
     *                                  the format has no on-screen template
     * @throws IllegalStateException    if the wallet's storage holds a
     *                                  negative value
     */
    CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet, CoinsFormat format);
}
