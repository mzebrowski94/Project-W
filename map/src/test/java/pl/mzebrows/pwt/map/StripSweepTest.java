package pl.mzebrows.pwt.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Many strips, each four of the five biomes in an order of its own, held to every rule {@link StripFaults}
 * checks - houses and gatehouses among them. 64 seeds on every verify; {@code PWT_SWEEP=1} runs 2000 and prints a
 * summary when they all pass. A failure lists every failing seed and fault together.
 */
class StripSweepTest {

    private static final boolean BIG = "1".equals(System.getenv("PWT_SWEEP"));
    private static final int SEEDS = BIG ? 2000 : 64;
    private static final int DEALT = 4;

    /** The summary counts a biome's houses up to this, the last bucket holding this many and more. */
    private static final int MOST_HOUSES = 3;

    /** The deal's stream, apart from the map's so the order and the ground do not move together. */
    private static final long DEAL = 0x5EED_DEA1L;

    @Test
    void holdsEveryStripToTheRules() {
        List<String> faults = new ArrayList<>();
        int[] cols = new int[SEEDS];
        int[] passCounts = new int[StripTest.RULES.passesMax() + 1];
        int[] houseCounts = new int[MOST_HOUSES + 1];
        int gatehouses = 0;
        long nanos = 0L;
        for (int seed = 0; seed < SEEDS; seed++) {
            List<BiomeGround> order = deal(seed);

            long start = System.nanoTime();
            Strip.Laid laid = StripTest.lay(order, seed);
            nanos += System.nanoTime() - start;

            for (String fault : StripFaults.all(laid, StripTest.grounds(order), StripTest.RULES)) {
                faults.add("seed " + seed + ": " + fault);
            }
            cols[seed] = laid.map().cols();
            for (int b = 0; b < StripFaults.ridges(laid); b++) {
                passCounts[Math.min(passCounts.length - 1, StripFaults.passesThrough(laid, b))]++;
                gatehouses += StripFaults.gatehouse(laid, b) ? 1 : 0;
            }
            for (int p = 0; p < DEALT; p++) {
                houseCounts[Math.min(MOST_HOUSES, StripFaults.housesIn(laid, p))]++;
            }
        }

        assertThat(faults)
                .withFailMessage(
                        () -> String.join("\n", faults) + "\n" + faults.size() + " faults in " + SEEDS + " seeds")
                .isEmpty();
        if (BIG) {
            System.out.println(summary(cols, passCounts, houseCounts, gatehouses, nanos));
        }
    }

    /** Four of the five, none twice, in an order drawn from the seed. */
    private static List<BiomeGround> deal(int seed) {
        List<TestBiome> pool = new ArrayList<>(TestBiome.FIVE);
        Randomness rng = TestStream.seeded(seed ^ DEAL);
        List<BiomeGround> order = new ArrayList<>();
        for (int i = 0; i < DEALT; i++) {
            order.add(pool.remove(rng.nextInt(pool.size())).ground());
        }
        return order;
    }

    private static String summary(int[] cols, int[] passCounts, int[] houseCounts, int gatehouses, long nanos) {
        int[] sorted = cols.clone();
        Arrays.sort(sorted);
        return String.format(
                Locale.ROOT,
                "StripSweep %d seeds, 0 faults, %.1f ms/strip, cols %d/%d/%d, passes/ridge%s, houses/biome%s,"
                        + " gatehouses/strip %.2f",
                SEEDS,
                nanos / 1e6 / SEEDS,
                sorted[0],
                sorted[SEEDS / 2],
                sorted[SEEDS - 1],
                shares(passCounts, 1),
                shares(houseCounts, 0),
                gatehouses / (float) SEEDS);
    }

    /** Each count from {@code from} up as its share of them all: {@code " 1:61% 2:39%"}. */
    private static String shares(int[] counts, int from) {
        int all = Arrays.stream(counts).sum();
        StringBuilder shares = new StringBuilder();
        for (int n = from; n < counts.length; n++) {
            shares.append(String.format(Locale.ROOT, " %d:%.0f%%", n, 100f * counts[n] / all));
        }
        return shares.toString();
    }
}
