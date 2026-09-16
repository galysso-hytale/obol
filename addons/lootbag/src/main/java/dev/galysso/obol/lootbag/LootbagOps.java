package dev.galysso.obol.lootbag;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Obol;
import dev.galysso.obol.lootbag.api.LootbagItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What opening lootbags does: roll their law and put the coins in the
 * player's balance.
 *
 * <p>Three entries. {@link #open} is the click: it takes the bags out of
 * their slot first, then credits, so that no coin exists without bags
 * gone. {@link #openAll} does the same for every stack of bags of a
 * container (the crouched click on a bag with a fixed amount, the
 * inventory sweep), still as one deposit. {@link #credit} is the pickup: the game
 * removes the item itself (the item entity on the ground, the stack a
 * harvest hands over), so only the roll and the deposit are left. All
 * roll once per bag and make one deposit of the sum, which is one line
 * in Obol's HUD feed. Nothing is said in the chat.</p>
 *
 * <p>A bag without a law (made by hand, or by a {@code Single} drop) rolls
 * the law its rarity has in {@code lootbag.json}, as its tooltip says.</p>
 *
 * <p>Everything runs on the world thread of the player (interactions,
 * packet handlers and inventory systems all do), Obol's locks cover the
 * rest.</p>
 */
public final class LootbagOps {

    private final LootbagConfig config;
    private final HytaleLogger logger;

    public LootbagOps(LootbagConfig config, HytaleLogger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * What an opening did.
     *
     * @param ok     whether coins were credited
     * @param amount how many, {@link Coins#ZERO} when none were
     * @param count  how many bags opened
     * @param rarity the highest rarity among the bags opened, what the
     *               opening sounds like, {@code null} when none did
     */
    public record Outcome(boolean ok, Coins amount, int count, Rarity rarity) {

        static final Outcome NOTHING = new Outcome(false, Coins.ZERO, 0, null);
    }

    /**
     * Opens {@code count} bags of the stack in {@code slot} of
     * {@code container}: removes them, then credits {@code player}.
     *
     * @param expected the stack read by the caller, so that a stack the
     *                 player moved meanwhile is left alone
     * @param count    how many bags, brought down to the stack's quantity
     * @return what happened, {@link Outcome#ok} false and nothing moved
     *         when the slot no longer holds {@code expected}, or when it
     *         is not a lootbag
     */
    public Outcome open(ItemContainer container, short slot, ItemStack expected, UUID player, int count) {
        Optional<LootLaw> law = lawOf(expected);
        if (law.isEmpty()) {
            return Outcome.NOTHING;
        }
        int opened = Math.min(Math.max(count, 1), expected.getQuantity());
        Coins sum = law.get().rollSum(opened, ThreadLocalRandom.current()::nextDouble);
        // The bags go first: a deposit with the bags still in hand would
        // be coins from nothing if the second step failed.
        ItemStackSlotTransaction removal = container.removeItemStackFromSlot(slot, expected, opened);
        if (!removal.succeeded()) {
            return Outcome.NOTHING;
        }
        return deposit(player, sum, opened, List.of(new Removed(container, slot, expected.withQuantity(opened))),
                LootbagItem.rarity(expected).orElseThrow());
    }

    /**
     * Opens every stack of bags {@code container} holds (a player's
     * combined inventory), as one deposit. A stack whose removal fails is
     * skipped, the others still open.
     *
     * @return what happened, {@link Outcome#ok} false when the container
     *         held no bag or the deposit failed
     */
    public Outcome openAll(ItemContainer container, UUID player) {
        List<Removed> removed = new ArrayList<>();
        Coins sum = Coins.ZERO;
        int opened = 0;
        Rarity highest = null;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            Optional<LootLaw> law = lawOf(stack);
            if (law.isEmpty()) {
                continue;
            }
            int count = stack.getQuantity();
            if (!container.removeItemStackFromSlot(slot, stack, count).succeeded()) {
                continue;
            }
            removed.add(new Removed(container, slot, stack));
            sum = sum.plus(law.get().rollSum(count, ThreadLocalRandom.current()::nextDouble));
            opened += count;
            // One sound for the lot, that of the best bag in it.
            Rarity rarity = LootbagItem.rarity(stack).orElseThrow();
            highest = highest == null || rarity.compareTo(highest) > 0 ? rarity : highest;
        }
        if (removed.isEmpty()) {
            return Outcome.NOTHING;
        }
        return deposit(player, sum, opened, removed, highest);
    }

    /** A stack taken out, to put back if the deposit fails. */
    private record Removed(ItemContainer container, short slot, ItemStack stack) {
    }

    private Outcome deposit(UUID player, Coins sum, int opened, List<Removed> removed, Rarity rarity) {
        try {
            Obol.playerWallet(player).deposit(sum);
        } catch (RuntimeException e) {
            // Obol's store refused: the bags come back, nothing is lost.
            for (Removed r : removed) {
                r.container().addItemStackToSlot(r.slot(), r.stack());
            }
            logger.atSevere().withCause(e).log("Could not credit %s for %d lootbag(s), bags returned", player, opened);
            return Outcome.NOTHING;
        }
        return new Outcome(true, sum, opened, rarity);
    }

    /**
     * Credits {@code player} for {@code count} bags of {@code stack},
     * removing nothing: the caller's path already took the bags away.
     *
     * @return what happened, {@link Outcome#ok} false when the stack is
     *         not a lootbag or the deposit failed
     */
    public Outcome credit(UUID player, ItemStack stack, int count) {
        Optional<LootLaw> law = lawOf(stack);
        if (law.isEmpty()) {
            return Outcome.NOTHING;
        }
        int opened = Math.min(Math.max(count, 1), stack.getQuantity());
        Coins sum = law.get().rollSum(opened, ThreadLocalRandom.current()::nextDouble);
        try {
            Obol.playerWallet(player).deposit(sum);
        } catch (RuntimeException e) {
            // The bags are already gone: there is nothing to give back,
            // only to tell.
            logger.atSevere().withCause(e).log("Could not credit %s for %d lootbag(s) of %s picked up",
                    player, opened, stack.getItemId());
            return Outcome.NOTHING;
        }
        return new Outcome(true, sum, opened, LootbagItem.rarity(stack).orElseThrow());
    }

    /** {@return the law of the stack, its own or its rarity's, empty when it is not a lootbag} */
    public Optional<LootLaw> lawOf(ItemStack stack) {
        Optional<Rarity> rarity = LootbagItem.rarity(stack);
        if (rarity.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(LootbagItem.law(stack).orElseGet(() -> config.law(rarity.get())));
    }

    /**
     * Plays the opening sound of {@code outcome} to {@code player} alone,
     * for the paths the client does not run itself (pickup, chest, sweep):
     * the sound of the best bag opened ({@link Rarity#soundEvent}), the
     * one the click plays through the item's {@code Effects}. Nothing when
     * {@code outcome} opened no bag.
     */
    public static void chime(PlayerRef player, Outcome outcome) {
        if (outcome.rarity() == null) {
            return;
        }
        int index = SoundEvent.getAssetMap().getIndex(outcome.rarity().soundEvent());
        if (index != Integer.MIN_VALUE) {
            SoundUtil.playSoundEvent2dToPlayer(player, index, SoundCategory.SFX);
        }
    }
}
