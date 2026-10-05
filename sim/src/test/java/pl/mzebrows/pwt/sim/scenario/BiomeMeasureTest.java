package pl.mzebrows.pwt.sim.scenario;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.BiomeDef;
import pl.mzebrows.pwt.sim.Roster;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Not a test: the runner a {@code new-biome} port is tuned with. {@code PWT_MEASURE=1} builds each Adventure a biome
 * was ported from, measures the band it came from - rock, thicket and patches as shares of it, and its outcrops'
 * count and size - and prints them beside the same measures over the missions that deal that biome, p10 / median /
 * p90, each taken inside the ridges' faces. Tune a block until its medians sit within a point of the measured ones.
 *
 * <p>It also prints the room each biome leaves for creatures (G0070): every roster swapped for more than fits, and
 * how many holes and loose creatures placement puts down - least, p1, p10 and median - dealt first and later. A
 * block's counts must fit on hard at the worst seed: x1.25 its most if dealt first, x1.625 at the last position.
 */
@EnabledIfEnvironmentVariable(named = "PWT_MEASURE", matches = "1")
class BiomeMeasureTest {

    /** Missions measured, on medium: the tier never moves the ground (G0065). */
    private static final int SEEDS = 256;

    /** Where each ported biome came from: a scenario and the band of its columns, first and last. A new port adds a row. */
    private static final Map<BiomeKind, Port> PORTS = ports();

    /** Missions the room is measured over: its worst seeds are what a block must fit. */
    private static final int ROOM_SEEDS = 2000;

    /** A roster line's count past anything a biome holds. */
    private static final int MORE_THAN_FITS = 30;

    @Test
    void printsEachBiomeBesideWhereItWasPortedFrom() {
        SimConfig config = SimConfig.defaults();
        Map<BiomeKind, List<double[]>> missions = new EnumMap<>(BiomeKind.class);
        for (BiomeKind kind : BiomeKind.values()) {
            missions.put(kind, new ArrayList<>());
        }
        int rough = config.mission().ground().ridgeRough();
        for (long seed = 0; seed < SEEDS; seed++) {
            Mission mission = Mission.build(config.withSeed(seed), TierKind.MEDIUM);
            TileMap map = mission.laid().map();
            for (int p = 0; p < mission.order().size(); p++) {
                BiomeKind kind = mission.order().get(p);
                TileRect rect = mission.laid().biomes().get(p);
                int from = rect.col() + (p == 0 ? 0 : rough);
                TileRect inside = new TileRect(from, rect.row(), rect.endCol() - rough - from, rect.height());
                missions.get(kind)
                        .add(measure(
                                map, inside, config.biomes().of(kind).ground().base()));
            }
        }

        StringBuilder text = new StringBuilder("BiomeMeasure, ")
                .append(SEEDS)
                .append(" missions on medium; shares in %, outcrops as count and mean tiles");
        for (BiomeKind kind : BiomeKind.values()) {
            Port port = PORTS.get(kind);
            double[] measured = port == null ? null : port.measure();
            text.append(String.format(
                    Locale.ROOT,
                    "%n%s%s",
                    kind,
                    port == null
                            ? " (new, no port)"
                            : " from " + port.scenario() + " columns " + port.first() + "-" + port.last()));
            String[] names = {"rock", "thicket", "patch", "outcrops", "outcrop size"};
            for (int m = 0; m < names.length; m++) {
                text.append(String.format(
                        Locale.ROOT,
                        "%n  %-12s %8s   mission %s",
                        names[m],
                        measured == null ? "-" : String.format(Locale.ROOT, "%.1f", measured[m]),
                        spread(missions.get(kind), m)));
            }
        }
        System.out.println(text);
    }

    @Test
    void printsTheRoomEachBiomeLeavesForCreatures() {
        SimConfig shipped = SimConfig.defaults();
        Defs<BiomeKind, BiomeDef> overloaded = shipped.biomes();
        for (BiomeKind kind : BiomeKind.values()) {
            overloaded = overloaded.map(
                    kind,
                    biome -> new BiomeDef(
                            biome.ground(),
                            List.of(
                                    new Roster.Nest(UnitKind.GRUNT, MORE_THAN_FITS, MORE_THAN_FITS),
                                    new Roster.Loose(UnitKind.GRUNT, MORE_THAN_FITS * 5, MORE_THAN_FITS * 5)),
                            biome.furniture()));
        }
        Map<String, List<double[]>> room = new java.util.TreeMap<>();
        for (long seed = 0; seed < ROOM_SEEDS; seed++) {
            SimConfig config = shipped.withSeed(seed).withBiomes(overloaded);
            Mission mission = Mission.build(config, TierKind.MEDIUM);
            for (int p = 0; p < mission.order().size(); p++) {
                int position = p;
                double holes = mission.placed().stream()
                        .filter(placed -> placed.biome() == position && placed instanceof Placed.Nest)
                        .count();
                double loose = mission.placed().stream()
                        .filter(placed -> placed.biome() == position && placed instanceof Placed.Loose)
                        .count();
                room.computeIfAbsent(
                                mission.order().get(p) + (p == 0 ? " dealt first" : " dealt later"),
                                k -> new ArrayList<>())
                        .add(new double[] {holes, loose});
            }
        }
        StringBuilder text =
                new StringBuilder("BiomeRoom, ").append(ROOM_SEEDS).append(" missions, least / p1 / p10 / median");
        room.forEach((where, measures) -> text.append(String.format(
                Locale.ROOT, "%n%-22s holes %s   loose %s", where, least(measures, 0), least(measures, 1))));
        System.out.println(text);
    }

