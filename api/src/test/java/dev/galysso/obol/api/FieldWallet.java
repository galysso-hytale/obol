package dev.galysso.obol.api;

/**
 * The "balance lives with my entity" flavour of {@link Wallet}, reduced to a
 * field. Counts writes and can be told to fail on the next one, which is how
 * the tests observe atomicity.
 */
class FieldWallet extends Wallet {

    private final WalletId id;
    long copper;
    int saves;
    RuntimeException failOnSave;

    FieldWallet(String key, long copper) {
        this.id = new WalletId("test", key);
        this.copper = copper;
    }

    @Override
    public WalletId id() {
        return id;
    }

    @Override
    protected long loadCopper() {
        return copper;
    }

    @Override
    protected void saveCopper(long copper) {
        saves++;
        if (failOnSave != null) {
            throw failOnSave;
        }
        this.copper = copper;
    }
}
