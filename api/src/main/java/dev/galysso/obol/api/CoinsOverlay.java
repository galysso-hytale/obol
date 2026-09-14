package dev.galysso.obol.api;

/**
 * A handle on an overlay put on a player's screen by {@link CoinsDisplay}.
 *
 * <p>An overlay belongs to the viewer's session: it disappears when they
 * disconnect and is not recreated when they come back. A mod that wants a
 * permanent overlay puts it back on the player's ready event. Once hidden,
 * for either reason, the handle is dead: {@link #update} and {@link #move}
 * do nothing and {@link #isVisible()} stays {@code false}.</p>
 *
 * <p>Every method may be called from any thread. Changes reach the client in
 * the order they were made on this handle.</p>
 */
public interface CoinsOverlay {

    /**
     * Shows another amount.
     *
     * <p>No effect on an overlay created by {@link CoinsDisplay#track}: the
     * wallet's balance is what it shows, nothing else.</p>
     *
     * @param coins the amount to show
     * @throws NullPointerException if {@code coins} is {@code null}
     */
    void update(Coins coins);

    /**
     * Moves the overlay.
     *
     * @param position where to put it
     * @throws NullPointerException if {@code position} is {@code null}
     */
    void move(ScreenPosition position);

    /**
     * Removes the overlay from the screen. Idempotent. Releases the listener
     * of a tracking overlay.
     */
    void hide();

    /**
     * {@return whether the overlay is on the screen}
     *
     * <p>{@code false} once {@link #hide()} was called or the viewer
     * disconnected.</p>
     */
    boolean isVisible();
}
