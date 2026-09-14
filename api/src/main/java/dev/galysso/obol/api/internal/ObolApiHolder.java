package dev.galysso.obol.api.internal;

import dev.galysso.obol.api.ObolApi;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wiring between the API and its implementation.
 *
 * @apiNote Not part of the public API. It is only {@code public} because the
 *          implementation lives in another package. Third-party plugins must
 *          go through {@link ObolApi#get()} and never call
 *          {@link #install(ObolApi)}.
 */
public final class ObolApiHolder {

    private static final AtomicReference<ObolApi> INSTANCE = new AtomicReference<>();

    private ObolApiHolder() {
    }

    /**
     * Publishes the implementation. Called once by Obol itself.
     *
     * @param api the implementation
     * @throws IllegalStateException if an instance is already installed
     */
    public static void install(ObolApi api) {
        if (!INSTANCE.compareAndSet(null, api)) {
            throw new IllegalStateException("Obol API is already installed");
        }
    }

    /**
     * Withdraws the implementation, on plugin shutdown or reload.
     */
    public static void uninstall() {
        INSTANCE.set(null);
    }

    /**
     * {@return the installed implementation}
     *
     * @throws IllegalStateException if none is installed
     */
    public static ObolApi require() {
        ObolApi api = INSTANCE.get();
        if (api == null) {
            throw new IllegalStateException(
                    "Obol is not loaded. Declare \"Galysso:obol\" "
                            + "in your manifest Dependencies.");
        }
        return api;
    }

    /**
     * {@return the installed implementation, or empty if none is installed}
     */
    public static Optional<ObolApi> find() {
        return Optional.ofNullable(INSTANCE.get());
    }
}
