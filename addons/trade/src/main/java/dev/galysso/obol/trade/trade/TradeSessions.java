package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.galysso.obol.trade.TradeConfig;

import java.util.Optional;
import java.util.UUID;

/**
 * The trades in flight, and the one entry point both hooks call when a
 * player asks another for a trade: Hail's menu entry ({@code HailBridge})
 * and, without Hail, the F step on a player ({@code TradeUseInteraction}).
 *
 * <p>Both callers hand over two online players on the actor's world thread.
 * For now the request only tells both players: the sessions, the pages and
 * the checks of the plan's §5 come with the next increments.</p>
 */
public final class TradeSessions {

    private final HytaleLogger logger;
    private final TradeConfig config;

    public TradeSessions(HytaleLogger logger, TradeConfig config) {
        this.logger = logger;
        this.config = config;
    }

    /**
     * Why {@code actor} may not ask {@code target} right now, or empty when
     * they may. Cheap and side-effect free: Hail calls it when it builds its
     * menu and again on the click. Distance and presence are not checked
     * here, the caller does that before {@link #request}.
     */
    public Optional<String> refusal(UUID actor, UUID target) {
        return Optional.empty();
    }

    /** {@code actor} asks {@code target} for a trade. */
    public void request(PlayerRef actor, PlayerRef target) {
        Optional<String> refusal = refusal(actor.getUuid(), target.getUuid());
        if (refusal.isPresent()) {
            actor.sendMessage(Message.raw(refusal.get()));
            return;
        }
        actor.sendMessage(Message.raw("You ask %s for a trade.".formatted(target.getUsername())));
        target.sendMessage(Message.raw("%s asks you for a trade.".formatted(actor.getUsername())));
        logger.atInfo().log("%s asks %s for a trade (timeout %d s, max distance %.1f)",
                actor.getUsername(), target.getUsername(),
                config.requestTimeoutSeconds(), config.maxDistance());
    }
}
