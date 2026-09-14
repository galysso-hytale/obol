package dev.galysso.obol.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.exceptions.GeneralCommandException;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsParseException;

import java.util.UUID;

/**
 * What the sub-commands share: amount parsing, naming a player who may be
 * offline, and error wording. Only the caller gets a message: the player
 * concerned has the HUD.
 *
 * <p>Amounts are read and written the way the public API does
 * ({@code Coins.parse}, {@code Coins.toString}), so that what an admin
 * types is what a modder's users type.</p>
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
        if (amount.equals(Coins.ZERO)) {
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
            return Coins.parse(text);
        } catch (CoinsParseException e) {
            throw error(e.getMessage() + ". Example: 2g 50s 4c");
        }
    }

    static String format(Coins coins) {
        return coins.toString();
    }

    /**
     * {@return the player's username if online, otherwise their UUID}
     */
    static String name(UUID playerId) {
        PlayerRef online = Universe.get().getPlayer(playerId);
        return online == null ? playerId.toString() : online.getUsername();
    }

    static GeneralCommandException error(String text) {
        return new GeneralCommandException(Message.raw(text));
    }
}
