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

    private record Installed(ObolApi api, ObolRuntime runtime) {
    }

    private static final AtomicReference<Installed> INSTANCE = new AtomicReference<>();

    private ObolApiHolder() {
    }

    /**
     * Publishes the implementation. Called once by Obol itself.
     *
     * <p>The single object serves both faces: the public {@link ObolApi} and
     * the {@link ObolRuntime} that {@code Wallet} relies on.</p>
     *
     * @param <T>  the implementation type
     * @param impl the implementation
     * @throws IllegalStateException if an instance is already installed
     */
    public static <T extends ObolApi & ObolRuntime> void install(T impl) {
        if (!INSTANCE.compareAndSet(null, new Installed(impl, impl))) {
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
        return installed().api();
    }

    /**
     * {@return the runtime side of the installed implementation}
     *
     * @throws IllegalStateException if none is installed
     */
    public static ObolRuntime runtime() {
        return installed().runtime();
    }

    /**
     * {@return the installed implementation, or empty if none is installed}
     */
    public static Optional<ObolApi> find() {
        return Optional.ofNullable(INSTANCE.get()).map(Installed::api);
    }

    private static Installed installed() {
        Installed installed = INSTANCE.get();
        if (installed == null) {
            throw new IllegalStateException(
                    "Obol is not loaded. Declare \"Galysso:obol\" "
                            + "in your manifest Dependencies.");
        }
        return installed;
    }
}
