package dev.galysso.obol.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.ObolApi;
import dev.galysso.obol.api.PlayerWallet;

import java.util.UUID;

/**
 * {@code /obol give|take|set <player> <amount>}: administration.
 *
 * <p>One permission node, {@code obol.admin}, for the root and the three
 * sub-commands; without an explicit {@code requirePermission} the server
 * would generate one node per sub-command. The node is granted to the
 * {@code hytale:Admin} group by default; other groups are configured in
 * {@code permissions.json}.</p>
 *
 * <p>The player is a {@code PLAYER_UUID}: the name of an online player, or a
 * raw UUID for someone offline — the store does not need them connected.</p>
 */
public final class ObolCommand extends CommandBase {

    static final String PERMISSION = "obol.admin";

    public ObolCommand() {
        super("obol", "Obol administration.");
        requirePermission(PERMISSION);
        setPermissionGroups("hytale:Admin");
        addSubCommand(new Give());
        addSubCommand(new Take());
        addSubCommand(new Set());
    }

    @Override
    protected void executeSync(CommandContext context) {
        context.sendMessage(getUsageString(context.sender()));
    }

    /**
     * Shared shape of the three sub-commands: a player and an amount.
     */
    private abstract static class Sub extends CommandBase {

        private final RequiredArg<UUID> player;
        private final RequiredArg<String> amount;

        Sub(String name, String description) {
            super(name, description);
            player = withRequiredArg("player", "Online player name, or a UUID.", ArgTypes.PLAYER_UUID);
            amount = withRequiredArg("amount", "Amount, e.g. 2g 50s.", ArgTypes.GREEDY_STRING);
            requirePermission(PERMISSION);
        }

        @Override
        protected final void executeSync(CommandContext context) {
            UUID target = player.get(context);
            String reply = apply(target, amount.get(context));
            context.sendMessage(Message.raw(reply));
        }

        /**
         * Applies the operation and returns what to tell the caller.
         *
         * @param amount raw text, parsed by the sub-command since only
         *               {@code set} accepts zero
         */
        abstract String apply(UUID target, String amount);
    }

    private static final class Give extends Sub {

        Give() {
            super("give", "Adds coins to a player's balance.");
        }

        @Override
        String apply(UUID target, String amountText) {
            Coins amount = Commands.positiveAmount(amountText);
            Coins after = new PlayerWallet(target).deposit(amount);
            Commands.tell(target, "You received " + Commands.format(amount) + ". Balance: " + Commands.format(after));
            return "Gave " + Commands.format(amount) + " to " + Commands.name(target)
                    + ". Balance: " + Commands.format(after);
        }
    }

    private static final class Take extends Sub {

        Take() {
            super("take", "Removes coins from a player's balance.");
        }

        @Override
        String apply(UUID target, String amountText) {
            Coins amount = Commands.positiveAmount(amountText);
            PlayerWallet wallet = new PlayerWallet(target);
            if (!wallet.withdraw(amount)) {
                throw Commands.error("Insufficient funds: " + Commands.name(target) + " has "
                        + Commands.format(wallet.balance()) + ".");
            }
            Coins after = wallet.balance();
            Commands.tell(target, Commands.format(amount) + " was taken from you. Balance: " + Commands.format(after));
            return "Took " + Commands.format(amount) + " from " + Commands.name(target)
                    + ". Balance: " + Commands.format(after);
        }
    }

    private static final class Set extends Sub {

        Set() {
            super("set", "Sets a player's balance; zero is allowed.");
        }

        @Override
        String apply(UUID target, String amountText) {
            Coins amount = Commands.amount(amountText);
            // Straight to the store: bypasses the wallet lock and listeners,
            // which is the documented contract of BalanceStore.set.
            Coins previous = ObolApi.get().balances().set(new PlayerWallet(target).id(), amount);
            Commands.tell(target, "Your balance was set to " + Commands.format(amount) + ".");
            return "Set " + Commands.name(target) + "'s balance to " + Commands.format(amount)
                    + " (was " + Commands.format(previous) + ").";
        }
    }
}
