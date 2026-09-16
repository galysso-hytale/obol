package dev.galysso.obol.trade.hail;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.galysso.hail.api.Availability;
import dev.galysso.hail.api.Encounter;
import dev.galysso.hail.api.HailApi;
import dev.galysso.hail.api.PlayerInteraction;
import dev.galysso.hail.api.Registration;
import dev.galysso.obol.trade.trade.TradeSessions;

import java.util.Optional;

/**
 * The one class that names Hail's API. {@code TradePlugin} only calls it
 * after {@code hasPlugin} said Hail is loaded, so these types resolve. With
 * Hail absent this class is never loaded and its imports never looked up.
 *
 * <p>Registers {@code obol:trade} in Hail's menu. The availability and the
 * action defer to {@link TradeSessions}, which is Hail-agnostic.</p>
 */
public final class HailBridge {

    /** The menu entry, as Hail lists it. */
    public static final String ID = "obol:trade";
    /** Above Hail's own examples (Wave is 10), below nothing in particular. */
    private static final int PRIORITY = 20;

    private HailBridge() {
    }

    /**
     * Puts Trade in Hail's menu. Returns what undoes it, or null when Hail's
     * API is not installed after all (a Hail that failed to construct), so
     * that the caller falls back to its own hook.
     */
    public static AutoCloseable register(TradeSessions sessions, HytaleLogger logger) {
        Optional<HailApi> api = HailApi.find();
        if (api.isEmpty()) {
            logger.atWarning().log("Hail is loaded but its API is not installed, falling back to the direct hook");
            return null;
        }
        Registration registration = api.get().registry().register(
                PlayerInteraction.builder(ID, "Trade")
                        .priority(PRIORITY)
                        .availability(e -> availability(sessions, e))
                        .perform(e -> perform(sessions, e)));
        return registration::close;
    }

    private static Availability availability(TradeSessions sessions, Encounter encounter) {
        return sessions.refusal(encounter.actor(), encounter.target())
                .<Availability>map(Availability::disabled)
                .orElse(Availability.AVAILABLE);
    }

    /**
     * On the actor's world thread, from the menu's click. Hail has just
     * checked that both are online, in the same world and within reach.
     */
    private static void perform(TradeSessions sessions, Encounter encounter) {
        Universe universe = Universe.get();
        PlayerRef actor = universe.getPlayer(encounter.actor());
        PlayerRef target = universe.getPlayer(encounter.target());
        if (actor == null || target == null) {
            return;
        }
        sessions.request(actor, target);
    }
}
