package dev.galysso.obol.lootbag;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.lootbag.LootLaw.Distribution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootLawTest {

    private static final int ROLLS = 200_000;

    private static Coins c(String text) {
        try {
            return Coins.parse(text);
        } catch (Exception e) {
            throw new AssertionError(text, e);
        }
    }

    private static DoubleSupplier seeded() {
        Random random = new Random(42);
        return random::nextDouble;
    }

    // Construction and validation

    @Test
    void factoriesBuildTheFourLaws() {
        assertEquals(Distribution.Fixed, LootLaw.fixed(c("5g")).distribution());
        assertEquals(Distribution.Uniform, LootLaw.uniform(c("1s"), c("5s")).distribution());
        assertEquals(Distribution.Triangular, LootLaw.triangular(c("1s"), c("5s"), c("2s")).distribution());
        assertEquals(Distribution.LogUniform, LootLaw.logUniform(c("1s"), c("5s")).distribution());
        assertTrue(LootLaw.fixed(c("5g")).isFixed());
        assertFalse(LootLaw.uniform(c("1s"), c("5s")).isFixed());
    }

    @Test
    void rejectsAnInvalidLaw() {
        assertThrows(IllegalArgumentException.class, () -> LootLaw.uniform(c("5s"), c("1s")));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.triangular(c("1s"), c("5s"), c("6s")));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.triangular(c("1s"), c("5s"), c("50c")));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.logUniform(c("0c"), c("5s")));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.uniform(c("1s"), c("5s")).withStep(c("0c")));
        assertThrows(IllegalArgumentException.class,
                () -> new LootLaw(Distribution.Uniform, c("1s"), c("5s"), c("2s"), null));
        assertThrows(IllegalArgumentException.class,
                () -> new LootLaw(Distribution.Fixed, c("1s"), c("5s"), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new LootLaw(Distribution.Triangular, c("1s"), c("5s"), null, null));
    }

    @Test
    void everyRarityHasADefault() {
        for (Rarity rarity : Rarity.values()) {
            LootLaw law = LootLaw.defaultFor(rarity);
            assertEquals(Distribution.Triangular, law.distribution(), rarity.name());
        }
        assertEquals(LootLaw.triangular(c("5s"), c("50s"), c("12s")), LootLaw.defaultFor(Rarity.Rare));
        // A tenfold scale: each rarity's floor is the previous one's ceiling.
        Rarity[] all = Rarity.values();
        for (int i = 1; i < all.length; i++) {
            assertEquals(LootLaw.defaultFor(all[i - 1]).max(), LootLaw.defaultFor(all[i]).min(), all[i].name());
        }
    }

    // The step

    @Test
    void defaultStepIsOneCoinBelowTheLargestTierOfMax() {
        assertEquals(c("1c"), LootLaw.defaultStep(c("50c")));
        assertEquals(c("1c"), LootLaw.defaultStep(c("50s")));
        assertEquals(c("1s"), LootLaw.defaultStep(c("5g")));
        assertEquals(c("1s"), LootLaw.defaultStep(c("50g")));
        assertEquals(c("1g"), LootLaw.defaultStep(c("2m")));
        assertEquals(c("1c"), LootLaw.uniform(c("5s"), c("50s")).effectiveStep());
        assertEquals(c("5c"), LootLaw.uniform(c("5s"), c("50s")).withStep(c("5c")).effectiveStep());
    }

    // Rolling

    @Test
    void fixedAlwaysGivesItsAmount() {
        LootLaw law = LootLaw.fixed(c("2g 35s"));
        DoubleSupplier random = seeded();
        for (int i = 0; i < 100; i++) {
            assertEquals(c("2g 35s"), law.roll(random));
        }
    }

    @Test
    void rollsStayInTheRangeOnMultiplesOfTheStep() {
        List<LootLaw> laws = List.of(
                LootLaw.uniform(c("5s"), c("50s")),
                LootLaw.triangular(c("50s"), c("5g"), c("1g 20s")),
                LootLaw.logUniform(c("5s"), c("5g")),
                LootLaw.uniform(c("7c"), c("9c")).withStep(c("3c")),
                LootLaw.triangular(c("1s"), c("1s"), c("1s")));
        for (LootLaw law : laws) {
            long step = law.effectiveStep().copper();
            DoubleSupplier random = seeded();
            for (int i = 0; i < 10_000; i++) {
                Coins roll = law.roll(random);
                assertTrue(roll.compareTo(law.min()) >= 0, law + " gave " + roll);
                assertTrue(roll.compareTo(law.max()) <= 0, law + " gave " + roll);
                // Below the minimum a roll is brought up to it, which may
                // not be a multiple. Anything else is.
                assertTrue(roll.equals(law.min()) || roll.copper() % step == 0, law + " gave " + roll);
            }
        }
    }

    @Test
    void extremesOfTheSourceGiveTheBounds() {
        LootLaw uniform = LootLaw.uniform(c("5s"), c("50s"));
        assertEquals(c("5s"), uniform.roll(() -> 0.0));
        assertEquals(c("50s"), uniform.roll(() -> Math.nextDown(1.0)));
        LootLaw triangular = LootLaw.triangular(c("5s"), c("50s"), c("12s"));
        assertEquals(c("5s"), triangular.roll(() -> 0.0));
        assertEquals(c("50s"), triangular.roll(() -> 1.0));
        LootLaw log = LootLaw.logUniform(c("5s"), c("50s"));
        assertEquals(c("5s"), log.roll(() -> 0.0));
        assertEquals(c("50s"), log.roll(() -> 1.0));
    }

    @Test
    void uniformReachesEveryMultipleWithTheSameChance() {
        LootLaw law = LootLaw.uniform(c("1c"), c("4c"));
        int[] hits = new int[5];
        DoubleSupplier random = seeded();
        for (int i = 0; i < ROLLS; i++) {
            hits[(int) law.roll(random).copper()]++;
        }
        for (int amount = 1; amount <= 4; amount++) {
            assertEquals(ROLLS / 4.0, hits[amount], ROLLS * 0.01, "amount " + amount);
        }
    }

    @Test
    void triangularMeanIsTheThirdOfTheSum() {
        // Mean of a triangular law: (min + mode + max) / 3.
        LootLaw law = LootLaw.triangular(c("5s"), c("50s"), c("12s"));
        double expected = (500 + 1200 + 5000) / 3.0;
        assertEquals(expected, mean(law), expected * 0.01);
    }

    @Test
    void logUniformMedianIsTheGeometricMean() {
        LootLaw law = LootLaw.logUniform(c("5s"), c("5g"));
        double expected = Math.sqrt(500.0 * 50_000.0);
        assertEquals(expected, median(law), expected * 0.02);
    }

    @Test
    void sumOfAStackAddsUp() {
        assertEquals(c("2g"), LootLaw.fixed(c("50s")).rollSum(4, seeded()));
        assertEquals(Coins.ZERO, LootLaw.fixed(c("50s")).rollSum(0, seeded()));
        Coins sum = LootLaw.uniform(c("1s"), c("2s")).rollSum(10, seeded());
        assertTrue(sum.compareTo(c("10s")) >= 0 && sum.compareTo(c("20s")) <= 0, sum.toString());
    }

    // The written form

    @Test
    void entriesAreWrittenInAFixedOrder() {
        Map<String, String> entries = LootLaw.triangular(c("5s"), c("50s"), c("12s")).withStep(c("1s")).toEntries();
        assertEquals(List.of("Distribution", "Min", "Max", "Mode", "Step"), new ArrayList<>(entries.keySet()));
        assertEquals(List.of("Triangular", "5s", "50s", "12s", "1s"), new ArrayList<>(entries.values()));
        assertEquals(Map.of("Distribution", "Fixed", "Amount", "2g 35s"), LootLaw.fixed(c("2g 35s")).toEntries());
        assertEquals(List.of("Distribution", "Min", "Max"),
                new ArrayList<>(LootLaw.logUniform(c("1g"), c("9g")).toEntries().keySet()));
    }

    @Test
    void sameLawSameEntries() {
        LootLaw a = LootLaw.triangular(c("50s"), c("5g"), c("1g 20s"));
        LootLaw b = LootLaw.of(Map.of("Distribution", "Triangular", "Min", "5000", "Max", "5 gold", "Mode", "120s"));
        assertEquals(a, b);
        assertEquals(new ArrayList<>(a.toEntries().entrySet()), new ArrayList<>(b.toEntries().entrySet()));
    }

    @Test
    void entriesRoundTrip() {
        for (LootLaw law : List.of(
                LootLaw.fixed(c("2g 35s")),
                LootLaw.uniform(c("5s"), c("50s")),
                LootLaw.uniform(c("5s"), c("50s")).withStep(c("5c")),
                LootLaw.triangular(c("5s"), c("50s"), c("12s")),
                LootLaw.logUniform(c("1g"), c("9g")))) {
            assertEquals(law, LootLaw.of(law.toEntries()));
        }
    }

    @Test
    void distributionIsInferredWhenLeftOut() {
        assertEquals(LootLaw.fixed(c("5g")), LootLaw.of(Map.of("Amount", "5g")));
        assertEquals(LootLaw.triangular(c("1s"), c("5s"), c("2s")),
                LootLaw.of(Map.of("Min", "1s", "Max", "5s", "Mode", "2s")));
        assertEquals(LootLaw.uniform(c("1s"), c("5s")), LootLaw.of(Map.of("Min", "1s", "Max", "5s")));
        assertEquals(LootLaw.uniform(c("1s"), c("5s")), LootLaw.of(Map.of("Min", "1s", "Max", "5s", "Mode", "")));
        assertNull(LootLaw.of(Map.of("Min", "1s", "Max", "5s")).step());
    }

    @Test
    void rejectsBadEntries() {
        assertThrows(IllegalArgumentException.class, () -> LootLaw.of(Map.of("Min", "1s")));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.of(Map.of("Min", "1s", "Max", "five")));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.of(Map.of("Distribution", "Gaussian", "Min", "1s", "Max", "5s")));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.of(Map.of("Distribution", "Fixed", "Min", "1s", "Max", "5s")));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.of(Map.of("Amount", "5g", "Max", "9g")));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.of(Map.of("Min", "1s", "Max", "5s", "Mean", "2s")));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.of(Map.of()));
    }

    // The one-line form

    @Test
    void parsesTheOneLineForm() {
        assertEquals(LootLaw.fixed(c("2g 35s")), LootLaw.parse("2g 35s"));
        assertEquals(LootLaw.fixed(c("5g")), LootLaw.parse("fixed 5g"));
        assertEquals(LootLaw.uniform(c("1g"), c("5g")), LootLaw.parse("uniform 1g 5g"));
        assertEquals(LootLaw.triangular(c("5s"), c("50s"), c("12s")), LootLaw.parse("Triangular 5s 50s 12s"));
        assertEquals(LootLaw.triangular(c("50s"), c("5g"), c("1g 20s")).withStep(c("1s")),
                LootLaw.parse("triangular 50s 5g 1g20s step 1s"));
        assertEquals(LootLaw.logUniform(c("1g"), c("9g")), LootLaw.parse("  loguniform 1g 9g "));
    }

    @Test
    void rejectsABadOneLineForm() {
        assertThrows(IllegalArgumentException.class, () -> LootLaw.parse(""));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.parse("uniform"));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.parse("uniform 1g"));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.parse("uniform 1g 5g 3g"));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.parse("gaussian 1g 5g"));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.parse("uniform 1g 5g step"));
        assertThrows(IllegalArgumentException.class, () -> LootLaw.parse("uniform 5g 1g"));
    }

    @Test
    void toStringIsTheOneLineForm() {
        for (LootLaw law : List.of(
                LootLaw.fixed(c("2g 35s")),
                LootLaw.uniform(c("5s"), c("50s")).withStep(c("5c")),
                LootLaw.triangular(c("50s"), c("5g"), c("1g 20s")),
                LootLaw.logUniform(c("1g"), c("9g")))) {
            assertEquals(law, LootLaw.parse(law.toString()), law.toString());
        }
        assertEquals("triangular 50s 5g 1g20s", LootLaw.triangular(c("50s"), c("5g"), c("1g 20s")).toString());
    }

    private static double mean(LootLaw law) {
        DoubleSupplier random = seeded();
        double sum = 0;
        for (int i = 0; i < ROLLS; i++) {
            sum += law.roll(random).copper();
        }
        return sum / ROLLS;
    }

    private static double median(LootLaw law) {
        DoubleSupplier random = seeded();
        long[] rolls = new long[ROLLS];
        for (int i = 0; i < ROLLS; i++) {
            rolls[i] = law.roll(random).copper();
        }
        java.util.Arrays.sort(rolls);
        return rolls[ROLLS / 2];
    }
}
