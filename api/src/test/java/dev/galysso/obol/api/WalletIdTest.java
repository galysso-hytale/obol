package dev.galysso.obol.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletIdTest {

    @Test
    void storageKeyAndParseAreInverse() {
        WalletId id = new WalletId("shop", "smith_01");
        assertEquals("shop:smith_01", id.storageKey());
        assertEquals("shop:smith_01", id.toString());
        assertEquals(id, WalletId.parse("shop:smith_01"));

        String uuid = UUID.randomUUID().toString();
        WalletId player = new WalletId("player", uuid);
        assertEquals(player, WalletId.parse(player.storageKey()));
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

    @ParameterizedTest
    @ValueSource(strings = {"", "shop", ":key", "shop:", "Shop:key", "shop:a:b"})
    void parseRejectsMalformedKeys(String bad) {
        assertThrows(IllegalArgumentException.class, () -> WalletId.parse(bad));
    }
}
