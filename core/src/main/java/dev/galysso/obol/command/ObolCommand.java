package dev.galysso.obol.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.internal.ObolBackendImpl;
import dev.galysso.obol.internal.WalletImpl;

import java.util.Objects;
import java.util.UUID;

/**
 * {@code /obol give|take|set <player> <amount>} and
 * {@code /obol transfer <from> <to> <amount>}: administration, the only
 * commands Obol has. Players see their balance on the HUD and move money
 * through the mods built on Obol (shops, trades), not by hand.
 *
 * <p>One permission node, {@code obol.admin}, for the root and the
 * sub-commands; without an explicit {@code requirePermission} the server
 * would generate one node per sub-command. The node is granted to the
 * {@code hytale:Admin} group by default; other groups are configured in
 * {@code permissions.json}.</p>
 *
 * <p>Players are {@code PLAYER_UUID}s: the name of an online player, or a
 * raw UUID for someone offline — the store does not need them connected.</p>
 *
 * <p>The commands talk to the implementation, not to the {@code Obol}
 * facade: {@code set} is an administrative write that no wallet operation
 * of the public API offers.</p>
 */
public final class ObolCommand extends CommandBase {

    static final String PERMISSION = "obol.admin";

    public ObolCommand(ObolBackendImpl backend) {
        super("obol", "Obol administration.");
        Objects.requireNonNull(backend, "backend");
        requirePermission(PERMISSION);
        setPermissionGroups("hytale:Admin");
        addSubCommand(new Give(backend));
        addSubCommand(new Take(backend));
        addSubCommand(new Set(backend));
        addSubCommand(new Transfer(backend));
    }

    @Override
    protected void executeSync(CommandContext context) {
        context.sendMessage(getUsageString(context.sender()));
    }

    /**
     * Shared shape of give, take and set: a player and an amount.
     */
    private abstract static class Sub extends CommandBase {

        final ObolBackendImpl backend;
        private final RequiredArg<UUID> player;
        private final RequiredArg<String> amount;

        Sub(ObolBackendImpl backend, String name, String description) {
            super(name, description);
            this.backend = backend;
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
         * Applies the operation and returns what to tell the caller. The
         * player is not told: their HUD shows the change as it happens,
         * the way it does for a change made by any other mod.
         *
         * @param amount raw text, parsed by the sub-command since only
         *               {@code set} accepts zero
         */
        abstract String apply(UUID target, String amount);
    }

    private static final class Give extends Sub {

        Give(ObolBackendImpl backend) {
            super(backend, "give", "Adds coins to a player's balance.");
        }

        @Override
        String apply(UUID target, String amountText) {
            Coins amount = Commands.positiveAmount(amountText);
            Coins after = backend.wallet(WalletId.player(target)).deposit(amount);
            return "Gave " + Commands.format(amount) + " to " + Commands.name(target)
                    + ". Balance: " + Commands.format(after);
        }
    }

    private static final class Take extends Sub {

        Take(ObolBackendImpl backend) {
            super(backend, "take", "Removes coins from a player's balance.");
        }

        @Override
        String apply(UUID target, String amountText) {
            Coins amount = Commands.positiveAmount(amountText);
            WalletImpl wallet = backend.wallet(WalletId.player(target));
            if (!wallet.withdraw(amount)) {
                throw Commands.error("Insufficient funds: " + Commands.name(target) + " has "
                        + Commands.format(wallet.balance()) + ".");
            }
            Coins after = wallet.balance();
            return "Took " + Commands.format(amount) + " from " + Commands.name(target)
                    + ". Balance: " + Commands.format(after);
        }
    }

    private static final class Set extends Sub {

        Set(ObolBackendImpl backend) {
            super(backend, "set", "Sets a player's balance; zero is allowed.");
        }

        @Override
        String apply(UUID target, String amountText) {
            Coins amount = Commands.amount(amountText);
            // The wallet takes its lock and publishes the change, so a HUD
            // tracking the player sees the new balance at once.
            Coins previous = backend.wallet(WalletId.player(target)).set(amount);
            return "Set " + Commands.name(target) + "'s balance to " + Commands.format(amount)
                    + " (was " + Commands.format(previous) + ").";
        }
    }

    private static final class Transfer extends CommandBase {

        private final ObolBackendImpl backend;
        private final RequiredArg<UUID> from;
        private final RequiredArg<UUID> to;
        private final RequiredArg<String> amount;

        Transfer(ObolBackendImpl backend) {
            super("transfer", "Moves coins from one player to another.");
            this.backend = backend;
            from = withRequiredArg("from", "Paying player: online name, or a UUID.", ArgTypes.PLAYER_UUID);
            to = withRequiredArg("to", "Receiving player: online name, or a UUID.", ArgTypes.PLAYER_UUID);
            amount = withRequiredArg("amount", "Amount, e.g. 2g 50s.", ArgTypes.GREEDY_STRING);
            requirePermission(PERMISSION);
        }

        @Override
        protected void executeSync(CommandContext context) {
            UUID source = from.get(context);
            UUID dest = to.get(context);
            Coins coins = Commands.positiveAmount(amount.get(context));
            if (source.equals(dest)) {
                throw Commands.error("The two players must differ.");
            }
            WalletImpl payer = backend.wallet(WalletId.player(source));
            WalletImpl payee = backend.wallet(WalletId.player(dest));
            if (!payer.transferTo(payee, coins)) {
                throw Commands.error("Insufficient funds: " + Commands.name(source) + " has "
                        + Commands.format(payer.balance()) + ".");
            }
            context.sendMessage(Message.raw("Moved " + Commands.format(coins) + " from " + Commands.name(source)
                    + " to " + Commands.name(dest) + "."));
        }
    }
}
