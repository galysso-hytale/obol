package dev.galysso.obol.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.galysso.obol.api.CoinsFormat;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link OverlayHuds} on the running server: a viewer is a connected player
 * with an entity in a world.
 */
public final class ServerHuds implements OverlayHuds {

    private final HytaleLogger logger;

    public ServerHuds(HytaleLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Optional<OverlayHud> open(UUID viewer, String key, CoinsFormat format) {
        PlayerRef player = Universe.get().getPlayer(viewer);
        if (player == null || player.getReference() == null) {
            return Optional.empty();
        }
        return Optional.of(new CoinsHud(player, key, format, logger));
    }
}
