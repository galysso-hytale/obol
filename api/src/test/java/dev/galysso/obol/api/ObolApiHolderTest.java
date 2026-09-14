package dev.galysso.obol.api;

import dev.galysso.obol.api.internal.ObolApiHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObolApiHolderTest {

    @AfterEach
    void uninstall() {
        ObolApiHolder.uninstall();
    }

    @Test
    void absentObolIsExplained() {
        assertTrue(ObolApi.find().isEmpty());
        IllegalStateException e = assertThrows(IllegalStateException.class, ObolApi::get);
        assertTrue(e.getMessage().contains("Galysso:obol"));
        assertThrows(IllegalStateException.class, ObolApiHolder::runtime);
    }

    @Test
    void installExposesBothFaces() {
        InMemoryRuntime runtime = InMemoryRuntime.install();
        assertSame(runtime, ObolApi.get());
        assertSame(runtime, ObolApi.find().orElseThrow());
        assertSame(runtime, ObolApiHolder.runtime());
        assertThrows(IllegalStateException.class, () -> ObolApiHolder.install(new InMemoryRuntime()));

        ObolApiHolder.uninstall();
        assertEquals(java.util.Optional.empty(), ObolApi.find());
    }
}
