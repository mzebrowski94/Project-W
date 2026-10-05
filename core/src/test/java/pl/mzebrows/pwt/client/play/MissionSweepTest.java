package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.scenario.Mission;
import pl.mzebrows.pwt.sim.scenario.Placed;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * Many missions, each held to every rule {@link MissionFaults} checks - its strip's, placement's (G0068) and its
 * finale's (G0064) - on every tier: the tier never moves the ground (G0065), but it moves how much there is to place.
 * 64 seeds on every verify; {@code PWT_SWEEP=1} runs 2000 and prints a summary and a few seeds that deal each biome
 * first, each as its medium and its hard code - the hard one to measure the field on. A failure lists every failing
 * mission by its code. In {@code core}, beside {@link MissionCode}, so a seed is named by the code that plays it.
 */
class MissionSweepTest {

    private static final boolean BIG = "1".equals(System.getenv("PWT_SWEEP"));
    private static final int SEEDS = BIG ? 2000 : 64;

    /** The tier the summary counts creatures on and the budget is measured on: the one with the most to place. */
    private static final TierKind TIER = TierKind.HARD;

    /** Steps through the seeds a code can name, so the codes swept are spread over all of them. */
    private static final long STRIDE = 2_654_435_761L;

    /** Codes printed for each biome dealt first. */
    private static final int CODES = 3;

    /** Whole worlds built to measure a build against its budget, the median taken. */
    private static final int BUILDS = 16;

    /** A mission's whole world built, at the median, in milliseconds (missions ticket 11). */
    private static final double BUDGET_MS = 100.0;

    @Test
    void holdsEveryMissionToTheRules() {
        List<String> faults = new ArrayList<>();
        long nanos = 0L;
        int[] creatures = new int[SEEDS * 4];
        int counted = 0;
        EnumMap<BiomeKind, List<String>> codes = new EnumMap<>(BiomeKind.class);
        for (BiomeKind kind : BiomeKind.values()) {
            codes.put(kind, new ArrayList<>());
        }
        for (int i = 0; i < SEEDS; i++) {
            long seed = seed(i);
            Mission mission = null;
            for (TierKind tier : TierKind.values()) {
                SimConfig config =
                        Scenarios.mission(tier).configure(SimConfig.defaults().withSeed(seed));

                long start = System.nanoTime();
                Mission built = Mission.build(config, tier);
                nanos += System.nanoTime() - start;

                String code = new MissionCode(tier, seed).toString();
                for (String fault : MissionFaults.all(built, config)) {
                    faults.add(code + ": " + fault);
                }
                if (tier == TIER) {
                    mission = built;
                }
            }
            if (mission == null) {
                continue;
            }
            for (int p = 0; p < mission.order().size(); p++) {
                int position = p;
                if (counted < creatures.length) {
                    creatures[counted++] = (int) mission.placed().stream()
                            .filter(placed -> placed.biome() == position
                                    && (placed instanceof Placed.Loose || placed instanceof Placed.Nest))
                            .count();
                }
            }
            List<String> first = codes.get(mission.order().getFirst());
            if (first.size() < CODES) {
                first.add(new MissionCode(TierKind.MEDIUM, seed) + "|" + new MissionCode(TierKind.HARD, seed));
            }
        }

        assertThat(faults)
                .withFailMessage(
                        () -> String.join("\n", faults) + "\n" + faults.size() + " faults in " + SEEDS + " seeds")
                .isEmpty();
        if (BIG) {
            System.out.println(summary(nanos, Arrays.copyOf(creatures, counted), codes));
        }
    }

    /** What a player waits for: a mission's whole world, its config, map, simulation and contents. */
    @Test
    void buildsAMissionsWorldWithinItsBudget() {
        Scenario scenario = Scenarios.mission(TIER);
        double[] millis = new double[BUILDS];
        for (int i = 0; i < BUILDS; i++) {
            long start = System.nanoTime();
            Scenarios.world(scenario, SimConfig.defaults().withSeed(seed(i)));
            millis[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(millis);
        double median = millis[BUILDS / 2];
        System.out.printf(
                Locale.ROOT, "Mission build %.1f ms median, %.1f ms worst of %d%n", median, millis[BUILDS - 1], BUILDS);

        assertThat(median).isLessThanOrEqualTo(BUDGET_MS);
    }

    private static long seed(int i) {
        return Math.floorMod(i * STRIDE, MissionCode.SEEDS);
    }

    private static String summary(long nanos, int[] creatures, EnumMap<BiomeKind, List<String>> codes) {
        int[] sorted = creatures.clone();
        Arrays.sort(sorted);
        StringBuilder text = new StringBuilder(String.format(
                Locale.ROOT,
                "MissionSweep %d seeds on every tier, 0 faults, %.1f ms/build, creatures/biome on %s %d/%d/%d",
                SEEDS,
                nanos / 1e6 / SEEDS / TierKind.values().length,
                TIER,
                sorted[0],
                sorted[sorted.length / 2],
                sorted[sorted.length - 1]));
        for (BiomeKind kind : BiomeKind.values()) {
            text.append(String.format(Locale.ROOT, "%n  %s first: %s", kind, String.join(" ", codes.get(kind))));
        }
        return text.toString();
    }
}
