package dev.galysso.obol.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.galysso.obol.api.CoinsFormat;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

/**
 * {@link OverlayHuds} on the running server: a viewer is a connected player
 * with an entity in a world.
 */
public final class ServerHuds implements OverlayHuds {

    private final HytaleLogger logger;
    private final ScheduledExecutorService scheduler;

    /**
     * @param scheduler what times the frames of a rolling count; the
     *                  server's own executor, shared with everything else
     */
    public ServerHuds(HytaleLogger logger, ScheduledExecutorService scheduler) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public Optional<OverlayHud> open(UUID viewer, String key, CoinsFormat format) {
        PlayerRef player = Universe.get().getPlayer(viewer);
        if (player == null || player.getReference() == null) {
            return Optional.empty();
        }
        return Optional.of(new CoinsHud(player, key, format, logger, scheduler));
    }
}
