package dev.galysso.obol.internal;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudPreferencesTest {

    private final HudPreferences preferences = new HudPreferences();
    private final UUID player = UUID.randomUUID();

    @Test
    void offByDefaultAndDirtyOnlyOnChange() {
        assertFalse(preferences.isEnabled(player));
        assertFalse(preferences.isDirty());

        assertFalse(preferences.set(player, false));
        assertFalse(preferences.isDirty());

        assertTrue(preferences.set(player, true));
        assertTrue(preferences.isEnabled(player));
        assertTrue(preferences.isDirty());

        assertFalse(preferences.set(player, true));
        assertEquals(Set.of(player), preferences.snapshot());
        assertFalse(preferences.isDirty());

        assertTrue(preferences.set(player, false));
        assertTrue(preferences.isDirty());
    }

    @Test
    void loadReplacesEverythingWithoutDirtying() {
        preferences.set(player, true);
        UUID other = UUID.randomUUID();

        preferences.snapshot();
        preferences.load(Set.of(other));

        assertFalse(preferences.isEnabled(player));
        assertTrue(preferences.isEnabled(other));
        assertFalse(preferences.isDirty());
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> preferences.set(null, true));
        assertThrows(NullPointerException.class, () -> preferences.isEnabled(null));
        assertThrows(NullPointerException.class, () -> preferences.load(null));
    }
}
