package dev.galysso.obol.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.ScreenPosition;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * One overlay on one player's screen, as a server {@code CustomUIHud}.
 *
 * <p>Two kinds of calls reach the server here. Packets ({@code update}) can
 * be written from any thread and are sent at once. The player's
 * {@code HudManager}, on the other hand, is plain state of the player
 * entity and is only touched on the player's world thread; adding and
 * removing the HUD are therefore scheduled there. Until the add has run,
 * text and position changes are folded into the initial document, so that
 * nothing is sent about a document the client does not have yet.</p>
 */
final class CoinsHud extends CustomUIHud implements OverlayHud {

    private final HytaleLogger logger;
    private final CoinsFormat format;
    private ScreenPosition position;
    private String text = "";
    /** The client has the document: changes are sent as they come. */
    private boolean shown;
    /** {@link #hide()} was called: nothing is sent any more. */
    private boolean hidden;

    CoinsHud(@Nonnull PlayerRef playerRef, @Nonnull String key, CoinsFormat format, HytaleLogger logger) {
        super(playerRef, key);
        this.format = format;
        this.logger = logger;
    }

    @Override
    protected synchronized void build(UICommandBuilder builder) {
        builder.appendInline(null, HudTemplates.document(format, position));
        builder.set(HudTemplates.AMOUNT_TEXT, text);
    }

    @Override
    public synchronized void show(ScreenPosition position, String text) {
        this.position = position;
        this.text = text;
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
    public synchronized void setText(String text) {
        this.text = text;
        if (shown && !hidden) {
            send(false, new UICommandBuilder().set(HudTemplates.AMOUNT_TEXT, text));
        }
    }

    @Override
    public synchronized void move(ScreenPosition position) {
        this.position = position;
        if (shown && !hidden) {
            // Same path as the first display: the document is rebuilt from
            // scratch, the client dropping the previous one first.
            UICommandBuilder builder = new UICommandBuilder();
            build(builder);
            send(true, builder);
        }
    }

    @Override
    public synchronized void hide() {
        if (hidden) {
            return;
        }
        hidden = true;
        if (shown) {
            onWorldThread(player -> player.getHudManager().removeCustomHud(getPlayerRef(), getKey()));
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
