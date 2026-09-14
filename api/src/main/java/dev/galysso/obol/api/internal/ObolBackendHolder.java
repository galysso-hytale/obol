package dev.galysso.obol.api.internal;

import dev.galysso.obol.api.Obol;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wiring between the static facade {@link Obol} and its implementation.
 *
 * <p>Resolved on every call of the facade, so that a backend withdrawn by a
 * plugin shutdown or reload makes the next call fail instead of talking to
 * a dead instance.</p>
 *
 * @apiNote Not part of the public API. It is only {@code public} because the
 *          implementation lives in another package. Third-party plugins must
 *          never call {@link #install} at runtime; their unit tests may, to
 *          run against a fake backend without a server.
 */
public final class ObolBackendHolder {

    private static final AtomicReference<ObolBackend> INSTANCE = new AtomicReference<>();

    private ObolBackendHolder() {
    }

    /**
     * Publishes the implementation. Called once by Obol itself.
     *
     * @param backend the implementation
     * @throws NullPointerException  if {@code backend} is {@code null}
     * @throws IllegalStateException if one is already installed
     */
    public static void install(ObolBackend backend) {
        Objects.requireNonNull(backend, "backend");
        if (!INSTANCE.compareAndSet(null, backend)) {
            throw new IllegalStateException("Obol is already installed");
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
    public static ObolBackend require() {
        ObolBackend backend = INSTANCE.get();
        if (backend == null) {
            throw new IllegalStateException(
                    "Obol is not loaded. Declare \"Galysso:obol\" "
                            + "in your manifest Dependencies.");
        }
        return backend;
    }
}
