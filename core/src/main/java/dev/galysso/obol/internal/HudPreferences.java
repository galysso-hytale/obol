package dev.galysso.obol.internal;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Which players turned Obol's own coins HUD on. Persisted next to the
 * balances, with the same dirty flag mechanism as {@link BalanceStoreImpl}.
 */
public final class HudPreferences {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean dirty = new AtomicBoolean();

    public boolean isEnabled(UUID player) {
        return enabled.contains(Objects.requireNonNull(player, "player"));
    }

    /**
     * Records the player's choice.
     *
     * @return {@code true} if it changed
     */
    public boolean set(UUID player, boolean on) {
        Objects.requireNonNull(player, "player");
        boolean changed = on ? enabled.add(player) : enabled.remove(player);
        if (changed) {
            dirty.set(true);
        }
        return changed;
    }

    /** Replaces the content with what was read from disk; not dirty. */
    public void load(Set<UUID> loaded) {
        Objects.requireNonNull(loaded, "loaded");
        loaded.forEach(player -> Objects.requireNonNull(player, "player"));
        enabled.clear();
        enabled.addAll(loaded);
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void markDirty() {
        dirty.set(true);
    }

    /** Copies the set and clears the dirty flag; see {@link BalanceStoreImpl#snapshot()}. */
    public Set<UUID> snapshot() {
        dirty.set(false);
        return new HashSet<>(enabled);
    }
}
