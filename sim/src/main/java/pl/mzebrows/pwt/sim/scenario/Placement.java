package pl.mzebrows.pwt.sim.scenario;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import pl.mzebrows.pwt.map.House;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.Strip;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.BiomeDef;
import pl.mzebrows.pwt.sim.Furniture;
import pl.mzebrows.pwt.sim.MissionConfig;
import pl.mzebrows.pwt.sim.PlacementRules;
import pl.mzebrows.pwt.sim.Roster;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Stock;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * Puts down what each biome of a mission counts, by rules and never by places (G0068): ordered {@link Scatter}
 * passes, the most constrained first - points, what a house holds, nests, packs, crates by points, turrets by points,
 * medkits and food,
 * barrels, boulders, trees, rabbits. Each pass keeps off what went down before it by its radii, crow-flies, and off every
 * tile the strip kept: the start, aprons, doorsteps, and every house's inside but for its stock. The last biome keeps
 * everything hostile outside the finale's watch (G0064), so holding its post never waits on a hole or a sleeper behind
 * the ridge.
 *
 * <p>Counts are drawn first, each from a stream of its own, and never rescaled by placement. A biome whose passes
 * cannot put down all they were dealt is placed again from a fresh stream, as a deal that reaches a dead end is dealt
 * again - its counts kept - and the attempt taken is the first that fits every tier, so one seed stands its points in
 * the same places on every tier. A biome no attempt fits puts down what fits and says so in
 * {@link #shortfalls}: a generator fault the sweep fails, never a quieter biome.
 *
 * <p>Allocates freely: a world is built once.
 */
final class Placement {

    /** How many times a biome is placed before what would not fit is reported. */
    private static final int ATTEMPTS = 32;

    private static final long PASSES = 0x3A67_D21C_95E0_4B8FL;
    private static final long ROSTER = 0x8BB1_CC42_46DE_2400L;
    private static final long FURNITURE = 0x665B_CE74_4BDC_4164L;
    private static final long POINTS = 0x1848_F9D0_278C_EAFAL;
    private static final long STOCK = 0x859B_0F5E_8DAB_B577L;
    private static final long NESTS = 0x176C_645E_059D_330AL;
    private static final long PACKS = 0xB77F_62E4_1C9B_4737L;
    private static final long CRATES = 0x892B_360E_4985_3C6BL;
    private static final long PICKUPS = 0xBCB8_8634_48D1_4DE2L;
    private static final long BARRELS = 0x712F_4803_E234_2E39L;
    private static final long BOULDERS = 0x69CB_E66C_A40B_DAC8L;
    private static final long RABBITS = 0xB4CB_70B3_9630_78A8L;
    private static final long TREES = 0x2F1D_8A6B_C3E5_9071L;
    private static final long TREES_BY_NEST = 0x5C83_E1A7_0B9F_42D6L;
    private static final long TRUNKS = 0xD06A_4B2E_97C1_F358L;
    private static final long TURRETS = 0x4E2B_97D3_1A6C_F085L;
    private static final long STONES = 0x93A7_5C1E_D24B_806FL;
    private static final long STONE_SIZES = 0x2B6E_F193_7A0D_C54BL;

    private final SimConfig config;
    private final PlacementRules rules;
    private final Strip.Laid laid;
    private final TileMap map;

    /** The finale's point, whose watch the last biome keeps everything hostile out of. */
    private final Placed.Hold hold;

    private final List<Placed> placed = new ArrayList<>();
    private final List<String> shortfalls = new ArrayList<>();

    Placement(SimConfig config, Strip.Laid laid, Placed.Hold hold) {
        this.config = config;
        this.rules = config.mission().placement();
        this.laid = laid;
        this.map = laid.map();
        this.hold = hold;
    }

    /** Everything put down so far, biome by biome in the order of its passes. */
    List<Placed> placed() {
        return placed;
    }

    /** Every pass that put down fewer than it was dealt. */
    List<String> shortfalls() {
        return shortfalls;
    }

    /**
     * Puts down one biome's roster and furniture: placed again until it fits every tier's pressure, then placed at
     * {@code pressure} from the attempt that did.
     *
     * @param position where it stands in the march, the first zero
     * @param pressure this tier's, which the counts are scaled by
     * @param every every tier's, which the attempt is chosen by
     * @param last whether it is the last before the finale: its last point stands in its last {@code approachColumns},
     *     and nothing hostile in it stands within the finale's watch
     * @param stream its own stream; only derived from
     */
    void biome(
            int position,
            BiomeKind kind,
            BiomeDef def,
            MissionConfig.Pressure pressure,
            List<MissionConfig.Pressure> every,
            boolean last,
            Randomness stream) {
        Randomness passes = stream.derive(PASSES);
        int attempt = 0;
        while (attempt < ATTEMPTS - 1 && !fits(position, kind, def, every, last, stream, passes.derive(attempt))) {
            attempt++;
        }
        Biome placedBiome = new Biome(position, kind, def, last, stream, passes.derive(attempt)).place(pressure);
        placed.addAll(placedBiome.here);
        shortfalls.addAll(placedBiome.shortfalls);
    }

    /** Whether one attempt puts down everything a biome is dealt at every one of these pressures. */
    private boolean fits(
            int position,
            BiomeKind kind,
            BiomeDef def,
            List<MissionConfig.Pressure> every,
            boolean last,
            Randomness stream,
            Randomness attempt) {
        for (MissionConfig.Pressure pressure : every) {
            if (!new Biome(position, kind, def, last, stream, attempt)
                    .place(pressure)
                    .shortfalls
                    .isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** One biome's passes, and what they have put down so far. */
    private final class Biome {

        private final int position;
        private final BiomeKind kind;
        private final BiomeDef def;
        private final boolean last;

        /** What the counts are drawn from: the same on every attempt. */
        private final Randomness stream;

        /** What this attempt's passes draw from. */
        private final Randomness attempt;

        private final TileRect rect;

        /** Where it is entered from the west: the start, for the first; the passes' mouths for the rest. */
        private final List<TileRect> entries;

        private final List<Placed> here = new ArrayList<>();
        private final List<Placed> points = new ArrayList<>();
        private final List<Placed> nests = new ArrayList<>();
        private final List<float[]> anchors = new ArrayList<>();
        private final List<String> shortfalls = new ArrayList<>();

        Biome(int position, BiomeKind kind, BiomeDef def, boolean last, Randomness stream, Randomness attempt) {
            this.position = position;
            this.kind = kind;
            this.def = def;
            this.last = last;
            this.stream = stream;
            this.attempt = attempt;
            this.rect = laid.biomes().get(position);
            this.entries = laid.entries().get(position);
        }

        Biome place(MissionConfig.Pressure pressure) {
            Randomness drawn = stream.derive(ROSTER);
            int[] creatures = new int[def.roster().size()];
            for (int i = 0; i < creatures.length; i++) {
                creatures[i] = def.roster().get(i).count(drawn, pressure);
            }
            Randomness counted = stream.derive(FURNITURE);
            Furniture furniture = def.furniture();
            int pointCount = furniture.points(counted);
            int barrels = furniture.barrels(counted);
            int rabbits = furniture.rabbits(counted);
            int boulders = furniture.boulders(counted);
            int[] stock = new int[furniture.pickups().size()];
            for (int i = 0; i < stock.length; i++) {
                stock[i] = furniture.pickups().get(i).count(counted);
            }
            int trees = furniture.trees(counted);
            // Drawn last, so a biome with none draws every other count as it did before turrets were a line (G0082).
            int turrets = furniture.turrets(counted);
            // Drawn after that, for the same reason: a biome with no stones draws everything as it did before them.
            int stones = furniture.stones(counted);

            points(pointCount, attempt.derive(POINTS));
            houseStock(stock, attempt.derive(STOCK));
            nests(creatures, attempt.derive(NESTS));
            packs(creatures, attempt.derive(PACKS));
            crates(stock, attempt.derive(CRATES));
            turrets(turrets, attempt.derive(TURRETS));
            pickups(stock, attempt.derive(PICKUPS));
            barrels(barrels, attempt.derive(BARRELS));
            boulders(boulders, attempt.derive(BOULDERS));
            trees(trees, stream.derive(TREES_BY_NEST), attempt.derive(TREES));
            stones(stones, attempt.derive(STONES), attempt.derive(STONE_SIZES));
            rabbits(rabbits, attempt.derive(RABBITS));
            return this;
        }

        // ---- the passes, in order ----

        /**
         * One point to each equal band of columns, so the goal line leads east; the last biome's last band is its
         * {@code approachColumns}.
         */
        private void points(int count, Randomness rng) {
            Scatter scatter = scatter(rng, 0f);
            float radius = config.controlPoint().radius();
            List<TileRect> bands = bands(count);
            for (int b = 0; b < bands.size(); b++) {
                int got = scatter.scatter(bands.get(b), 1, radius, (x, y) -> {
                    put(new Placed.Point(x, y, position));
                    scatter.reserve(x, y, rules.pointApart());
                });
                shortOf("point in band " + b, 1, got);
            }
        }

        /** What a house holds: crates first, then whatever else is not food, up to {@code houseStock} a house. */
        private void houseStock(int[] stock, Randomness rng) {
            Scatter scatter = new Scatter(map, rng, 0f, 0f);
            for (House house : laid.houses()) {
                TileRect walls = house.walls();
                if (!rect.contains(walls.col(), walls.row())
                        || !rect.contains(walls.endCol() - 1, walls.endRow() - 1)) {
                    continue;
                }
                for (int room = rules.houseStock(); room > 0; room--) {
                    int line = nextForAHouse(stock);
                    if (line < 0) {
                        return;
                    }
                    PickupKind pickup = kindOf(line);
                    int got = scatter.scatter(
                            house.inside(),
                            1,
                            config.pickup(pickup).radius(),
                            (x, y) -> put(new Placed.Pickup(pickup, x, y, position)));
                    shortOf(pickup + " in a house", 1, got);
                    stock[line]--;
                }
            }
        }

        /** Holes: off the way in, off every point and off each other; never in a house, nor in the finale's watch. */
        private void nests(int[] creatures, Randomness rng) {
            Scatter scatter = scatter(rng, 0f).keepingOffRock(rules.rockMargin());
            keepOffEntries(scatter, entryKeepOff(rules.nestKeepOff()));
            keepOffTheWatch(scatter, 0f);
            for (Placed point : points) {
                scatter.reserve(point.x(), point.y(), rules.pointKeepOff());
            }
            for (Placed stocked : here) {
                if (stocked instanceof Placed.Pickup) {
                    scatter.reserve(stocked.x(), stocked.y(), rules.pickupKeepOff());
                }
            }
            for (int i = 0; i < creatures.length; i++) {
                if (def.roster().get(i) instanceof Roster.Nest nest) {
                    int got = scatter.scatter(rect, creatures[i], config.nest().radius(), (x, y) -> {
                        Placed hole = new Placed.Nest(nest.strain(), x, y, position);
                        put(hole);
                        nests.add(hole);
                        scatter.reserve(x, y, rules.nestApart());
                    });
                    shortOf("nests of " + nest.strain(), creatures[i], got);
                }
            }
        }

        /**
         * Each loose line in packs round anchors kept off the passes in, the start and each other: encounters, not a
         * carpet.
         */
        private void packs(int[] creatures, Randomness rng) {
            float spread = rules.packApart() / 2f;
            Scatter anchoring = scatter(rng, 0f);
            keepOffEntries(anchoring, position == 0 ? rules.startKeepOff() : rules.nestKeepOff());
            keepOffTheWatch(anchoring, spread);
            Scatter members = scatter(rng, 0f);
            if (position == 0) {
                keepOffEntries(members, rules.startKeepOff());
            }
            keepOffTheWatch(members, 0f);
            for (int i = 0; i < creatures.length; i++) {
                if (!(def.roster().get(i) instanceof Roster.Loose loose)) {
                    continue;
                }
                float radius = config.unit(loose.strain()).radius();
                for (int left = creatures[i]; left > 0; ) {
                    int size = Math.min(left, rules.packMin() + rng.nextInt(rules.packMax() - rules.packMin() + 1));
                    left -= size;
                    float[] anchor = new float[2];
                    int found = anchoring.scatter(rect, 1, radius, (x, y) -> {
                        anchor[0] = x;
                        anchor[1] = y;
                        anchoring.reserve(x, y, rules.packApart());
                    });
                    if (found == 0) {
                        shortOf("pack of " + loose.strain(), size, 0);
                        continue;
                    }
                    anchors.add(anchor);
                    int got = members.scatterNearest(
                            TileRect.around(map, anchor[0], anchor[1], spread),
                            anchor[0],
                            anchor[1],
                            size,
                            radius,
                            (x, y) -> put(new Placed.Loose(loose.strain(), x, y, position)));
                    shortOf("pack of " + loose.strain(), size, got);
                }
            }
        }

        /** Crates the houses did not take, each within {@code pointReach} of a point: the reward where the fight is. */
        private void crates(int[] stock, Randomness rng) {
            Scatter scatter = scatter(rng, 0f);
            keepOffNests(scatter);
            for (int line = 0; line < stock.length; line++) {
                PickupKind pickup = kindOf(line);
                if (!crate(pickup)) {
                    continue;
                }
                float radius = config.pickup(pickup).radius();
                Scatter.Place place = (x, y) -> put(new Placed.Pickup(pickup, x, y, position));
                for (int k = 0; k < stock[line]; k++) {
                    shortOf(pickup + " by a point", 1, byAPoint(scatter, k, radius, place));
                }
                stock[line] = 0;
            }
        }

        /**
         * Turrets, each within {@code pointReach} of a point, a point at a time from a drawn one, off every nest: a gun
         * where the fight is, somewhere the level tells you to stand (G0082).
         */
        private void turrets(int count, Randomness rng) {
            if (count == 0) {
                return;
            }
            Scatter scatter = scatter(rng, 0f);
            keepOffNests(scatter);
            float radius = config.turret().radius();
            Scatter.Place place = (x, y) -> put(new Placed.Turret(x, y, position));
            int first = rng.nextInt(points.size());
            for (int k = 0; k < count; k++) {
                shortOf("turret by a point", 1, byAPoint(scatter, first + k, radius, place));
            }
        }

        /**
         * One thing within {@code pointReach} of a point, trying the points in turn from the {@code from}-th: how many
         * went down, one or none.
         */
        private int byAPoint(Scatter scatter, int from, float radius, Scatter.Place place) {
            int got = 0;
            for (int j = 0; j < points.size() && got == 0; j++) {
                Placed point = points.get((from + j) % points.size());
                scatter.excluding(within(point.x(), point.y(), rules.pointReach()));
                got = scatter.scatter(TileRect.around(map, point.x(), point.y(), rules.pointReach()), 1, radius, place);
            }
            return got;
        }

        /** Everything else lying about, off every nest; food on the biome's organic ground while there is room. */
        private void pickups(int[] stock, Randomness rng) {
            Scatter scatter = scatter(rng, 0f);
            keepOffNests(scatter);
            boolean organic = organic();
            for (int line = 0; line < stock.length; line++) {
                if (stock[line] == 0) {
                    continue;
                }
                PickupKind pickup = kindOf(line);
                float radius = config.pickup(pickup).radius();
                Scatter.Place place = (x, y) -> put(new Placed.Pickup(pickup, x, y, position));
                int got = food(pickup) && organic
                        ? scatter.scatter(rect, stock[line], radius, EnumSet.of(Terrain.ORGANIC), place)
                        : 0;
                got += scatter.scatter(rect, stock[line] - got, radius, place);
                shortOf(pickup.toString(), stock[line], got);
            }
        }

        /** Barrels by the holes and the packs, so a shot sets one off among them. */
        private void barrels(int count, Randomness rng) {
            Scatter scatter = scatter(rng, 0f);
            float radius = config.pickup(PickupKind.BARREL).radius();
            Scatter.Place place = (x, y) -> put(new Placed.Barrel(x, y, position));
            List<float[]> by = new ArrayList<>();
            for (Placed nest : nests) {
                by.add(new float[] {nest.x(), nest.y()});
            }
            by.addAll(anchors);
            for (int k = 0; k < count; k++) {
                int got = 0;
                if (by.isEmpty()) {
                    got = scatter.scatter(rect, 1, radius, place);
                }
                int first = by.isEmpty() ? 0 : rng.nextInt(by.size());
                for (int j = 0; j < by.size() && got == 0; j++) {
                    float[] at = by.get((first + j) % by.size());
                    scatter.excluding(within(at[0], at[1], rules.barrelReach()));
                    got = scatter.scatter(TileRect.around(map, at[0], at[1], rules.barrelReach()), 1, radius, place);
                }
                shortOf("barrel", 1, got);
            }
        }

        /** Boulders anywhere, {@code rockMargin} off rock and off everything else (G0042). */
        private void boulders(int count, Randomness rng) {
            Scatter scatter = scatter(rng, rules.rockMargin()).keepingOffRock(rules.rockMargin());
            int got = scatter.scatter(
                    rect, count, config.obstacle().radius(), (x, y) -> put(new Placed.Boulder(x, y, position)));
            shortOf("boulders", count, got);
        }

        /**
         * Trees, each as a boulder stands (G0042): {@code treesByNest} within {@code treeReach} of each nest first -
         * foliage to come out of - then the rest anywhere (G0072). How many by each nest is drawn from {@code byNest},
         * nest by nest, so an attempt placed again keeps its counts (G0070); each trunk from a stream of its own.
         */
        private void trees(int count, Randomness byNest, Randomness rng) {
            Scatter scatter = scatter(rng, rules.rockMargin()).keepingOffRock(rules.rockMargin());
            Randomness trunks = rng.derive(TRUNKS);
            int left = count;
            for (Placed nest : nests) {
                int by = Math.min(left, def.furniture().treesByNest(byNest));
                left -= by;
                scatter.excluding(within(nest.x(), nest.y(), rules.treeReach()));
                TileRect around = TileRect.around(map, nest.x(), nest.y(), rules.treeReach());
                int got = 0;
                for (int k = 0; k < by; k++) {
                    got += tree(scatter, around, config.obstacle().trunk(trunks));
                }
                shortOf("trees by " + nest, by, got);
            }
            scatter.excluding(laid::kept);
            int got = 0;
            for (int k = 0; k < left; k++) {
                got += tree(scatter, rect, config.obstacle().trunk(trunks));
            }
            shortOf("trees", left, got);
        }

        private int tree(Scatter scatter, TileRect area, float trunk) {
            return scatter.scatter(area, 1, trunk, (x, y) -> put(new Placed.Tree(x, y, trunk, position)));
        }

        /**
         * Stones anywhere, as boulders stand: {@code rockMargin} off rock and off everything else (G0042). Each one's
         * size from a stream of its own, so the count and the sizes are drawn apart.
         */
        private void stones(int count, Randomness rng, Randomness sizes) {
            Scatter scatter = scatter(rng, rules.rockMargin()).keepingOffRock(rules.rockMargin());
            int got = 0;
            for (int k = 0; k < count; k++) {
                float radius = config.obstacle().stone(sizes);
                got += scatter.scatter(rect, 1, radius, (x, y) -> put(new Placed.Stone(x, y, radius, position)));
            }
            shortOf("stones", count, got);
        }

        /** Rabbits anywhere off the start. */
        private void rabbits(int count, Randomness rng) {
            int got = scatter(rng, 0f)
                    .scatter(
                            rect,
                            count,
                            config.pickup(PickupKind.RABBIT).radius(),
                            (x, y) -> put(new Placed.Rabbit(x, y, position)));
            shortOf("rabbits", count, got);
        }

        // ---- helpers ----

        /** A scatter over this biome, off every kept tile and every thing already standing in it. */
        private Scatter scatter(Randomness rng, float clearance) {
            Scatter scatter = new Scatter(map, rng, clearance, 0f).excluding(laid::kept);
            for (Placed standing : here) {
                scatter.reserve(standing.x(), standing.y(), radius(standing));
            }
            return scatter;
        }

        private void put(Placed thing) {
            here.add(thing);
            if (thing instanceof Placed.Point) {
                points.add(thing);
            }
        }

        /** The biome's columns in {@code count} equal bands; for the last biome, the last is its last {@code approachColumns}. */
        private List<TileRect> bands(int count) {
            int east = last ? Math.min(config.mission().approachColumns(), rect.width()) : 0;
            int even = last ? count - 1 : count;
            int span = rect.width() - east;
            List<TileRect> bands = new ArrayList<>();
            for (int b = 0; b < even; b++) {
                int from = rect.col() + span * b / even;
                int to = rect.col() + span * (b + 1) / even;
                bands.add(new TileRect(from, rect.row(), to - from, rect.height()));
            }
            if (last) {
                bands.add(new TileRect(rect.endCol() - east, rect.row(), east, rect.height()));
            }
            return bands;
        }

        /** The first biome's way in is the start, which keeps everything hostile further off than a pass does. */
        private float entryKeepOff(float fromAPass) {
            return position == 0 ? Math.max(rules.startKeepOff(), fromAPass) : fromAPass;
        }

        /**
         * Keeps a scatter {@code distance} off every tile of every way in, crow-flies: a circle on each tile, reaching
         * past its corners.
         */
        private void keepOffEntries(Scatter scatter, float distance) {
            float tile = map.tileSize();
            float corner = tile * (float) Math.sqrt(2.0) / 2f;
            for (TileRect entry : entries) {
                for (int row = entry.row(); row < entry.endRow(); row++) {
                    for (int col = entry.col(); col < entry.endCol(); col++) {
                        scatter.reserve(map.tileCentreX(col), map.tileCentreY(row), distance + corner);
                    }
                }
            }
        }

        /** The last biome's: keeps a scatter out of the finale's watch, and {@code more} past it. */
        private void keepOffTheWatch(Scatter scatter, float more) {
            if (last) {
                scatter.reserve(hold.x(), hold.y(), hold.watch() + more);
            }
        }

        private void keepOffNests(Scatter scatter) {
            for (Placed nest : nests) {
                scatter.reserve(nest.x(), nest.y(), rules.pickupKeepOff());
            }
        }

        /** The tiles wholly within {@code reach} of a place, and not kept. */
        private Scatter.Exclusion within(float x, float y, float reach) {
            float tile = map.tileSize();
            return (col, row) -> {
                float dx = Math.max(Math.abs(col * tile - x), Math.abs((col + 1) * tile - x));
                float dy = Math.max(Math.abs(row * tile - y), Math.abs((row + 1) * tile - y));
                return laid.kept(col, row) || dx * dx + dy * dy > reach * reach;
            };
        }

        /** The next stock line a house takes: a crate, then anything that is not food. */
        private int nextForAHouse(int[] stock) {
            int other = -1;
            for (int line = 0; line < stock.length; line++) {
                PickupKind pickup = kindOf(line);
                if (stock[line] > 0 && crate(pickup)) {
                    return line;
                }
                if (stock[line] > 0 && other < 0 && !food(pickup)) {
                    other = line;
                }
            }
            return other;
        }

        private PickupKind kindOf(int line) {
            return switch (def.furniture().pickups().get(line)) {
                case Stock.Pickup pickup -> pickup.kind();
            };
        }

        /** Whether the biome shows organic ground anywhere it was not kept. */
        private boolean organic() {
            for (int row = rect.row(); row < rect.endRow(); row++) {
                for (int col = rect.col(); col < rect.endCol(); col++) {
                    if (map.terrainAtTile(col, row) == Terrain.ORGANIC && !laid.kept(col, row)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void shortOf(String what, int wanted, int got) {
            if (got < wanted) {
                shortfalls.add("biome " + position + " (" + kind + "): " + got + " of " + wanted + " " + what);
            }
        }
    }

    /** What a thing standing takes up, as its body does once spawned. */
    private float radius(Placed thing) {
        return switch (thing) {
            case Placed.Point _ -> config.controlPoint().radius();
            case Placed.Nest _ -> config.nest().radius();
            case Placed.Loose loose -> config.unit(loose.strain()).radius();
            case Placed.Pickup pickup -> config.pickup(pickup.kind()).radius();
            case Placed.Barrel _ -> config.pickup(PickupKind.BARREL).radius();
            case Placed.Boulder _ -> config.obstacle().radius();
            case Placed.Tree tree -> tree.trunk();
            case Placed.Stone stone -> stone.radius();
            case Placed.Rabbit _ -> config.pickup(PickupKind.RABBIT).radius();
            case Placed.Hold _ -> config.controlPoint().radius();
            case Placed.Turret _ -> config.turret().radius();
            case Placed.Mine _ -> config.mine().radius();
        };
    }

    /** A crate: opened by hand and dealt from the armoury deck (G0056). */
    private boolean crate(PickupKind pickup) {
        return config.pickup(pickup).armoury().crate();
    }

    /** Food: something a creature gains from eating it, so it lies where creatures graze. */
    private boolean food(PickupKind pickup) {
        return config.pickup(pickup).offersSomethingTo(Side.ALIEN);
    }
}
