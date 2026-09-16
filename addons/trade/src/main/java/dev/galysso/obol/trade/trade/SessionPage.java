package dev.galysso.obol.trade.trade;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * What the three pages of a trade share: the side they belong to, the
 * session they report to, and the rule that dismissing the page, however
 * it happens, tells the session. The session sorts out whether that
 * dismissal means anything (it does not when the page is being replaced
 * by the next one).
 *
 * @param <E> the event the page's bindings send back
 */
abstract class SessionPage<E> extends InteractiveCustomUIPage<E> {

    protected final TradeSession.Side side;
    protected final TradeSession session;

    SessionPage(TradeSession.Side side, TradeSession session, BuilderCodec<E> codec) {
        super(side.player, CustomPageLifetime.CanDismiss, codec);
        this.side = side;
        this.session = session;
    }

    /** {@return the name of the player on the other side} */
    protected String otherName() {
        return session.other(side).name();
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        super.onDismiss(ref, store);
        session.pageClosed(this);
    }

    /**
     * Closes this page if it is still the one the player has open. On the
     * player's world thread.
     */
    void closeIfCurrent(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && player.getPageManager().getCustomPage() == this) {
            close();
        }
    }
}
