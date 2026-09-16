package dev.galysso.obol.trade.fallback;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionValidation;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * First step of the {@code Obol_Trade_Use} root chain: takes over when the
 * aimed entity is another player, fails otherwise so that the chain carries
 * on with the vanilla unarmed steps.
 *
 * <p>Copied from Hail's {@code HailUseInteraction}: same guards, same
 * verdicts, only the handler differs (Hail opens its menu, this step asks
 * for the trade directly).</p>
 *
 * <p>Every player carries {@code Interactions{Use: Obol_Trade_Use}}, so
 * without this step the vanilla {@code UseEntity} would run the target's
 * root, which is {@code Obol_Trade_Use} again, and so on forever. A player
 * target therefore never leaves this step, whatever happens next: too far
 * away, or busy, still {@link InteractionState#Finished}.
 *
 * <p>The client waits for the server's verdict before picking a branch
 * ({@link WaitForDataFrom#Server}), as {@code GameFlagCondition} does.
 */
public final class TradeUseInteraction extends SimpleInstantInteraction {

    /** Name under {@code "Type"} in the interaction JSON. */
    public static final String TYPE = "ObolTradeUse";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** What happens once a player is confirmed to have used another one. */
    @FunctionalInterface
    public interface Handler {
        /**
         * Called on the world thread, from the actor's interaction chain.
         *
         * @param actor  the player who pressed the key
         * @param target the player aimed at, within interaction range
         * @param buffer the command buffer of the running tick
         */
        void onPlayerUse(PlayerRef actor, PlayerRef target, CommandBuffer<EntityStore> buffer);
    }

    /** The codec's supplier captures the handler: the JSON step has no field. */
    public static BuilderCodec<TradeUseInteraction> codec(Handler handler) {
        return BuilderCodec
                .builder(TradeUseInteraction.class, () -> new TradeUseInteraction(handler), SimpleInstantInteraction.CODEC)
                .documentation("Finishes when the aimed entity is another player and asks them for a trade, fails otherwise.")
                .build();
    }

    private final Handler handler;

    private TradeUseInteraction(Handler handler) {
        this.handler = handler;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        boolean taken;
        try {
            taken = takeOver(context);
        } catch (RuntimeException e) {
            // A step that throws gets the player removed from the world.
            LOGGER.atSevere().withCause(e).log("ObolTradeUse failed; falling back to the vanilla chain");
            taken = false;
        }
        context.getState().state = taken ? InteractionState.Finished : InteractionState.Failed;
    }

    /** True when the aimed entity is another player; the vanilla chain must not see them. */
    private boolean takeOver(InteractionContext context) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> actor = context.getEntity();
        // This step runs on the first server tick of the chain, before any
        // per-step client data exists (getClientState() is still null there,
        // UseEntity only reads it because it comes later in the chain). The
        // aimed entity is what InteractionManager.syncStart put in the meta
        // store from the chain packet.
        Ref<EntityStore> target = context.getTargetEntity();
        if (target == null || !target.isValid() || target.getIndex() == actor.getIndex()) {
            return false;
        }
        PlayerRef targetRef = buffer.getComponent(target, PlayerRef.getComponentType());
        if (targetRef == null) {
            return false;
        }
        PlayerRef actorRef = buffer.getComponent(actor, PlayerRef.getComponentType());
        if (actorRef != null
                && InteractionValidation.canPlayerInteractWithEntity(actor, buffer, context.getHeldItem(), target)) {
            handler.onPlayerUse(actorRef, targetRef, buffer);
        }
        return true;
    }

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }
}
