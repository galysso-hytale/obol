package dev.galysso.obol.internal;

import dev.galysso.obol.api.BalanceStore;
import dev.galysso.obol.api.ObolApi;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.internal.ObolRuntime;

import java.util.concurrent.locks.Lock;

/**
 * The single implementation of {@link ObolApi}, published to third-party
 * plugins through {@code ObolApiHolder}. Also the {@link ObolRuntime} that
 * {@code Wallet} relies on.
 */
public final class ObolApiImpl implements ObolApi, ObolRuntime {

    // Both services are wired in the next step (WalletLocks + BalanceStoreImpl).

    @Override
    public BalanceStore balances() {
        throw new UnsupportedOperationException("BalanceStore is not wired yet");
    }

    @Override
    public Lock lockFor(WalletId id) {
        throw new UnsupportedOperationException("Wallet locks are not wired yet");
    }
}
