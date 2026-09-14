package dev.galysso.obol.ui;

import dev.galysso.obol.api.CoinsFormat;

import java.util.Optional;
import java.util.UUID;

/**
 * Where {@link CoinsDisplayImpl} gets an {@link OverlayHud} for a viewer.
 */
public interface OverlayHuds {

    /**
     * Prepares an overlay for a viewer; nothing is shown yet.
     *
     * @param viewer the player
     * @param key    a key unique to this overlay on that player
     * @param format the on-screen format, already known to have a template
     * @return the overlay, or empty if the viewer is not connected and in a
     *         world
     */
    Optional<OverlayHud> open(UUID viewer, String key, CoinsFormat format);
}
