package dev.galysso.obol.internal;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsListener;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.event.CoinsChangedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenersTest {

    private static final CoinsChangedEvent EVENT =
            new CoinsChangedEvent(new WalletId("test", "a"), Coins.ZERO, Coins.ofCopper(1));

    private final List<String> reported = new ArrayList<>();
    private final Listeners listeners = new Listeners(
            (listener, event, e) -> reported.add(e.getMessage()));

    @Test
    void dispatchesInSubscriptionOrder() {
        List<String> calls = new ArrayList<>();
        listeners.add(event -> calls.add("first"));
        listeners.add(event -> calls.add("second"));
        listeners.publish(EVENT);
        assertEquals(List.of("first", "second"), calls);
    }

    @Test
    void aThrowingListenerIsReportedAndDoesNotStopTheOthers() {
        List<CoinsChangedEvent> seen = new ArrayList<>();
        CoinsListener broken = event -> {
            throw new IllegalStateException("boom");
        };
        listeners.add(broken);
        listeners.add(seen::add);

        listeners.publish(EVENT);

        assertEquals(List.of(EVENT), seen);
        assertEquals(List.of("boom"), reported);
    }

    @Test
    void theReporterReceivesTheCulpritAndTheEvent() {
        List<Object> details = new ArrayList<>();
        Listeners detailed = new Listeners((listener, event, e) -> {
            details.add(listener);
            details.add(event);
        });
        CoinsListener broken = event -> {
            throw new UnsupportedOperationException();
        };
        detailed.add(broken);
        detailed.publish(EVENT);
        assertSame(broken, details.get(0));
        assertSame(EVENT, details.get(1));
    }

    @Test
    void removeReportsWhetherItWasSubscribed() {
        CoinsListener listener = event -> {
        };
        assertFalse(listeners.remove(listener));
        listeners.add(listener);
        assertTrue(listeners.remove(listener));
        assertFalse(listeners.remove(listener));
    }

    @Test
    void aListenerMayUnsubscribeItselfWhileBeingCalled() {
        List<Integer> calls = new ArrayList<>();
        CoinsListener[] once = new CoinsListener[1];
        once[0] = event -> {
            calls.add(1);
            assertTrue(listeners.remove(once[0]));
        };
        listeners.add(once[0]);
        listeners.publish(EVENT);
        listeners.publish(EVENT);
        assertEquals(List.of(1), calls);
        assertTrue(reported.isEmpty());
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> listeners.add(null));
        assertThrows(NullPointerException.class, () -> listeners.publish(null));
        assertThrows(NullPointerException.class, () -> new Listeners(null));
    }
}
