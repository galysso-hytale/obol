package dev.galysso.obol.api;

import dev.galysso.obol.api.ScreenPosition.Corner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenPositionTest {

    @Test
    void factoriesNameTheCorner() {
        assertEquals(new ScreenPosition(Corner.TOP_LEFT, 1, 2), ScreenPosition.topLeft(1, 2));
        assertEquals(new ScreenPosition(Corner.TOP_RIGHT, 1, 2), ScreenPosition.topRight(1, 2));
        assertEquals(new ScreenPosition(Corner.BOTTOM_LEFT, 1, 2), ScreenPosition.bottomLeft(1, 2));
        assertEquals(new ScreenPosition(Corner.BOTTOM_RIGHT, 1, 2), ScreenPosition.bottomRight(1, 2));
    }

    @Test
    void cornersKnowTheirEdges() {
        assertTrue(Corner.TOP_RIGHT.isRight());
        assertTrue(Corner.BOTTOM_RIGHT.isRight());
        assertFalse(Corner.TOP_LEFT.isRight());
        assertTrue(Corner.BOTTOM_LEFT.isBottom());
        assertFalse(Corner.TOP_RIGHT.isBottom());
    }

    @Test
    void rejectsNullCornerAndNegativeOffsets() {
        assertThrows(NullPointerException.class, () -> new ScreenPosition(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ScreenPosition.topLeft(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> ScreenPosition.topLeft(0, -1));
        assertEquals(0, ScreenPosition.topLeft(0, 0).offsetX());
    }
}
