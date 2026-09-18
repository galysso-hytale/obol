package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.economy.api.Transfer;
import com.hypixel.hytale.server.core.Message;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObolGoldProviderTest {

    /** A wallet that is only a number, transfers included. */
    private static final class MemoryWallet implements Wallet {
        Coins balance = Coins.ZERO;

        @Override public WalletId id() { return new WalletId("test", "memory"); }
        @Override public Coins balance() { return balance; }
        @Override public boolean canAfford(Coins amount) { return balance.covers(amount); }
        @Override public Coins deposit(Coins amount) { balance = balance.plus(amount); return balance; }
        @Override public boolean withdraw(Coins amount) {
            var left = balance.minus(amount);
            left.ifPresent(c -> balance = c);
            return left.isPresent();
        }
        @Override public boolean transferTo(Wallet to, Coins amount) {
            if (!withdraw(amount)) {
                return false;
            }
            to.deposit(amount);
            return true;
        }
        @Override public void clear() { balance = Coins.ZERO; }
    }

    private final ObolGoldProvider provider = new ObolGoldProvider(Rate.DEFAULT, (source, amount) -> List.of());
    private final MemoryWallet player = new MemoryWallet();
    private final MemoryWallet treasury = new MemoryWallet();
    private final ObolGoldAccount from = new ObolGoldAccount(player, Rate.DEFAULT);
    private final ObolGoldAccount to = new ObolGoldAccount(treasury, Rate.DEFAULT);

    /** What a message reads as, each coloured span in its colour: "[#C0C0C0]4 silver[#B87333] 32 copper". */
    private static String reads(Message message) {
        StringBuilder out = new StringBuilder();
        if (message.getColor() != null) {
            out.append('[').append(message.getColor()).append(']');
        }
        if (message.getRawText() != null) {
            out.append(message.getRawText());
        }
        for (Message child : message.getChildren()) {
            out.append(reads(child));
        }
        return out.toString();
    }

    @Test
    void transferMovesObolNotationAtObolPrecision() {
        player.balance = Coins.of(Denomination.GOLD, 3);
        Transfer moved = provider.transfer(from, to, "4s 32c");
        assertEquals(Transfer.Outcome.MOVED, moved.outcome());
        assertEquals("[#C0C0C0]4 silver[#B87333] 32 copper", reads(moved.moved()));
        assertEquals(Coins.ofCopper(432), treasury.balance);
        assertEquals(Coins.of(Denomination.GOLD, 3).minus(Coins.ofCopper(432)).orElseThrow(), player.balance);
    }

    @Test
    void bareNumbersAreCopperAsEverywhereInObol() {
        player.balance = Coins.of(Denomination.GOLD, 3);
        assertEquals(Transfer.Outcome.MOVED, provider.transfer(from, to, "432").outcome());
        assertEquals(Coins.ofCopper(432), treasury.balance);
    }

    @Test
    void blankTextMovesTheWholeWallet() {
        player.balance = Coins.ofCopper(1234);
        assertEquals(Transfer.Outcome.MOVED, provider.transfer(from, to, "").outcome());
        assertEquals(Coins.ZERO, player.balance);
        assertEquals(Coins.ofCopper(1234), treasury.balance);
        assertEquals(Transfer.Outcome.NOT_AVAILABLE, provider.transfer(from, to, null).outcome());
    }

    @Test
    void refusesWhatIsNotAnAmount() {
        player.balance = Coins.of(Denomination.GOLD, 3);
        for (String text : new String[] {"ten", "1g1g", "0", "0s", "-5"}) {
            assertEquals(Transfer.Outcome.NOT_AN_AMOUNT, provider.transfer(from, to, text).outcome(), text);
        }
        assertEquals(Coins.of(Denomination.GOLD, 3), player.balance);
    }

    @Test
    void refusesMoreThanTheWalletHolds() {
        player.balance = Coins.of(Denomination.SILVER, 20);
        assertEquals(Transfer.Outcome.NOT_AVAILABLE, provider.transfer(from, to, "21s").outcome());
        assertEquals(Coins.of(Denomination.SILVER, 20), player.balance);
        assertEquals(Coins.ZERO, treasury.balance);
    }

    @Test
    void accountBalanceIsWrittenExactly() {
        player.balance = Coins.ofCopper(32504);
        assertEquals("[#FFD700]3 gold[#C0C0C0] 25 silver[#B87333] 4 copper", reads(provider.amount(from)));
        // While the coin count Aetherhaven spends from is rounded down.
        assertEquals(65, from.balance());
    }

    @Test
    void coinCountsAreWrittenThroughTheRateInWordsAndColours() {
        assertEquals("[#C0C0C0]50 silver", reads(provider.amount(10L)));
        assertEquals("[#FFD700]2 gold[#C0C0C0] 5 silver", reads(provider.amount(41L)));
        assertEquals("[#B87333]0 copper", reads(provider.amount(0L)));
    }
}
