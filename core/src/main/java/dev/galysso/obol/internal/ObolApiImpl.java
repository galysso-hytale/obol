package dev.galysso.obol.internal;

import dev.galysso.obol.api.BalanceStore;
import dev.galysso.obol.api.CoinsListener;
import dev.galysso.obol.api.ObolApi;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.event.CoinsChangedEvent;
import dev.galysso.obol.api.internal.ObolRuntime;
import dev.galysso.obol.ui.CoinsDisplayImpl;
import dev.galysso.obol.ui.OverlayHuds;

import java.util.concurrent.locks.Lock;

/**
 * The single implementation of {@link ObolApi}, published to third-party
 * plugins through {@code ObolApiHolder}. Also the {@link ObolRuntime} that
 * {@code Wallet} relies on.
 */
public final class ObolApiImpl implements ObolApi, ObolRuntime {

    private final WalletLocks locks = new WalletLocks();
    private final BalanceStoreImpl store = new BalanceStoreImpl();
    private final Listeners listeners;
    private final GuardedBalanceStore balances;
    private final CoinsDisplayImpl display;

    /**
     * @param listenerFailures where a listener that throws is reported
     * @param huds             what draws the overlays
     */
    public ObolApiImpl(Listeners.FailureReporter listenerFailures, OverlayHuds huds) {
        this.listeners = new Listeners(listenerFailures);
        this.balances = new GuardedBalanceStore(store, locks, listeners);
        this.display = new CoinsDisplayImpl(huds, listeners);
    }

    @Override
    public BalanceStore balances() {
        return balances;
    }

    /** Covariant on purpose: the plugin forwards disconnects to it. */
    @Override
    public CoinsDisplayImpl display() {
        return display;
    }

    @Override
    public void addListener(CoinsListener listener) {
        listeners.add(listener);
    }

    @Override
    public boolean removeListener(CoinsListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public Lock lockFor(WalletId id) {
        return locks.lockFor(id);
    }

    @Override
    public void publish(CoinsChangedEvent event) {
        listeners.publish(event);
    }

    /** Covariant on purpose: the persistence layer needs the implementation. */
    @Override
    public BalanceStoreImpl storedBalances() {
        return store;
    }
}
