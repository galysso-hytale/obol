package dev.galysso.obol.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.galysso.obol.api.PlayerWallet;
import dev.galysso.obol.ui.PlayerHuds;

import java.util.Locale;
import java.util.UUID;

/**
 * {@code /balance}: the caller's own balance; {@code /balance hud on|off}:
 * Obol's own HUD, which follows that balance.
 *
 * <p>Also the smoke test of the whole plugin: if this answers, the plugin is
 * loaded, the API installed and the store reachable.</p>
 */
public final class BalanceCommand extends CommandBase {

    public BalanceCommand(PlayerHuds huds) {
        super("balance", "Shows your balance.");
        requireNoPermission();
        addSubCommand(new Hud(huds));
    }

    @Override
    protected void executeSync(CommandContext context) {
        // Throws a SenderTypeException (relayed as a message) from the console.
        PlayerRef me = context.senderAs(PlayerRef.class);
        PlayerWallet wallet = new PlayerWallet(me.getUuid());
        context.sendMessage(Message.raw("Balance: " + Commands.format(wallet.balance())));
    }

    private static final class Hud extends CommandBase {

        private final PlayerHuds huds;
        private final RequiredArg<String> state;

        Hud(PlayerHuds huds) {
            super("hud", "Shows or hides your balance on screen.");
            this.huds = huds;
            state = withRequiredArg("state", "on or off", ArgTypes.STRING);
            requireNoPermission();
        }

        @Override
        protected void executeSync(CommandContext context) {
            UUID me = context.senderAs(PlayerRef.class).getUuid();
            String reply = switch (state.get(context).toLowerCase(Locale.ROOT)) {
                case "on" -> enable(me);
                case "off" -> huds.disable(me) ? "Balance HUD off." : "Balance HUD is already off.";
                default -> throw Commands.error("Usage: /balance hud on|off");
            };
            context.sendMessage(Message.raw(reply));
        }

        private String enable(UUID me) {
            try {
                return huds.enable(me) ? "Balance HUD on." : "Balance HUD is already on.";
            } catch (IllegalArgumentException e) {
                // Connected but not in a world yet: the choice is kept and
                // the HUD appears when the player is ready.
                return "Balance HUD on; it will show once you are in a world.";
            }
        }
    }
}
