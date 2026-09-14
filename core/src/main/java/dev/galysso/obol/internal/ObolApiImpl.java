package dev.galysso.obol.internal;

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

    private final WalletLocks locks = new WalletLocks();
    private final BalanceStoreImpl balances = new BalanceStoreImpl();

    /** Covariant on purpose: the persistence layer needs the implementation. */
    @Override
    public BalanceStoreImpl balances() {
        return balances;
    }

    @Override
    public Lock lockFor(WalletId id) {
        return locks.lockFor(id);
    }
}
