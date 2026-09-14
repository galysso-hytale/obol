package dev.galysso.obol.api;

import java.util.Objects;
import java.util.UUID;

/**
 * The wallet of a player, identified by their {@link UUID}.
 *
 * <p>Stateless like every wallet: build one whenever needed, there is nothing
 * to look up or register. Its id is {@code player:<uuid>}.</p>
 */
public final class PlayerWallet extends StoredWallet {

    /** The {@link WalletId#kind()} of every player wallet. */
    public static final String KIND = "player";

    private final UUID playerId;
    private final WalletId id;

    /**
     * @param playerId the player's UUID
     */
    public PlayerWallet(UUID playerId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.id = new WalletId(KIND, playerId.toString());
    }

    /**
     * {@return the player's UUID}
     */
    public UUID playerId() {
        return playerId;
    }

    @Override
    public WalletId id() {
        return id;
    }
}
