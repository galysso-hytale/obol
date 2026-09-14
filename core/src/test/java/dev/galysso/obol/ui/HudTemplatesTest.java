package dev.galysso.obol.ui;

import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.ScreenPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudTemplatesTest {

    @Test
    void onlyStandardHasATemplate() {
        assertTrue(HudTemplates.supports(CoinsFormat.STANDARD));
        assertFalse(HudTemplates.supports(CoinsFormat.LONG));
        assertThrows(IllegalArgumentException.class,
                () -> HudTemplates.document(CoinsFormat.LONG, ScreenPosition.topLeft(0, 0)));
    }

    @Test
    void anchorsNameTheEdgesOfTheCorner() {
        assertEquals("Top: 10, Left: 20", HudTemplates.anchor(ScreenPosition.topLeft(20, 10)));
        assertEquals("Top: 10, Right: 20", HudTemplates.anchor(ScreenPosition.topRight(20, 10)));
        assertEquals("Bottom: 10, Left: 20", HudTemplates.anchor(ScreenPosition.bottomLeft(20, 10)));
        assertEquals("Bottom: 10, Right: 20", HudTemplates.anchor(ScreenPosition.bottomRight(20, 10)));
    }

    @Test
    void theDocumentIsPlacedAndCarriesTheAmountLabel() {
        String right = HudTemplates.document(CoinsFormat.STANDARD, ScreenPosition.bottomRight(5, 6));
        assertTrue(right.contains("LayoutMode: Right;"));
        assertTrue(right.contains("Anchor: (Bottom: 6, Right: 5, Height: 36);"));
        assertTrue(right.contains("Label #Amount {"));
        assertFalse(right.contains("%"), "every placeholder is filled");

        String left = HudTemplates.document(CoinsFormat.STANDARD, ScreenPosition.topLeft(5, 6));
        assertTrue(left.contains("LayoutMode: Left;"));
        assertTrue(left.contains("Anchor: (Top: 6, Left: 5, Height: 36);"));
        assertEquals("#Amount.Text", HudTemplates.AMOUNT_TEXT);
    }
}
