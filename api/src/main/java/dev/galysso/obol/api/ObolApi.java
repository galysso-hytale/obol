package dev.galysso.obol.api;

import dev.galysso.obol.api.internal.ObolApiHolder;

import java.util.Optional;

/**
 * Entry point of the Obol public API.
 *
 * <p>Third-party plugins obtain the singleton through {@link #get()} once Obol
 * has finished loading. Declare a manifest dependency so ordering is
 * guaranteed:</p>
 *
 * <pre>{@code
 * "Dependencies": { "Galysso:obol": ">=0.1.0" }
 * }</pre>
 *
 * <p>For an optional integration, declare it under
 * {@code OptionalDependencies} and use {@link #find()} instead.</p>
 */
public interface ObolApi {

    /**
     * {@return the running API instance}
     *
     * @throws IllegalStateException if Obol is not loaded, which means a
     *                               missing or misordered manifest dependency
     */
    static ObolApi get() {
        return ObolApiHolder.require();
    }

    /**
     * {@return the running API instance, or empty if Obol is absent}
     */
    static Optional<ObolApi> find() {
        return ObolApiHolder.find();
    }
}
