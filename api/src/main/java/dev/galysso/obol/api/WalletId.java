package dev.galysso.obol.api;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stable identity of a {@link Wallet}.
 *
 * <p>{@code kind} separates namespaces: Obol's own player wallets use
 * {@link #PLAYER_KIND}, a third-party plugin picks its own kind (for instance
 * {@code "shop"}) and can never collide with anyone else's keys. The identity
 * is what Obol locks on, what Obol's balance store keys entries by, and what
 * it prints in diagnostics.</p>
 *
 * <p>Both parts are restricted to {@code [a-z0-9_-]+} so that
 * {@link #toString()} ({@code kind:key}) is unambiguous and safe to use as a
 * file or JSON key. A {@link UUID#toString() UUID} in its canonical form is
 * a valid key.</p>
 *
 * @param kind the namespace, for instance {@code "player"}
 * @param key  the identifier inside that namespace, unique per wallet
 */
public record WalletId(String kind, String key) {

    /** The {@link #kind()} of every player wallet. */
    public static final String PLAYER_KIND = "player";

    private static final Pattern PART = Pattern.compile("[a-z0-9_-]+");

    /**
     * Validates both parts.
     *
     * @throws NullPointerException     if either part is {@code null}
     * @throws IllegalArgumentException if either part does not match
     *                                  {@code [a-z0-9_-]+}
     */
    public WalletId {
        checkPart("kind", kind);
        checkPart("key", key);
    }

    /**
     * {@return the identity of a player's wallet: {@code player:<uuid>}}
     *
     * @param player the player's UUID
     * @throws NullPointerException if {@code player} is {@code null}
     */
    public static WalletId player(UUID player) {
        return new WalletId(PLAYER_KIND, Objects.requireNonNull(player, "player").toString());
    }

    private static void checkPart(String name, String value) {
        if (value == null) {
            throw new NullPointerException("WalletId " + name + " is null");
        }
        if (!PART.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "WalletId " + name + " must match [a-z0-9_-]+, got \"" + value + "\"");
        }
    }

    /**
     * {@return the flat form {@code kind + ":" + key}, for instance
     * {@code "player:8f0c…"}}
     */
    @Override
    public String toString() {
        return kind + ":" + key;
    }
}
