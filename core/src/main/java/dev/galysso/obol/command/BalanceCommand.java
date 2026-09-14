package dev.galysso.obol.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.galysso.obol.api.PlayerWallet;

/**
 * {@code /balance}: the caller's own balance.
 *
 * <p>Also the smoke test of the whole plugin: if this answers, the plugin is
 * loaded, the API installed and the store reachable.</p>
 */
public final class BalanceCommand extends CommandBase {

    public BalanceCommand() {
        super("balance", "Shows your balance.");
        requireNoPermission();
    }

    @Override
    protected void executeSync(CommandContext context) {
        // Throws a SenderTypeException (relayed as a message) from the console.
        PlayerRef me = context.senderAs(PlayerRef.class);
        PlayerWallet wallet = new PlayerWallet(me.getUuid());
        context.sendMessage(Message.raw("Balance: " + Commands.format(wallet.balance())));
    }
}
