package dev.galysso.obol.api;

/**
 * A {@link Wallet} whose balance lives in Obol's own balance store.
 *
 * <p>The lowest-commitment way to own money: a subclass provides an
 * {@link #id()} and nothing else. Persistence is Obol's problem. The entry is
 * never removed on its own; call {@link BalanceStore#delete(WalletId)} when the
 * owning object disappears for good.</p>
 */
public abstract class StoredWallet extends Wallet {

    protected StoredWallet() {
    }

    @Override
    protected final long loadCopper() {
        return balances().balance(id()).copper();
    }

    @Override
    protected final void saveCopper(long copper) {
        balances().set(id(), Coins.ofCopper(copper));
    }

    private static BalanceStore balances() {
        return ObolApi.get().balances();
    }
}
