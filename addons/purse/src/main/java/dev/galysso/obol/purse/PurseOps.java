package dev.galysso.obol.purse;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Obol;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.purse.api.PurseItem;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What one does with a purse: put coins in from one's balance, take coins
 * out of it, hand everything to another player. Each is a
 * {@link Wallet#transferTo} between Obol wallets, followed by a rewrite of
 * the item in its slot (tooltip and state, see {@link PurseItem}).
 *
 * <p>The item is rewritten by compare-and-swap
 * ({@link ItemContainer#replaceItemStackInSlot}): if the slot no longer
 * holds the stack read at the start, the player moved the purse meanwhile,
 * and the slot is left alone. The money is already where it should be, in
 * Obol's store. Only the tooltip of the moved item is stale, and it is
 * rewritten at the purse's next use. No path creates or loses coins.</p>
 *
 * <p>Filling an empty purse writes the key into the item <em>before</em>
 * the transfer, so that a purse carrying a key always has a wallet behind
 * it. If the transfer is refused, the key is taken back. A stack of
 * several empty purses is split first: one gets the key, the rest go back
 * into the inventory.</p>
 *
 * <p>Everything runs on the world thread of the player (interactions,
 * pages and commands all do), Obol's locks cover the rest.</p>
 */
public final class PurseOps {

    private static final String MOVED = "The purse is no longer in your hand.";
    private static final int DRAIN_ATTEMPTS = 3;

    private final HytaleLogger logger;

    public PurseOps(HytaleLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * What an operation did, and what to tell the player.
     *
     * @param ok      whether coins moved
     * @param amount  how many, {@link Coins#ZERO} when none did
     * @param message for the player who acted
     */
    public record Outcome(boolean ok, Coins amount, String message) {

        /** {@return an outcome that moved {@code amount}} */
        public static Outcome moved(Coins amount, String message) {
            return new Outcome(true, amount, message);
        }

        /** {@return an outcome that moved nothing} */
        public static Outcome refused(String message) {
            return new Outcome(false, Coins.ZERO, message);
        }
    }

    /**
     * {@return what the purse holds, {@link Coins#ZERO} for an empty purse
     * or a stack that is not one}
     */
    public Coins content(ItemStack stack) {
        return PurseItem.walletId(stack).map(id -> Obol.wallet(id).balance()).orElse(Coins.ZERO);
    }

    /**
     * Moves {@code amount} from the player's balance into the purse in
     * {@code slot} of {@code container}.
     *
     * @param inventory where the rest of a stack of several purses goes
     */
    public Outcome put(UUID player, ItemContainer container, short slot, ItemContainer inventory, Coins amount) {
        ItemStack stack = container.getItemStack(slot);
        if (!PurseItem.isPurse(stack)) {
            return Outcome.refused(MOVED);
        }
        if (amount.equals(Coins.ZERO)) {
            return Outcome.refused("The amount must be more than 0c.");
        }
        Wallet payer = Obol.playerWallet(player);
        if (!payer.canAfford(amount)) {
            return Outcome.refused("You only have " + payer.balance() + ".");
        }
        Optional<WalletId> existing = PurseItem.walletId(stack);
        WalletId id;
        ItemStack keyed;
        if (existing.isPresent()) {
            id = existing.get();
            keyed = stack;
        } else {
            id = PurseItem.newWalletId();
            keyed = key(container, slot, inventory, stack, id);
            if (keyed == null) {
                return Outcome.refused(stack.getQuantity() > 1 ? "No room to split the stack of purses." : MOVED);
            }
        }
        Wallet purse = Obol.wallet(id);
        if (!payer.transferTo(purse, amount)) {
            if (existing.isEmpty()) {
                // Nothing was written under the key: take it back.
                container.replaceItemStackInSlot(slot, keyed, PurseItem.emptied(keyed));
            }
            return Outcome.refused("You only have " + payer.balance() + ".");
        }
        refresh(container, slot, keyed, id, purse);
        return Outcome.moved(amount, "Put " + amount + " in the purse.");
    }

    /**
     * Moves the whole balance of the player into the purse.
     */
    public Outcome putAll(UUID player, ItemContainer container, short slot, ItemContainer inventory) {
        Coins balance = Obol.playerWallet(player).balance();
        if (balance.equals(Coins.ZERO)) {
            return Outcome.refused("You have nothing to put in.");
        }
        return put(player, container, slot, inventory, balance);
    }

    /**
     * Moves {@code amount} from the purse in {@code slot} of
     * {@code container} into the player's balance.
     */
    public Outcome take(UUID player, ItemContainer container, short slot, Coins amount) {
        ItemStack stack = container.getItemStack(slot);
        if (!PurseItem.isPurse(stack)) {
            return Outcome.refused(MOVED);
        }
        if (amount.equals(Coins.ZERO)) {
            return Outcome.refused("The amount must be more than 0c.");
        }
        Optional<WalletId> id = PurseItem.walletId(stack);
        if (id.isEmpty()) {
            return Outcome.refused("The purse is empty.");
        }
        Wallet purse = Obol.wallet(id.get());
        if (!purse.transferTo(Obol.playerWallet(player), amount)) {
            return Outcome.refused("The purse only holds " + purse.balance() + ".");
        }
        refresh(container, slot, stack, id.get(), purse);
        return Outcome.moved(amount, "Took " + amount + " out of the purse.");
    }

    /**
     * Moves everything the purse holds into the player's balance and
     * leaves the purse empty.
     */
    public Outcome takeAll(UUID player, ItemContainer container, short slot) {
        return drainInto(Obol.playerWallet(player), container, slot, "Took %s out of the purse.");
    }

    /**
     * Moves everything the purse holds into {@code receiver}'s balance and
     * leaves the purse in the giver's hand, empty.
     */
    public Outcome give(UUID receiver, String receiverName, ItemContainer container, short slot) {
        return drainInto(Obol.playerWallet(receiver), container, slot, "Handed %s to " + receiverName + ".");
    }

    private Outcome drainInto(Wallet to, ItemContainer container, short slot, String done) {
        ItemStack stack = container.getItemStack(slot);
        if (!PurseItem.isPurse(stack)) {
            return Outcome.refused(MOVED);
        }
        Optional<WalletId> id = PurseItem.walletId(stack);
        if (id.isEmpty()) {
            return Outcome.refused("The purse is empty.");
        }
        Wallet purse = Obol.wallet(id.get());
        Coins moved = drain(purse, to);
        refresh(container, slot, stack, id.get(), purse);
        if (moved.equals(Coins.ZERO)) {
            return Outcome.refused("The purse is empty.");
        }
        return Outcome.moved(moved, String.format(done, moved));
    }

    /**
     * Transfers the whole balance of {@code purse} to {@code to}. The
     * balance is read, then transferred, so a second key to the same wallet
     * used at the same instant from another world thread can make the
     * transfer come up short: read again, a bounded number of times.
     *
     * @return what was moved, {@link Coins#ZERO} if the purse was empty
     */
    private Coins drain(Wallet purse, Wallet to) {
        for (int attempt = 0; attempt < DRAIN_ATTEMPTS; attempt++) {
            Coins balance = purse.balance();
            if (balance.equals(Coins.ZERO)) {
                return Coins.ZERO;
            }
            if (purse.transferTo(to, balance)) {
                return balance;
            }
        }
        logger.atWarning().log("Purse %s changed under three attempts to drain it", purse.id());
        return Coins.ZERO;
    }

    /**
     * Gives the purse in {@code slot} the key {@code id}, isolating one
     * purse first when the slot holds a stack of several.
     *
     * @return the keyed stack now in the slot, or {@code null} if the slot
     *         changed under us or the rest of the stack has no room
     */
    private ItemStack key(ItemContainer container, short slot, ItemContainer inventory, ItemStack stack, WalletId id) {
        int quantity = stack.getQuantity();
        if (quantity == 1) {
            ItemStack keyed = PurseItem.withWallet(stack, id, Coins.ZERO);
            return container.replaceItemStackInSlot(slot, stack, keyed).succeeded() ? keyed : null;
        }
        // Take the others out of the slot before keying, and put them
        // elsewhere only once the slot holds the keyed purse: while it holds
        // a plain one, they would merge straight back into it.
        ItemStack one = stack.withQuantity(1);
        ItemStack rest = stack.withQuantity(quantity - 1);
        if (!container.removeItemStackFromSlot(slot, quantity - 1).succeeded()) {
            return null;
        }
        ItemStack keyed = PurseItem.withWallet(one, id, Coins.ZERO);
        if (!container.replaceItemStackInSlot(slot, one, keyed).succeeded()) {
            container.addItemStackToSlot(slot, rest);
            return null;
        }
        if (!inventory.addItemStack(rest, true, false, true).succeeded()) {
            container.replaceItemStackInSlot(slot, keyed, one);
            container.addItemStackToSlot(slot, rest);
            return null;
        }
        return keyed;
    }

    /**
     * Rewrites the purse in {@code slot} after a transfer: the tooltip and
     * the state of the new balance, or an empty purse (and no wallet entry
     * any more) when the balance is zero.
     */
    private void refresh(ItemContainer container, short slot, ItemStack current, WalletId id, Wallet purse) {
        Coins balance = purse.balance();
        ItemStack updated;
        if (balance.equals(Coins.ZERO)) {
            purse.clear();
            updated = PurseItem.emptied(current);
        } else {
            updated = PurseItem.withWallet(current, id, balance);
        }
        if (!container.replaceItemStackInSlot(slot, current, updated).succeeded()) {
            logger.atFine().log("Purse %s moved during its update; its tooltip is stale until next use", id);
        }
    }
}