    private static String least(List<double[]> measures, int which) {
        double[] values = measures.stream().mapToDouble(m -> m[which]).sorted().toArray();
        return String.format(
                Locale.ROOT,
                "%.0f / %.0f / %.0f / %.0f",
                values[0],
                values[values.length / 100],
                values[values.length / 10],
                values[values.length / 2]);
    }

    /** Rock, thicket and patch shares in %, then how many outcrops and their mean size in tiles. */
    private static double[] measure(TileMap map, TileRect band, Terrain base) {
        int rock = 0;
        int thicket = 0;
        int patch = 0;
        for (int row = band.row(); row < band.endRow(); row++) {
            for (int col = band.col(); col < band.endCol(); col++) {
                Terrain terrain = map.terrainAtTile(col, row);
                if (terrain == Terrain.ROCK) {
                    rock++;
                } else if (terrain == Terrain.THICKET) {
                    thicket++;
                } else if (terrain.passable() && terrain != base) {
                    patch++;
                }
            }
        }
        int[] pieces = outcrops(map, band);
        double tiles = band.tileCount() / 100.0;
        return new double[] {
            rock / tiles,
            thicket / tiles,
            patch / tiles,
            pieces[0],
            pieces[0] == 0 ? 0.0 : pieces[1] / (double) pieces[0]
        };
    }

    /** The pieces of rock lying wholly inside the band, four-connected: how many, and how many tiles in all. */
    private static int[] outcrops(TileMap map, TileRect band) {
        boolean[] seen = new boolean[band.tileCount()];
        int count = 0;
        int tiles = 0;
        int[] stack = new int[band.tileCount()];
        for (int start = 0; start < seen.length; start++) {
            if (seen[start] || !rock(map, band, start)) {
                continue;
            }
            int size = 0;
            boolean inside = true;
            int depth = 0;
            stack[depth++] = start;
            seen[start] = true;
            while (depth > 0) {
                int at = stack[--depth];
                size++;
                int col = at % band.width();
                int row = at / band.width();
                int[][] around = {{col - 1, row}, {col + 1, row}, {col, row - 1}, {col, row + 1}};
                for (int[] next : around) {
                    if (next[0] < 0 || next[1] < 0 || next[0] >= band.width() || next[1] >= band.height()) {
                        inside &= map.terrainAtTile(band.col() + next[0], band.row() + next[1]) != Terrain.ROCK;
                        continue;
                    }
                    int index = next[1] * band.width() + next[0];
                    if (!seen[index] && rock(map, band, index)) {
                        seen[index] = true;
                        stack[depth++] = index;
                    }
                }
            }
            if (inside) {
                count++;
                tiles += size;
            }
        }
        return new int[] {count, tiles};
    }

    private static boolean rock(TileMap map, TileRect band, int index) {
        return map.terrainAtTile(band.col() + index % band.width(), band.row() + index / band.width()) == Terrain.ROCK;
    }

    private static String spread(List<double[]> measures, int which) {
        if (measures.isEmpty()) {
            return "never dealt";
        }
        double[] values = measures.stream().mapToDouble(m -> m[which]).sorted().toArray();
        return String.format(
                Locale.ROOT,
                "%.1f / %.1f / %.1f",
                values[values.length / 10],
                values[values.length / 2],
                values[values.length * 9 / 10]);
    }

    private static Map<BiomeKind, Port> ports() {
        Map<BiomeKind, Port> ports = new EnumMap<>(BiomeKind.class);
        ports.put(BiomeKind.PLAIN, new Port("Adventure", 1, 29, Terrain.PLAIN));
        ports.put(BiomeKind.QUARRY, new Port("Adventure", 91, 119, Terrain.DUST));
        ports.put(BiomeKind.ANTHILL, new Port("Adventure 2", 61, 88, Terrain.ORGANIC));
        ports.put(BiomeKind.BRAKE, new Port("Adventure 2", 151, 178, Terrain.PLAIN));
        return ports;
    }

    /** A band of columns of a hand-laid level, inside its border rows, and the ground it was painted on. */
    private record Port(String scenario, int first, int last, Terrain base) {

        double[] measure() {
            TileMap map = Scenarios.blank(Scenarios.named(scenario), SimConfig.defaults())
                    .map();
            return BiomeMeasureTest.measure(map, new TileRect(first, 1, last - first + 1, map.rows() - 2), base);
        }
    }
}
