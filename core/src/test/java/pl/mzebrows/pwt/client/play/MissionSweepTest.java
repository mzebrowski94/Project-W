package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.MissionConfig;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.FinaleKind;
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
 *
 * <p><strong>Every layout of every finale, by name</strong> (G0087): beside the spread seeds it sweeps the first
 * {@link #PER_LAYOUT} seeds that end on each finale, layout and flip, picked by {@link Mission#drawn} - never trusting
 * the spread to happen on them.
 */
class MissionSweepTest {

    private static final boolean BIG = "1".equals(System.getenv("PWT_SWEEP"));
    private static final int SEEDS = BIG ? 2000 : 64;

    /** Seeds swept for each finale, layout and flip, on top of the spread. */
    private static final int PER_LAYOUT = BIG ? 16 : 2;

    /** Where those are looked for along the stride: past the big spread, so they are other seeds in either run. */
    private static final int SPREAD = 2000;

    /** The tier the summary counts creatures on and the budget is measured on: the one with the most to place. */
    private static final TierKind TIER = TierKind.HARD;

    /** Steps through the seeds a code can name, so the codes swept are spread over all of them. */
    private static final long STRIDE = 2_654_435_761L;

    /** Codes printed for each biome dealt first. */
    private static final int CODES = 3;

    /** Whole worlds built to measure a build against its budget, the median taken. */
    private static final int BUILDS = 16;

    /** Seeds swept at each end of the lengths: a long strip costs a dozen times a short one. */
    private static final int LONG_SEEDS = BIG ? 200 : 12;

    /** A mission's whole world built, at the median, in milliseconds (missions ticket 11). */
    private static final double BUDGET_MS = 100.0;

    @Test
    void holdsEveryMissionToTheRules() {
        List<String> faults = new ArrayList<>();
        long nanos = 0L;
        List<Long> seeds = seeds();
        int[] creatures = new int[seeds.size() * 4];
        int counted = 0;
        EnumMap<BiomeKind, List<String>> codes = new EnumMap<>(BiomeKind.class);
        for (BiomeKind kind : BiomeKind.values()) {
            codes.put(kind, new ArrayList<>());
        }
        Set<String> endings = new TreeSet<>();
        for (long seed : seeds) {
            Mission mission = null;
            for (TierKind tier : TierKind.values()) {
                SimConfig config =
                        Scenarios.mission(tier).configure(SimConfig.defaults().withSeed(seed));

                long start = System.nanoTime();
                Mission built = Mission.build(config, tier);
                nanos += System.nanoTime() - start;

                String code = new MissionCode(tier, 6, seed).toString();
                for (String fault : MissionFaults.all(built, config)) {
                    faults.add(code + ": " + fault);
                }
                if (tier == TIER) {
                    mission = built;
                }
                endings.add(ending(built.finale()));
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
                first.add(new MissionCode(TierKind.MEDIUM, 6, seed) + "|" + new MissionCode(TierKind.HARD, 6, seed)
                        + " " + mission.finale().kind().name().toLowerCase(Locale.ROOT));
            }
        }

        if (BIG) {
            // Before the verdict, so a run red on faults elsewhere still names codes that deal each biome first.
            System.out.println(summary(seeds.size(), faults.size(), nanos, Arrays.copyOf(creatures, counted), codes));
        }
        assertThat(faults)
                .withFailMessage(() ->
                        String.join("\n", faults) + "\n" + faults.size() + " faults in " + seeds.size() + " seeds")
                .isEmpty();
        assertThat(endings).as("every finale, layout and flip swept").containsAll(everyEnding());
    }

    /**
     * A strip of the shortest and the longest length holds every rule a six-biome one does - biomes met again are laid
     * and placed afresh (G0102) - and a mission's world builds at the longest within a generous budget.
     */
    @Test
    void holdsTheShortestAndTheLongestMissionToTheRules() {
        MissionConfig lengths = SimConfig.defaults().mission();
        List<String> faults = new ArrayList<>();
        double worstMillis = 0;
        for (int length : new int[] {lengths.biomesMin(), lengths.biomesMax()}) {
            for (int i = 0; i < LONG_SEEDS; i++) {
                long seed = seed(i);
                SimConfig config = Scenarios.mission(TIER, length)
                        .configure(SimConfig.defaults().withSeed(seed));
                Mission built = Mission.build(config, TIER);
                for (String fault : MissionFaults.all(built, config)) {
                    faults.add(new MissionCode(TIER, length, seed) + ": " + fault);
                }
                assertThat(built.order()).hasSize(length);
                long start = System.nanoTime();
                Scenarios.world(
                        Scenarios.mission(TIER, length), SimConfig.defaults().withSeed(seed));
                worstMillis = Math.max(worstMillis, (System.nanoTime() - start) / 1e6);
            }
        }
        System.out.printf(Locale.ROOT, "Longest mission build %.1f ms worst of %d%n", worstMillis, LONG_SEEDS);

        assertThat(faults).withFailMessage(() -> String.join("\n", faults)).isEmpty();
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

    /** The spread, then the first {@link #PER_LAYOUT} seeds past the big one that end on each finale, layout and flip. */
    private static List<Long> seeds() {
        List<Long> seeds = new ArrayList<>();
        for (int i = 0; i < SEEDS; i++) {
            seeds.add(seed(i));
        }
        Map<String, Integer> found = new TreeMap<>();
        Set<String> wanted = new TreeSet<>(everyEnding());
        for (int i = SPREAD; !wanted.isEmpty(); i++) {
            long seed = seed(i);
            String ending = ending(Mission.drawn(SimConfig.defaults().withSeed(seed)));
            if (wanted.contains(ending)) {
                seeds.add(seed);
                if (found.merge(ending, 1, Integer::sum) >= PER_LAYOUT) {
                    wanted.remove(ending);
                }
            }
        }
        return seeds;
    }

    /** Every finale, each of its layouts, flipped and not. */
    private static List<String> everyEnding() {
        List<String> endings = new ArrayList<>();
        for (FinaleKind kind : FinaleKind.values()) {
            int layouts = SimConfig.defaults().finales().of(kind).layoutCount();
            for (int layout = 0; layout < layouts; layout++) {
                endings.add(ending(new Mission.Finale(kind, false, layout, List.of(), List.of())));
                endings.add(ending(new Mission.Finale(kind, true, layout, List.of(), List.of())));
            }
        }
        return endings;
    }

    private static String ending(Mission.Finale finale) {
        return finale.kind() + " layout " + (finale.layout() + 1) + (finale.flipped() ? " flipped" : "");
    }

    private static String summary(
            int seeds, int faults, long nanos, int[] creatures, EnumMap<BiomeKind, List<String>> codes) {
        int[] sorted = creatures.clone();
        Arrays.sort(sorted);
        StringBuilder text = new StringBuilder(String.format(
                Locale.ROOT,
                "MissionSweep %d seeds on every tier, %d faults, %.1f ms/build, creatures/biome on %s %d/%d/%d",
                seeds,
                faults,
                nanos / 1e6 / seeds / TierKind.values().length,
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
