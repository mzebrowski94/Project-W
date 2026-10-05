package pl.mzebrows.pwt.sim.scenario;

import java.util.ArrayList;
import java.util.List;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Strip;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.BiomeDef;
import pl.mzebrows.pwt.sim.FinaleDef;
import pl.mzebrows.pwt.sim.Hold;
import pl.mzebrows.pwt.sim.LairLine;
import pl.mzebrows.pwt.sim.Offset;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.RngStream;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.WaveLine;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.FinaleKind;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.Wave;

/**
 * A mission as it is built from its seed and tier, before anything is spawned (G0065): the biomes dealt, the strip
 * they are laid in, everything placement put down on it (G0068), and the finale's set piece (G0064). One seed is one
 * mission on every tier - the seed deals the order, draws and flips the finale, lays the ground and chooses where
 * things stand, and the tier moves only how many creatures there are, in a biome and in a wave.
 *
 * <p>Streams (G0065): the strip derives from the map stream; the order, the counts, every placement pass and the
 * finale derive from {@link RngStream#MISSION}, per sub-concern and per position, so retuning one biome's block moves
 * no other biome, and hard's bigger counts move nothing put down before the creatures.
 *
 * @param tier how hard it is
 * @param seed the mission seed: the simulation's
 * @param order the biomes dealt, west to east
 * @param laid the strip they are laid in, and the finale past its last ridge
 * @param placed everything placement put down, biome by biome in the order of its passes, then the finale's set
 *     piece: its hold, turrets, mines, barrels and pickups
 * @param finale which finale, whether it is flipped, and its waves
 * @param shortfalls every pass that put down fewer than it was dealt - a generator fault, which the sweep fails
 */
public record Mission(
        TierKind tier,
        long seed,
        List<BiomeKind> order,
        Strip.Laid laid,
        List<Placed> placed,
        Finale finale,
        List<String> shortfalls) {

    private static final long ORDER = 0x2F5B_4C81_D7A3_906EL;
    private static final long PLACEMENT = 0x8D1E_63F7_0BA2_C459L;
    private static final long FINALE = 0xE7C4_1A0D_93B6_5F28L;
    private static final long WAVES = 0x4B19_F6E2_07CD_A853L;

    /** How many deals are tried before a pool that cannot deal its count is refused. */
    private static final int DEALS = 64;

    public Mission {
        order = List.copyOf(order);
        placed = List.copyOf(placed);
        shortfalls = List.copyOf(shortfalls);
    }

    /**
     * The finale a mission ends on (G0064). Its set piece is in {@code placed}, at the position after the last biome.
     *
     * @param kind which one was drawn
     * @param flipped whether its layout is mirrored north-south
     * @param waves its schedule, held back until its post is taken, each count drawn and scaled for the tier
     */
    public record Finale(FinaleKind kind, boolean flipped, List<Wave> waves) {

        public Finale {
            waves = List.copyOf(waves);
        }

        /** Its definition as it is laid: flipped, if it is. */
        public FinaleDef def(SimConfig config) {
            FinaleDef def = config.finales().of(kind);
            return flipped ? def.flipped() : def;
        }
    }

    /** The mission {@code config.seed()} names on this tier, from a config the tier has been applied to or not. */
    public static Mission build(SimConfig config, TierKind tier) {
        return build(config, tier, Rng.stream(config.seed(), RngStream.MAP));
    }

    /** Every ground the strip laid, west to east, the finale's last. */
    public List<BiomeGround> grounds(SimConfig config) {
        List<BiomeGround> grounds = new ArrayList<>();
        for (BiomeKind kind : order) {
            grounds.add(config.biomes().of(kind).ground());
        }
        grounds.add(finale.def(config).ground());
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
        Randomness drawing = mission(config).derive(FINALE);
        List<FinaleKind> kinds = List.of(FinaleKind.values());
        FinaleKind kind = kinds.get(drawing.nextInt(kinds.size()));
        boolean flipped = drawing.nextInt(2) == 1;
        FinaleDef finale = new Finale(kind, flipped, List.of()).def(config);

        List<BiomeGround> grounds = new ArrayList<>();
        for (BiomeKind dealt : order) {
            grounds.add(config.biomes().of(dealt).ground());
        }
        // The start lies inside the first biome whatever width it is dealt, against the border.
        TileRect start = config.spawn()
                .on(1 + grounds.getFirst().widthMin(), config.mission().ground().rows());
        Strip.Laid laid = Strip.lay(
                config.mission().ground(),
                grounds,
                finale.ground(),
                start,
                map,
                config.map().tileSize());

        int position = order.size();
        TileRect anchor = Strip.anchor(laid.biomes().getLast());
        TileMap tiles = laid.map();
        Hold hold = finale.hold();
        Placed.Hold point = new Placed.Hold(
                centreX(tiles, hold.post(), anchor), centreY(tiles, hold.post(), anchor), hold.watch(), position);

        Placement placement = new Placement(config, laid, point);
        Randomness placing = mission(config).derive(PLACEMENT);
        for (int p = 0; p < order.size(); p++) {
            BiomeKind dealt = order.get(p);
            BiomeDef def = config.biomes().of(dealt);
            placement.biome(
                    p,
                    dealt,
                    def,
                    config.mission().pressure(tier, p),
                    config.mission().pressures(p),
                    p == order.size() - 1,
                    placing.derive(p));
        }

        List<Placed> placed = new ArrayList<>(placement.placed());
        placed.add(point);
        for (Offset at : hold.turrets()) {
            placed.add(new Placed.Turret(centreX(tiles, at, anchor), centreY(tiles, at, anchor), position));
        }
        for (Offset at : hold.mines()) {
            placed.add(new Placed.Mine(centreX(tiles, at, anchor), centreY(tiles, at, anchor), position));
        }
        for (Offset at : hold.barrels()) {
            placed.add(new Placed.Barrel(centreX(tiles, at, anchor), centreY(tiles, at, anchor), position));
        }
        for (LairLine line : hold.pickups()) {
            if (line instanceof LairLine.Pickup pickup) {
                Offset at = pickup.at();
                placed.add(new Placed.Pickup(
                        pickup.kind(), centreX(tiles, at, anchor), centreY(tiles, at, anchor), position));
            }
        }

        // Every tier draws the same counts from the same stream and scales them, so a harder tier moves nothing else.
        Randomness counting = drawing.derive(WAVES);
        List<Wave> waves = new ArrayList<>();
        for (WaveLine line : finale.waves()) {
            waves.add(new Wave(
                    line.afterSeconds(),
                    line.strain(),
                    line.count(counting, config.mission().finale(tier)),
                    hold.mouth(line.mouth()).from(anchor)));
        }
        return new Mission(
                tier, config.seed(), order, laid, placed, new Finale(kind, flipped, waves), placement.shortfalls());
    }

    private static float centreX(TileMap map, Offset at, TileRect anchor) {
        return map.tileCentreX(at.from(anchor).col());
    }

    private static float centreY(TileMap map, Offset at, TileRect anchor) {
        return map.tileCentreY(at.from(anchor).row());
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
