package dev.galysso.obol.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.exceptions.GeneralCommandException;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.CoinsParseException;

import java.util.UUID;

/**
 * What the three commands share: amount parsing, naming a player who may be
 * offline, and message wording.
 *
 * <p>Commands go through the public API only ({@code PlayerWallet},
 * {@code ObolApi.get()}), never through {@code ObolApiImpl}: they are the
 * first consumer of the API and must not need more than a third-party plugin
 * gets.</p>
 */
final class Commands {

    private Commands() {
    }

    /**
     * Parses a command amount, rejecting zero.
     *
     * @throws GeneralCommandException with the parse error, so the server
     *                                 relays it to the sender
     */
    static Coins positiveAmount(String text) {
        Coins amount = amount(text);
        if (amount.isZero()) {
            throw error("The amount must be more than 0c.");
        }
        return amount;
    }

    /**
     * Parses a command amount; zero is allowed.
     *
     * @throws GeneralCommandException with the parse error, so the server
     *                                 relays it to the sender
     */
    static Coins amount(String text) {
        try {
            return CoinsFormat.parse(text);
        } catch (CoinsParseException e) {
            throw error(e.getMessage() + ". Example: 2g 50s 4c");
        }
    }

    static String format(Coins coins) {
        return CoinsFormat.STANDARD.format(coins);
    }

    /**
     * {@return the player's username if online, otherwise their UUID}
     */
    static String name(UUID playerId) {
        PlayerRef online = Universe.get().getPlayer(playerId);
        return online == null ? playerId.toString() : online.getUsername();
    }

    /**
     * Sends {@code message} to the player if they are online; a no-op
     * otherwise, since the balance store does not need them connected.
     */
    static void tell(UUID playerId, String message) {
        PlayerRef online = Universe.get().getPlayer(playerId);
        if (online != null) {
            online.sendMessage(Message.raw(message));
        }
    }

    static GeneralCommandException error(String text) {
        return new GeneralCommandException(Message.raw(text));
    }
}
