package dev.galysso.obol.internal;

import dev.galysso.obol.api.CoinsListener;
import dev.galysso.obol.api.event.CoinsChangedEvent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The subscribed {@link CoinsListener}s and their dispatch, behind
 * {@code Obol.addListener}, fed by {@link WalletImpl}.
 *
 * <p>A {@link CopyOnWriteArrayList}: subscriptions are rare, dispatches are
 * not, and a listener may add or remove listeners while being called. A
 * listener that throws is reported to the {@link FailureReporter} and does
 * not stop the others, nor does it reach the code that moved the money: the
 * write is already done by the time listeners run, so an exception there
 * would only mislead the caller about it.</p>
 *
 * <p>JDK-only so that dispatch is unit-tested; the plugin plugs the server
 * logger in as the reporter.</p>
 */
public final class Listeners {

    /** Where a failing listener is reported. Must not throw. */
    @FunctionalInterface
    public interface FailureReporter {
        void report(CoinsListener listener, CoinsChangedEvent event, RuntimeException failure);
    }

    private final List<CoinsListener> listeners = new CopyOnWriteArrayList<>();
    private final FailureReporter reporter;

    public Listeners(FailureReporter reporter) {
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    public void add(CoinsListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public boolean remove(CoinsListener listener) {
        return listeners.remove(listener);
    }

    /** Calls every listener in subscription order, on the current thread. */
    public void publish(CoinsChangedEvent event) {
        Objects.requireNonNull(event, "event");
        for (CoinsListener listener : listeners) {
            try {
                listener.onCoinsChanged(event);
            } catch (RuntimeException e) {
                reporter.report(listener, event, e);
            }
        }
    }
}
