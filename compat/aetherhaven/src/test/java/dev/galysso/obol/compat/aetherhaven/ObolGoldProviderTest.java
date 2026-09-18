package dev.galysso.obol.compat.aetherhaven;

import com.hexvane.aetherhaven.economy.api.Transfer;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsListener;
import dev.galysso.obol.api.CoinsOverlay;
import dev.galysso.obol.api.Denomination;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.Wallet;
import dev.galysso.obol.api.WalletId;
import dev.galysso.obol.api.internal.ObolBackend;
import dev.galysso.obol.api.internal.ObolBackendHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** Obol "loaded" for {@code show}, which only checks that it is; nothing here is called. */
    private static final class InertBackend implements ObolBackend {
        @Override public Wallet wallet(WalletId id) { throw new UnsupportedOperationException(); }
        @Override public CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins) { throw new UnsupportedOperationException(); }
        @Override public CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet) { throw new UnsupportedOperationException(); }
        @Override public void addListener(CoinsListener listener) { throw new UnsupportedOperationException(); }
        @Override public boolean removeListener(CoinsListener listener) { throw new UnsupportedOperationException(); }
    }

    @AfterEach
    void unloadObol() {
        ObolBackendHolder.uninstall();
    }

    /** Each command as "Type selector data" on one line. */
    private static String commands(UICommandBuilder builder) {
        return Stream.of(builder.getCommands())
            .map(c -> c.type + " " + c.selector + " " + (c.data != null ? c.data : c.text))
            .reduce("", (a, b) -> a + b + "\n");
    }

    @Test
    void drawsTheAmountThroughTheRateAtTheSizeOfTheLine() {
        ObolBackendHolder.install(new InertBackend());
        UICommandBuilder builder = new UICommandBuilder();
        provider.show(builder, "#PriceLine #Price", 41L, 13);
        String queued = commands(builder);
        // 41 gold through the default rate is 2g 5s: gold and silver, no copper (a small page), the digits
        // at 13 in a column scaled from 30 to 18, the coins 15 with their margins scaled.
        assertTrue(queued.contains("#PriceLine #Price #ObolCoins #Gold #Count.Text {\"0\": \"2\"}\n"), queued);
        assertTrue(queued.contains("#PriceLine #Price #ObolCoins #Silver #Count.Text {\"0\": \"5\"}\n"), queued);
        assertTrue(!queued.contains("#Copper"), queued);
        assertTrue(queued.contains("#Gold #Count.Style.FontSize {\"0\": 13}\n"), queued);
        assertTrue(queued.contains("#Gold #Count.Anchor {\"0\": {\"Width\": 18}}\n"), queued);
        assertTrue(queued.contains("#Gold #Coin.Anchor {\"0\": {\"Left\": 2, \"Right\": 5, \"Height\": 15, \"Width\": 15}}\n"), queued);
    }

    @Test
    void drawsNothingForZeroButCopper() {
        ObolBackendHolder.install(new InertBackend());
        UICommandBuilder builder = new UICommandBuilder();
        provider.show(builder, "#Price", 0L, 16);
        String queued = commands(builder);
        assertTrue(queued.contains("#Price #ObolCoins #Copper #Count.Text {\"0\": \"0\"}\n"), queued);
        assertTrue(!queued.contains("#Silver") && !queued.contains("#Gold"), queued);
    }

    @Test
    void coinCountsAreWrittenThroughTheRateInWordsAndColours() {
        assertEquals("[#C0C0C0]50 silver", reads(provider.amount(10L)));
        assertEquals("[#FFD700]2 gold[#C0C0C0] 5 silver", reads(provider.amount(41L)));
        assertEquals("[#B87333]0 copper", reads(provider.amount(0L)));
    }
}
