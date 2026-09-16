package dev.galysso.obol.trade.fallback;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Makes every player a target for F: the {@link Interactable} marker gives
 * the other clients their "Press [F] to trade" prompt, and the
 * {@link Interactions} entry names the root chain the server runs.
 *
 * <p>Copied from Hail's {@code PlayerInteractable}, which owns this gesture.
 * Only the root and the hint differ. Any fix Hail finds in game belongs
 * here too.</p>
 *
 * <p>That same {@code Interactions} component is also what the server reads
 * first for the player's <em>own</em> key presses, so {@code Obol_Trade_Use}
 * must behave like the vanilla unarmed chain whenever the target is not a
 * player. See {@code Server/Item/RootInteractions/Obol/Obol_Trade_Use.json}.
 *
 * <p>The component is saved with the player. A saved {@code Use: Obol_Trade_Use}
 * loaded on a server without this add-on resolves to an empty root
 * interaction and that player's F does nothing, so {@link #remove} undoes
 * {@link #apply} whenever the entry would outlive the plugin: when the
 * player leaves a world (just before the save) and at shutdown.
 */
public final class PlayerInteractable {

    /** Root interaction asset, {@code Server/Item/RootInteractions/Obol/Obol_Trade_Use.json}. */
    public static final String ROOT_INTERACTION = "Obol_Trade_Use";

    /** Translation key of the prompt, a vanilla one: "Press [{key}] to trade". */
    public static final String HINT = "server.interactionHints.trade";

    private PlayerInteractable() {
    }

    /**
     * Puts the components on a player about to enter a world, before the
     * entity tracker announces it to the other clients.
     *
     * <p>Merges into an existing {@code Interactions} rather than replacing
     * it: the game mode state keeps its own entries in that component and
     * removes only those on the way out. Idempotent, so a player whose saved
     * data already carries the entry is left as is.
     */
    public static void apply(Holder<EntityStore> holder) {
        Interactions interactions = holder.ensureAndGetComponent(Interactions.getComponentType());
        interactions.setInteractionId(InteractionType.Use, ROOT_INTERACTION);
        interactions.setInteractionHint(HINT);
        holder.ensureComponent(Interactable.getComponentType());
    }

    /**
     * Undoes {@link #apply} on a player that just left a world. Mirrors what
     * the game mode state does on the way out: only our own entries go, and
     * the {@code Interactions} component itself only once it is empty.
     */
    public static void remove(Holder<EntityStore> holder) {
        Interactions interactions = holder.getComponent(Interactions.getComponentType());
        if (interactions != null && strip(interactions)) {
            holder.tryRemoveComponent(Interactions.getComponentType());
        }
        holder.tryRemoveComponent(Interactable.getComponentType());
    }

    /**
     * Same as {@link #remove(Holder)} for a player still in a world. Must
     * run on that world's thread.
     */
    public static void remove(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        Interactions interactions = accessor.getComponent(ref, Interactions.getComponentType());
        if (interactions != null && strip(interactions)) {
            accessor.tryRemoveComponent(ref, Interactions.getComponentType());
        }
        accessor.tryRemoveComponent(ref, Interactable.getComponentType());
    }

    /** Drops our entries; true when nothing else is left in the component. */
    private static boolean strip(Interactions interactions) {
        if (ROOT_INTERACTION.equals(interactions.getInteractionId(InteractionType.Use))) {
            interactions.removeInteractionId(InteractionType.Use);
        }
        if (HINT.equals(interactions.getInteractionHint())) {
            interactions.setInteractionHint(null);
        }
        return interactions.isEmpty();
    }
}
