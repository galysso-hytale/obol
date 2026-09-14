package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.WalletId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalancesPersistenceTest {

    /** In-memory stand-in for balances.json. */
    private static final class MemoryBackend implements BalancesBackend {
        Map<String, Long> disk = new HashMap<>();
        Set<UUID> hudsOnDisk = new HashSet<>();
        int saves;
        boolean failNextSave;

        @Override
        public Snapshot load() {
            return new Snapshot(new HashMap<>(disk), new HashSet<>(hudsOnDisk));
        }

        @Override
        public void save(Snapshot snapshot) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("disk full");
            }
            saves++;
            disk = new HashMap<>(snapshot.balances());
            hudsOnDisk = new HashSet<>(snapshot.hudEnabled());
        }
    }

    private final BalanceStoreImpl store = new BalanceStoreImpl();
    private final HudPreferences huds = new HudPreferences();
    private final MemoryBackend backend = new MemoryBackend();
    private final BalancesPersistence persistence = new BalancesPersistence(store, huds, backend);
    private final WalletId alice = new WalletId("player", "alice");
    private final WalletId bob = new WalletId("player", "bob");

    @Test
    void loadFillsTheStoreWithoutDirtyingIt() {
        backend.disk.put("player:alice", 250L);
        backend.disk.put("player:bob", 0L);

        assertEquals(2, persistence.load());

        assertEquals(Coins.ofCopper(250), store.balance(alice));
        assertTrue(store.exists(bob));
        assertFalse(store.isDirty());
    }

    @Test
    void loadOfNothingIsAnEmptyStore() {
        store.set(alice, Coins.ofCopper(1));
        assertEquals(0, persistence.load());
        assertFalse(store.exists(alice));
    }

    @Test
    void negativeBalanceOnDiskIsRefusedAndTheStoreUntouched() {
        store.set(alice, Coins.ofCopper(1));
        backend.disk.put("player:bob", -5L);

        assertThrows(IllegalArgumentException.class, persistence::load);

        assertEquals(Coins.ofCopper(1), store.balance(alice));
        assertFalse(store.exists(bob));
    }

    @Test
    void saveIfDirtyWritesOnlyWhenSomethingChanged() {
        assertFalse(persistence.saveIfDirty());
        assertEquals(0, backend.saves);

        store.set(alice, Coins.ofCopper(42));
        assertTrue(persistence.saveIfDirty());
        assertEquals(Map.of("player:alice", 42L), backend.disk);

        assertFalse(persistence.saveIfDirty());
        assertEquals(1, backend.saves);
    }

    @Test
    void saveAlwaysWrites() {
        persistence.save();
        persistence.save();
        assertEquals(2, backend.saves);
        assertTrue(backend.disk.isEmpty());
    }

    @Test
    void aFailedSaveLeavesTheStoreDirtyForTheNextOne() {
        store.set(alice, Coins.ofCopper(7));
        backend.failNextSave = true;

        assertThrows(IllegalStateException.class, persistence::saveIfDirty);

        assertTrue(store.isDirty());
        assertTrue(persistence.saveIfDirty());
        assertEquals(Map.of("player:alice", 7L), backend.disk);
    }

    @Test
    void roundTripKeepsZeroBalances() {
        store.set(alice, Coins.ZERO);
        persistence.save();

        BalanceStoreImpl reloaded = new BalanceStoreImpl();
        new BalancesPersistence(reloaded, new HudPreferences(), backend).load();

        assertTrue(reloaded.exists(alice));
        assertEquals(Coins.ZERO, reloaded.balance(alice));
    }

    @Test
    void hudPreferencesTravelWithTheBalances() {
        UUID player = UUID.randomUUID();
        assertFalse(persistence.saveIfDirty());

        assertTrue(huds.set(player, true));
        assertTrue(persistence.saveIfDirty(), "a HUD change alone dirties the file");
        assertEquals(Set.of(player), backend.hudsOnDisk);
        assertFalse(persistence.saveIfDirty());

        HudPreferences reloaded = new HudPreferences();
        new BalancesPersistence(new BalanceStoreImpl(), reloaded, backend).load();
        assertTrue(reloaded.isEnabled(player));
        assertFalse(reloaded.isDirty());
    }

    @Test
    void aFailedSaveLeavesTheHudPreferencesDirtyToo() {
        huds.set(UUID.randomUUID(), true);
        backend.failNextSave = true;

        assertThrows(IllegalStateException.class, persistence::saveIfDirty);

        assertTrue(huds.isDirty());
        assertTrue(persistence.saveIfDirty());
    }
}
