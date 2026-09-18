package dev.galysso.obol.api;

import dev.galysso.obol.api.event.CoinsChangedEvent;
import dev.galysso.obol.api.internal.ObolBackendHolder;

import java.util.UUID;

/**
 * Entry point of the Obol public API: static, nothing to look up or keep.
 *
 * <p>Declare a manifest dependency so that Obol is loaded before your
 * plugin, then call these methods from anywhere:</p>
 *
 * <pre>{@code
 * "Dependencies": { "Galysso:obol": ">=0.2.0" }
 *
 * if (!Obol.playerWallet(player.getUuid()).withdraw(price)) { ... }
 * }</pre>
 *
 * <p>Every method throws {@link IllegalStateException} if Obol is not
 * loaded, which means a missing or misordered manifest dependency: with the
 * dependency declared, the server guarantees that Obol is up before your
 * plugin's constructor runs, and down after your shutdown.</p>
 */
public final class Obol {

    private Obol() {
    }

    /**
     * {@return the wallet with that identity}
     *
     * <p>Nothing is created or looked up: a wallet is a stateless handle on
     * an entry of Obol's balance store, and an id nobody has written to
     * holds {@link Coins#ZERO}. Build the handle wherever it is needed.</p>
     *
     * @param id the identity, whose {@link WalletId#kind()} is your mod's
     *           namespace
     * @throws NullPointerException  if {@code id} is {@code null}
     * @throws IllegalStateException if Obol is not loaded
     */
    public static Wallet wallet(WalletId id) {
        return ObolBackendHolder.require().wallet(id);
    }

    /**
     * {@return the wallet of that player, {@code wallet(WalletId.player(player))}}
     *
     * @param player the player's UUID
     * @throws NullPointerException  if {@code player} is {@code null}
     * @throws IllegalStateException if Obol is not loaded
     */
    public static Wallet playerWallet(UUID player) {
        return wallet(WalletId.player(player));
    }

    /**
     * Puts a fixed amount on a player's screen, as a HUD overlay. The caller
     * changes it through the handle.
     *
     * <p>A mod that wants to show a price or a reward does not need to know
     * anything about the server's UI system: it gives a viewer, a position
     * and what to show, and gets a {@link CoinsOverlay} back to update or
     * hide it. A player can have any number of overlays at once, one per
     * call; each is independent. They all vanish when the player
     * disconnects (see {@link CoinsOverlay}).</p>
     *
     * @param viewer   the player to show it to, who must be connected and in
     *                 a world
     * @param position where to put it
     * @param coins    the amount to show
     * @return the handle on the overlay
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the viewer is not connected: a bug
     *                                  of the calling code, not a case to
     *                                  handle
     * @throws IllegalStateException    if Obol is not loaded
     */
    public static CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins) {
        return ObolBackendHolder.require().show(viewer, position, coins);
    }

    /**
     * Puts a wallet's balance on a player's screen and keeps it up to date
     * until the overlay is hidden or the viewer disconnects.
     *
     * <p>Every write that goes through Obol is reflected, whoever made the
     * change: a deposit, a transfer, an administrative command. The balance
     * is re-read from the wallet on each change, so the overlay never shows
     * a stale value even when changes race.</p>
     *
     * <p>A tracking overlay also lists the wallet's recent changes next to
     * the balance, each for a few seconds: what moved, and in which
     * direction. A fixed overlay ({@link #show}) has no such feed.</p>
     *
     * @param viewer   the player to show it to, who must be connected and in
     *                 a world
     * @param position where to put it
     * @param wallet   the wallet to follow
     * @return the handle on the overlay
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the viewer is not connected
     * @throws IllegalStateException    if Obol is not loaded
     */
    public static CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet) {
        return ObolBackendHolder.require().track(viewer, position, wallet);
    }

    /**
     * Whether Obol puts its own HUD up: every player's balance, tracked, at
     * the top right of their screen from the moment they are in a world. Up
     * by default.
     *
     * <p>A mod that draws every player's balance itself, in a HUD of its
     * own, takes Obol's down from its {@code setup()}: {@code hud(false)}
     * removes it from the screen of the players in a world and keeps it
     * off the next ones, {@code hud(true)} puts it back for all of them.
     * Server wide, and the last call wins whoever made it: two mods that
     * both draw the balance agree between themselves, Obol keeps no
     * count. The overlays of {@link #show} and {@link #track} are not
     * concerned.</p>
     *
     * @param shown whether Obol's HUD is on the players' screens
     * @throws IllegalStateException if Obol is not loaded
     */
    public static void hud(boolean shown) {
        ObolBackendHolder.require().hud(shown);
    }

    /**
     * Subscribes to balance changes of every wallet.
     *
     * <p>Listeners run synchronously on the thread that moved the money,
     * outside the wallet lock, in subscription order, and receive a
     * {@link CoinsChangedEvent}. A listener that throws is logged and
     * skipped; the others still run and the caller of the wallet operation
     * never sees the exception. Adding the same listener twice calls it
     * twice.</p>
     *
     * @param listener the listener to add
     * @throws NullPointerException  if {@code listener} is {@code null}
     * @throws IllegalStateException if Obol is not loaded
     */
    public static void addListener(CoinsListener listener) {
        ObolBackendHolder.require().addListener(listener);
    }

    /**
     * Removes a previously added listener.
     *
     * <p>A dispatch already in progress on another thread may still deliver
     * one last event to it.</p>
     *
     * @param listener the listener to remove
     * @return {@code true} if it was subscribed
     * @throws IllegalStateException if Obol is not loaded
     */
    public static boolean removeListener(CoinsListener listener) {
        return ObolBackendHolder.require().removeListener(listener);
    }
}
