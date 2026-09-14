package dev.galysso.obol.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.api.ScreenPosition;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One overlay on one player's screen, as a server {@code CustomUIHud}.
 *
 * <p>Two kinds of calls reach the server here. Packets ({@code update}) can
 * be written from any thread and are sent at once. The player's
 * {@code HudManager}, on the other hand, is plain state of the player
 * entity and is only touched on the player's world thread; adding and
 * removing the HUD are therefore scheduled there. Until the add has run,
 * amount and position changes are folded into the initial document, so that
 * nothing is sent about a document the client does not have yet.</p>
 *
 * <p>A change of amount is not shown at once: the counts roll from the
 * amount on screen to the new one over the frames of {@link Tween}, tinted
 * green or red meanwhile ({@link HudTemplates.Tint}), and take their own
 * colour back a moment after settling. A change that arrives during a roll
 * retargets it from the frame on screen, so the number never jumps. As
 * long as the roll keeps the pill's shape — the same tiers — a frame only
 * patches the counts and their styles; when a tier appears or goes, the
 * document is rebuilt, which is cheaper than editing it for a handful of
 * elements.</p>
 */
final class CoinsHud extends CustomUIHud implements OverlayHud {

    private final HytaleLogger logger;
    private final ScheduledExecutorService scheduler;
    private final CoinsFormat format;
    private ScreenPosition position;
    /** Where the counts are heading. */
    private Coins target = Coins.ZERO;
    /** The last frame sent: what the client shows. */
    private Coins displayed = Coins.ZERO;
    /** The tiers of the pill the client holds, largest first. */
    private List<Denomination> shape = List.of();
    /** The counts tinted at the moment, and how. */
    private Map<Denomination, HudTemplates.Tint> tints = Map.of();
    /** The frames still to send of the roll under way. */
    private final Deque<Coins> frames = new ArrayDeque<>();
    /** The timer sending the frames, while a roll is under way. */
    private ScheduledFuture<?> roll;
    /** The timer taking the tints off, once the roll has settled. */
    private ScheduledFuture<?> settle;
    /** The client has the document: changes are sent as they come. */
    private boolean shown;
    /** {@link #hide()} was called: nothing is sent any more. */
    private boolean hidden;

    CoinsHud(@Nonnull PlayerRef playerRef, @Nonnull String key, CoinsFormat format,
             HytaleLogger logger, ScheduledExecutorService scheduler) {
        super(playerRef, key);
        this.format = format;
        this.logger = logger;
        this.scheduler = scheduler;
    }

    @Override
    protected synchronized void build(UICommandBuilder builder) {
        builder.appendInline(null, HudTemplates.document(format, position));
        List<HudTemplates.Tier> tiers = HudTemplates.tiers(displayed);
        for (HudTemplates.Tier tier : tiers) {
            builder.append(HudTemplates.PILL, tier.document());
            patch(builder, tier, true);
        }
        shape = tiers.stream().map(HudTemplates.Tier::denomination).toList();
    }

    @Override
    public synchronized void show(ScreenPosition position, Coins coins) {
        this.position = position;
        this.target = coins;
        this.displayed = coins;
        onWorldThread(player -> {
            synchronized (this) {
                if (hidden) {
                    return;
                }
                // addCustomHud calls show(), which builds and sends.
                player.getHudManager().addCustomHud(getPlayerRef(), this);
                shown = true;
            }
        });
    }

