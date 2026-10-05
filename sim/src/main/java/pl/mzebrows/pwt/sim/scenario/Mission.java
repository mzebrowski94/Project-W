package pl.mzebrows.pwt.sim.scenario;

import java.util.ArrayList;
import java.util.List;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.map.Crags;
import pl.mzebrows.pwt.map.Houses;
import pl.mzebrows.pwt.map.Outcrops;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Strip;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.BiomeDef;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.RngStream;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.TierKind;

/**
 * A mission as it is built from its seed and tier, before anything is spawned (G0065): the biomes dealt, the strip
 * they are laid in, and everything placement put down on it (G0068). One seed is one mission on every tier - the
 * seed deals the order, lays the ground and chooses where things stand, and the tier moves only how many creatures
 * there are.
 *
 * <p>Streams (G0065): the strip derives from the map stream; the order, the counts and every placement pass derive
 * from {@link RngStream#MISSION}, per sub-concern and per position, so retuning one biome's block moves no other
 * biome, and hard's bigger counts move nothing put down before the creatures.
 *
 * @param tier how hard it is
 * @param seed the mission seed: the simulation's
 * @param order the biomes dealt, west to east
 * @param laid the strip they are laid in, and the bare ground past its last ridge
 * @param placed everything placement put down, biome by biome in the order of its passes
 * @param shortfalls every pass that put down fewer than it was dealt - a generator fault, which the sweep fails
 */
public record Mission(
        TierKind tier,
        long seed,
        List<BiomeKind> order,
        Strip.Laid laid,
        List<Placed> placed,
        List<String> shortfalls) {

    private static final long ORDER = 0x2F5B_4C81_D7A3_906EL;
    private static final long PLACEMENT = 0x8D1E_63F7_0BA2_C459L;

    /** How many deals are tried before a pool that cannot deal its count is refused. */
    private static final int DEALS = 64;

    /**
     * The ground past the last ridge while missions have no finale: the redoubt's, bare. Scaffolding until the
     * finale is built (missions stage 8), listed in {@code alpha-phase.md}.
     */
    private static final BiomeGround BARE =
            new BiomeGround(34, 34, 3, Terrain.DUST, Terrain.ASH, Houses.off(), List.of(), Crags.off(), Outcrops.off());

    public Mission {
        order = List.copyOf(order);
        placed = List.copyOf(placed);
        shortfalls = List.copyOf(shortfalls);
    }

    /** The mission {@code config.seed()} names on this tier, from a config the tier has been applied to or not. */
    public static Mission build(SimConfig config, TierKind tier) {
        return build(config, tier, Rng.stream(config.seed(), RngStream.MAP));
    }

    /** Every ground the strip laid, west to east, the ground past the last ridge last. */
    public List<BiomeGround> grounds(SimConfig config) {
        List<BiomeGround> grounds = new ArrayList<>();
        for (BiomeKind kind : order) {
            grounds.add(config.biomes().of(kind).ground());
        }
        grounds.add(BARE);
        return grounds;
    }

    /**
     * The biomes {@code config.seed()} deals, west to east: none twice and never two of one base side by side. A
     * dead end deals again from where the stream has got to.
     */
    public static List<BiomeKind> deal(SimConfig config) {
        return deal(config.biomes(), config.mission().biomes(), mission(config).derive(ORDER));
    }

    /** {@link #build(SimConfig, TierKind)} on a map stream already derived, as {@code Scenarios.world} hands one. */
    static Mission build(SimConfig config, TierKind tier, Randomness map) {
        List<BiomeKind> order = deal(config);
        List<BiomeGround> grounds = new ArrayList<>();
        for (BiomeKind kind : order) {
            grounds.add(config.biomes().of(kind).ground());
        }
        // The start lies inside the first biome whatever width it is dealt, against the border.
        TileRect start = config.spawn()
                .on(1 + grounds.getFirst().widthMin(), config.mission().ground().rows());
        Strip.Laid laid = Strip.lay(
                config.mission().ground(),
                grounds,
                BARE,
                start,
                map,
                config.map().tileSize());

        Placement placement = new Placement(config, laid);
        Randomness placing = mission(config).derive(PLACEMENT);
        for (int position = 0; position < order.size(); position++) {
            BiomeKind kind = order.get(position);
            BiomeDef def = config.biomes().of(kind);
            placement.biome(
                    position,
                    kind,
                    def,
                    config.mission().pressure(tier, position),
                    config.mission().pressures(position),
                    position == order.size() - 1,
                    placing.derive(position));
        }
        return new Mission(tier, config.seed(), order, laid, placement.placed(), placement.shortfalls());
    }

    private static Randomness mission(SimConfig config) {
        return Rng.stream(config.seed(), RngStream.MISSION);
    }

    private static List<BiomeKind> deal(Defs<BiomeKind, BiomeDef> pool, int count, Randomness rng) {
        for (int attempt = 0; attempt < DEALS; attempt++) {
            List<BiomeKind> left = new ArrayList<>(List.of(BiomeKind.values()));
            List<BiomeKind> order = new ArrayList<>();
            while (order.size() < count) {
                List<BiomeKind> fit = new ArrayList<>();
                for (BiomeKind kind : left) {
                    if (order.isEmpty() || base(pool, kind) != base(pool, order.getLast())) {
                        fit.add(kind);
                    }
                }
                if (fit.isEmpty()) {
                    break;
                }
                BiomeKind next = fit.get(rng.nextInt(fit.size()));
                order.add(next);
                left.remove(next);
            }
            if (order.size() == count) {
                return List.copyOf(order);
            }
        }
        throw new IllegalStateException(
                "the biome pool cannot deal " + count + " biomes with no base twice side by side");
    }

    private static Terrain base(Defs<BiomeKind, BiomeDef> pool, BiomeKind kind) {
        return pool.of(kind).ground().base();
    }
}
