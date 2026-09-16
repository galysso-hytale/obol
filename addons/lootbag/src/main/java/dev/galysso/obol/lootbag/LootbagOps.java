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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What opening lootbags does: roll their law and put the coins in the
 * player's balance.
 *
 * <p>Two entries. {@link #open} is the click: it takes the bags out of
 * their slot first, then credits, so that no coin exists without bags
 * gone. {@link #credit} is the pickup: the game removes the item itself
 * (the item entity on the ground, the stack a harvest hands over), so
 * only the roll and the deposit are left. Both roll once per bag and make
 * one deposit of the sum, which is one line in Obol's HUD feed. Nothing
 * is said in the chat.</p>
 *
 * <p>A bag without a law (made by hand, or by a {@code Single} drop) rolls
 * the law its rarity has in {@code lootbag.json}, as its tooltip says.</p>
 *
 * <p>Everything runs on the world thread of the player (interactions,
 * packet handlers and inventory systems all do), Obol's locks cover the
 * rest.</p>
 */
public final class LootbagOps {

    /** The sound event of the pack played when bags open, whatever the path. */
    public static final String SOUND_EVENT = "SFX_Obol_Lootbag_Open";

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
     */
    public record Outcome(boolean ok, Coins amount, int count) {

        static final Outcome NOTHING = new Outcome(false, Coins.ZERO, 0);
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
        try {
            Obol.playerWallet(player).deposit(sum);
        } catch (RuntimeException e) {
            // Obol's store refused: the bags come back, nothing is lost.
            container.addItemStackToSlot(slot, expected.withQuantity(opened));
            logger.atSevere().withCause(e).log("Could not credit %s for %d lootbag(s) of %s, bags returned",
                    player, opened, expected.getItemId());
            return Outcome.NOTHING;
        }
        return new Outcome(true, sum, opened);
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
        return new Outcome(true, sum, opened);
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
     * Plays the opening sound to {@code player} alone, for the paths the
     * client does not run itself (pickup, chest): the same
     * {@link #SOUND_EVENT} the click plays through its {@code Effects}.
     */
    public static void chime(PlayerRef player) {
        int index = SoundEvent.getAssetMap().getIndex(SOUND_EVENT);
        if (index != Integer.MIN_VALUE) {
            SoundUtil.playSoundEvent2dToPlayer(player, index, SoundCategory.SFX);
        }
    }
}
