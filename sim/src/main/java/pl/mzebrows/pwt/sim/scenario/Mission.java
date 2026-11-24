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
import pl.mzebrows.pwt.sim.def.Arrival;
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
 * @param finale which finale, whether it is flipped, the cover its rules dealt, and its waves
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
    private static final long RULES = 0x7C2D_E580_1F9B_46A3L;

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
     * @param flipped whether its skeleton is mirrored north-south
     * @param cover the rock its rules dealt round the skeleton (G0120), tile offsets from its anchor - in mirrored
     *     north-south pairs, each piece's mirror after it
     * @param waves its schedule, held back until its post is taken, each count drawn and scaled for the tier
     * @param lair what rises once every commander stands in the finale - its holes, then its gun caches, then its boss;
     *     empty but for a keep; its ground is the finale's whole rect (G0048)
     */
    public record Finale(FinaleKind kind, boolean flipped, List<TileRect> cover, List<Wave> waves, List<Arrival> lair) {

        public Finale {
            cover = List.copyOf(cover);
            waves = List.copyOf(waves);
            lair = List.copyOf(lair);
        }

        /** Its definition as it is laid: flipped, if it is, with its cover. */
        public FinaleDef def(SimConfig config) {
            FinaleDef def = config.finales().of(kind);
            return (flipped ? def.flipped() : def).covered(cover);
        }
    }

    /**
     * Which finale {@code config.seed()} ends on - its kind, then its flip, from the finale's stream - with its cover,
     * waves and lair not yet resolved. What a sweep picks seeds by, so it reaches every finale both ways by name rather
     * than by luck. What its rules place is drawn after, from a stream of their own (G0120).
     */
    public static Finale drawn(SimConfig config) {
        Randomness drawing = mission(config).derive(FINALE);
        List<FinaleKind> kinds = List.of(FinaleKind.values());
        FinaleKind kind = kinds.get(drawing.nextInt(kinds.size()));
        boolean flipped = drawing.nextInt(2) == 1;
        return new Finale(kind, flipped, List.of(), List.of(), List.of());
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
     * The biomes {@code config.seed()} deals, west to east, from a bag: none comes a second time before every biome of
     * the pool has come once, and never two of one base side by side. Up to the pool's size that is none twice. A
     * dead end deals again from where the stream has got to (G0102).
     */
    public static List<BiomeKind> deal(SimConfig config) {
        return deal(config.biomes(), config.mission().biomes(), mission(config).derive(ORDER));
    }

    /** {@link #build(SimConfig, TierKind)} on a map stream already derived, as {@code Scenarios.world} hands one. */
    static Mission build(SimConfig config, TierKind tier, Randomness map) {
        List<BiomeKind> order = deal(config);
        Finale drawn = drawn(config);
        // The skeleton as it is laid, its cover dealt by its rules on its bare ground before the strip is (G0120).
        FinalePlacement rules = new FinalePlacement(
                config, drawn.def(config), mission(config).derive(FINALE).derive(RULES));
        List<TileRect> cover = rules.cover();
        FinaleDef finale = drawn.def(config).covered(cover);

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
                    config.mission().pressure(tier, p).times(config.swarm().strengthCountPercent()),
                    config.mission().pressures(p),
                    p == order.size() - 1,
                    placing.derive(p));
        }

        // The lair's fixed spots first, so what the rules put down round the post keeps off them.
        List<Arrival> spots = new ArrayList<>();
        for (LairLine line : finale.lair()) {
            float x = centreX(tiles, line.at(), anchor);
            float y = centreY(tiles, line.at(), anchor);
            spots.add(
                    switch (line) {
                        case LairLine.Unit unit -> Arrival.unit(unit.strain(), x, y);
                        case LairLine.Nest nest -> Arrival.nest(nest.strain(), x, y);
                        case LairLine.Pickup pickup -> Arrival.pickup(pickup.kind(), x, y);
                    });
        }
        FinalePlacement.Items items = rules.items(laid, laid.biomes().getLast(), position, spots);

        List<Placed> placed = new ArrayList<>(placement.placed());
        placed.add(point);
        placed.addAll(items.placed());

        // Every tier draws the same counts from the same stream and scales them, so a harder tier moves nothing else.
        // A wave from several mouths draws a count for each, in the order written (G0087).
        Randomness counting = mission(config).derive(FINALE).derive(WAVES);
        List<Wave> waves = new ArrayList<>();
        for (WaveLine line : finale.waves()) {
            List<Wave.Lot> lots = new ArrayList<>();
            for (String mouth : line.mouthNames()) {
                lots.add(new Wave.Lot(
                        line.count(
                                counting,
                                config.mission()
                                        .finale(tier)
                                        .times(config.swarm().strengthCountPercent())),
                        hold.mouth(mouth).from(anchor)));
            }
            waves.add(new Wave(line.afterSeconds(), line.strain(), lots));
        }
        // Holes first, then the caches, then whoever rises among them, as Adventure 2 placed them (G0048).
        List<Arrival> lair = new ArrayList<>();
        for (Arrival spot : spots) {
            if (spot.form() != Arrival.Form.UNIT) {
                lair.add(spot);
            }
        }
        lair.addAll(items.caches());
        for (Arrival spot : spots) {
            if (spot.form() == Arrival.Form.UNIT) {
                lair.add(spot);
            }
        }
        List<String> shortfalls = new ArrayList<>(placement.shortfalls());
        shortfalls.addAll(rules.shortfalls());
        return new Mission(
                tier,
                config.seed(),
                order,
                laid,
                placed,
                new Finale(drawn.kind(), drawn.flipped(), cover, waves, lair),
                shortfalls);
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
                if (left.isEmpty()) {
                    // The whole pool has come once: a fresh bag, so a biome comes again only now (G0102).
                    left.addAll(List.of(BiomeKind.values()));
                }
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
