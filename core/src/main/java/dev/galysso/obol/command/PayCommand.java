package dev.galysso.obol.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.PlayerWallet;

/**
 * {@code /pay <player> <amount>}: moves coins from the caller to an online
 * player. Both parties are told.
 */
public final class PayCommand extends CommandBase {

    private final RequiredArg<PlayerRef> target;
    private final RequiredArg<String> amount;

    public PayCommand() {
        super("pay", "Sends coins to another player.");
        target = withRequiredArg("player", "Online player to pay.", ArgTypes.PLAYER_REF);
        // Greedy: "2g 50s" is one amount, spaces included.
        amount = withRequiredArg("amount", "Amount, e.g. 2g 50s.", ArgTypes.GREEDY_STRING);
        requireNoPermission();
    }

    @Override
    protected void executeSync(CommandContext context) {
        PlayerRef me = context.senderAs(PlayerRef.class);
        PlayerRef to = target.get(context);
        Coins coins = Commands.positiveAmount(amount.get(context));
        if (to.getUuid().equals(me.getUuid())) {
            throw Commands.error("You cannot pay yourself.");
        }

        PlayerWallet from = new PlayerWallet(me.getUuid());
        PlayerWallet dest = new PlayerWallet(to.getUuid());
        if (!from.transferTo(dest, coins)) {
            throw Commands.error("Insufficient funds: you have " + Commands.format(from.balance()) + ".");
        }
        context.sendMessage(Message.raw("You sent " + Commands.format(coins) + " to " + to.getUsername()
                + ". Balance: " + Commands.format(from.balance())));
        to.sendMessage(Message.raw(me.getUsername() + " sent you " + Commands.format(coins)
                + ". Balance: " + Commands.format(dest.balance())));
    }
}
