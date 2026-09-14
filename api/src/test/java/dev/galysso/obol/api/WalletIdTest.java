package dev.galysso.obol.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletIdTest {

    @Test
    void toStringIsKindColonKey() {
        WalletId id = new WalletId("shop", "smith_01");
        assertEquals("shop:smith_01", id.toString());
    }

    @Test
    void playerWalletsAreKeyedByUuid() {
        UUID uuid = UUID.fromString("8f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f");
        WalletId id = WalletId.player(uuid);
        assertEquals(new WalletId(WalletId.PLAYER_KIND, "8f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f"), id);
        assertEquals("player:8f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f", id.toString());
        assertThrows(NullPointerException.class, () -> WalletId.player(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Shop", "sh op", "shop:1", "smith.01", "é"})
    void rejectsInvalidParts(String bad) {
        assertThrows(IllegalArgumentException.class, () -> new WalletId(bad, "key"));
        assertThrows(IllegalArgumentException.class, () -> new WalletId("kind", bad));
    }

    @Test
    void rejectsNullParts() {
        assertThrows(NullPointerException.class, () -> new WalletId(null, "key"));
        assertThrows(NullPointerException.class, () -> new WalletId("kind", null));
    }
}
