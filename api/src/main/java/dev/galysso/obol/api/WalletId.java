package dev.galysso.obol.api;

import java.util.regex.Pattern;

/**
 * Stable identity of a {@link Wallet}.
 *
 * <p>{@code kind} separates namespaces: Obol's own player wallets use
 * {@code "player"}, a third-party plugin picks its own kind (for instance
 * {@code "shop"}) and can never collide with anyone else's keys. The identity
 * is what Obol locks on, what Obol's balance store keys entries by, and what it
 * prints in diagnostics.</p>
 *
 * <p>Both parts are restricted to {@code [a-z0-9_-]+} so that
 * {@link #storageKey()} is unambiguous and safe to use as a file or JSON key.
 * A {@link java.util.UUID#toString() UUID} in its canonical form is a valid
 * key.</p>
 *
 * @param kind the namespace, for instance {@code "player"}
 * @param key  the identifier inside that namespace, unique per wallet
 */
public record WalletId(String kind, String key) {

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
     *
     * <p>This is the form used as a storage key and as the global lock order
     * in transfers. {@link #parse(String)} reverses it.</p>
     */
    public String storageKey() {
        return kind + ":" + key;
    }

    /**
     * Parses the form produced by {@link #storageKey()}.
     *
     * @param storageKey text of the form {@code kind:key}
     * @return the identity
     * @throws IllegalArgumentException if the text has no {@code ':'} or if
     *                                  either part is invalid
     */
    public static WalletId parse(String storageKey) {
        int colon = storageKey.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException(
                    "Storage key must be of the form kind:key, got \"" + storageKey + "\"");
        }
        return new WalletId(storageKey.substring(0, colon), storageKey.substring(colon + 1));
    }

    @Override
    public String toString() {
        return storageKey();
    }
}