    @Override
    public synchronized void setCoins(Coins coins) {
        target = coins;
        if (!shown) {
            // Folded into the initial document.
            displayed = coins;
            return;
        }
        if (hidden) {
            return;
        }
        frames.clear();
        frames.addAll(Tween.frames(displayed, target));
        cancel(settle);
        if (frames.isEmpty()) {
            // Back where the screen already is (a roll undone before its
            // first frame): nothing to roll, only tints to take off.
            if (!tints.isEmpty()) {
                settle = scheduler.schedule(this::settle, Tween.HOLD_MS, TimeUnit.MILLISECONDS);
            }
            return;
        }
        tints = HudTemplates.changed(displayed, target);
        if (roll == null || roll.isDone()) {
            roll = scheduler.scheduleAtFixedRate(this::frame, 0, Tween.STEP_MS, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public synchronized void move(ScreenPosition position) {
        this.position = position;
        resend();
    }

    @Override
    public synchronized void hide() {
        if (hidden) {
            return;
        }
        hidden = true;
        cancel(roll);
        cancel(settle);
        if (shown) {
            onWorldThread(player -> player.getHudManager().removeCustomHud(getPlayerRef(), getKey()));
        }
    }

    /** One tick of the roll: the next frame, then the settling once it was the last. */
    private synchronized void frame() {
        try {
            Coins next = gone() ? null : frames.poll();
            if (next == null) {
                cancel(roll);
                return;
            }
            render(next);
            if (frames.isEmpty()) {
                cancel(roll);
                settle = scheduler.schedule(this::settle, Tween.HOLD_MS, TimeUnit.MILLISECONDS);
            }
        } catch (RuntimeException e) {
            // A timer task that throws is silently dropped by the executor.
            logger.atWarning().withCause(e).log("HUD %s of %s stopped rolling", getKey(), getPlayerRef().getUuid());
            cancel(roll);
        }
    }

    /** The roll has settled: the counts take their own colour back. */
    private synchronized void settle() {
        tints = Map.of();
        if (!gone()) {
            render(displayed);
        }
    }

    /**
     * Puts {@code coins} on the screen: a patch of the counts when the pill
     * keeps its tiers, a rebuild otherwise.
     */
    private void render(Coins coins) {
        displayed = coins;
        List<HudTemplates.Tier> tiers = HudTemplates.tiers(coins);
        UICommandBuilder builder = new UICommandBuilder();
        if (tiers.stream().map(HudTemplates.Tier::denomination).toList().equals(shape)) {
            tiers.forEach(tier -> patch(builder, tier, false));
            send(false, builder);
        } else {
            build(builder);
            send(true, builder);
        }
    }

    /**
     * The count and the style of one tier already in the document. A fresh
     * document starts in the normal style on its own; it is only named when
     * a count that was tinted has to go back.
     */
    private void patch(UICommandBuilder builder, HudTemplates.Tier tier, boolean fresh) {
        HudTemplates.Tint tint = tints.getOrDefault(tier.denomination(), HudTemplates.Tint.NORMAL);
        builder.set(tier.countSelector(), tier.countText());
        if (tint != HudTemplates.Tint.NORMAL || !fresh) {
            builder.set(tier.styleSelector(), Value.ref(tier.document(), tint.styleName()));
        }
    }

    /**
     * Same path as the first display: the document is rebuilt from scratch,
     * the client dropping the previous one first.
     */
    private void resend() {
        if (shown && !hidden) {
            UICommandBuilder builder = new UICommandBuilder();
            build(builder);
            send(true, builder);
        }
    }

    /** A packet to a player who just left is not worth a stack trace. */
    private void send(boolean clear, UICommandBuilder builder) {
        try {
            update(clear, builder);
        } catch (RuntimeException e) {
            logger.atWarning().withCause(e).log("Could not update HUD %s of %s", getKey(), getPlayerRef().getUuid());
        }
    }

    /**
     * Whether there is no screen to send to any more: hidden, or the player
     * left. The display forgets a viewer's overlays on disconnect without
     * hiding them, so a timer still running has to notice on its own.
     */
    private boolean gone() {
        return hidden || getPlayerRef().getReference() == null;
    }

    private static void cancel(ScheduledFuture<?> timer) {
        if (timer != null) {
            timer.cancel(false);
        }
    }

    /**
     * Runs {@code action} with the player entity on its world thread, or
     * not at all if the player is no longer in a world: the HUD dies with
     * the session anyway.
     */
    private void onWorldThread(Consumer<Player> action) {
        PlayerRef playerRef = getPlayerRef();
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        try {
            world.execute(() -> {
                Ref<EntityStore> now = playerRef.getReference();
                if (now == null) {
                    return;
                }
                if (now.getStore() != store) {
                    // Changed world meanwhile: the entity belongs to another
                    // thread now. Follow it.
                    onWorldThread(action);
                    return;
                }
                Player player = store.getComponent(now, Player.getComponentType());
                if (player != null) {
                    action.accept(player);
                }
            });
        } catch (RuntimeException e) {
            // The world refuses tasks while shutting down.
            logger.atWarning().withCause(e).log("Could not reach the world of %s for HUD %s", playerRef.getUuid(), getKey());
        }
    }
}
