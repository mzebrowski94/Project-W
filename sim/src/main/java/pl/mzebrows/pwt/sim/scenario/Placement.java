package pl.mzebrows.pwt.sim.scenario;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import pl.mzebrows.pwt.map.Clearing;
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
import pl.mzebrows.pwt.sim.def.EmplacementDef;
import pl.mzebrows.pwt.sim.def.EmplacementKind;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WorkedSiteDef;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;

/**
 * Puts down what each biome of a mission counts, by rules and never by places (G0068): ordered {@link Scatter}
 * passes, the most constrained first - sites (a worked site in its house first, G0111), what a house holds, its house
 * packs (G0112), nests, packs, turrets by points, geysers, emplacements (G0113), medkits and
 * food,
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

    /** Which of a biome's site bands another kind of site takes, a bit a band: none, until one does (G0110). */
    private static final long NO_OTHER_SITES = 0L;

    /** Half a tile's diagonal, as a fraction of its side: a circle on a tile's centre this wide covers the tile. */
    private static final float HALF_DIAGONAL = 0.7072f;

    /** A chance is dealt as a whole per cent. */
    private static final int PERCENT = 100;

    /** How many times a biome is placed before what would not fit is reported. */
    private static final int ATTEMPTS = 32;

    private static final long PASSES = 0x3A67_D21C_95E0_4B8FL;
    private static final long ROSTER = 0x8BB1_CC42_46DE_2400L;
    private static final long FURNITURE = 0x665B_CE74_4BDC_4164L;
    private static final long POINTS = 0x1848_F9D0_278C_EAFAL;
    private static final long STOCK = 0x859B_0F5E_8DAB_B577L;
    private static final long NESTS = 0x176C_645E_059D_330AL;
    private static final long PACKS = 0xB77F_62E4_1C9B_4737L;
    private static final long PICKUPS = 0xBCB8_8634_48D1_4DE2L;
    private static final long BARRELS = 0x712F_4803_E234_2E39L;
    private static final long BOULDERS = 0x69CB_E66C_A40B_DAC8L;
    private static final long RABBITS = 0xB4CB_70B3_9630_78A8L;
    private static final long TREES = 0x2F1D_8A6B_C3E5_9071L;
    private static final long TREES_BY_NEST = 0x5C83_E1A7_0B9F_42D6L;
    private static final long TRUNKS = 0xD06A_4B2E_97C1_F358L;
    private static final long TURRETS = 0x4E2B_97D3_1A6C_F085L;
    private static final long GEYSERS = 0xA71C_3E95_D28B_640FL;
    private static final long GEYSER_SIZES = 0x3F6B_C150_8E2D_97A4L;
    private static final long STONES = 0x93A7_5C1E_D24B_806FL;
    private static final long STONE_SIZES = 0x2B6E_F193_7A0D_C54BL;
    private static final long WORKED = 0xC4E1_3B87_5F20_9AD6L;
    private static final long HOUSE_PACKS = 0x7D3A_E5C1_29B8_604FL;
    private static final long HOUSED = 0xE19C_4F72_B05D_3A86L;
    private static final long EMPLACEMENTS = 0x5B2D_8E71_C93A_04F6L;
    private static final long CYCLES = 0x96E4_17C3_5AD0_B82FL;
    private static final long OPEN_BANDS = 0x3C71_A9E2_58D4_0B6FL;
    private static final long OPEN_SITES = 0xD28F_6B13_E7A0_4C95L;
    private static final long CLEARING_SITES = 0x4A9E_2C71_B3F5_08D6L;

    /** Where an emplacement may stand: any ground but a thicket's, so never in a thicket blob's middle (G0113). */
    private static final EnumSet<Terrain> OFF_THICKET = offThicket();

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
            Biome tried = new Biome(position, kind, def, last, stream, attempt).place(pressure);
            if (!tried.shortfalls.isEmpty() || tried.crowded) {
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

        /** The worked sites, which a nest and a geyser keep off as they keep off a point (G0111); no gun stands by one. */
        private final List<Placed> worked = new ArrayList<>();

        private final List<Placed> nests = new ArrayList<>();
        private final List<float[]> anchors = new ArrayList<>();
        private final List<String> shortfalls = new ArrayList<>();

        /** A pack found no room of its own and joined others: what a host's count may do, never a tier's (G0106). */
        private boolean crowded;

        /** Its ground's clearings (G0115), west to east: none in every biome but the rift. */
        private final List<Clearing> clearings = new ArrayList<>();

        /** Its ground's nooks (G0115, amended 2026-11-24), west to east: room for a nest in the rift's maze. */
        private final List<Clearing> nooks = new ArrayList<>();

        Biome(int position, BiomeKind kind, BiomeDef def, boolean last, Randomness stream, Randomness attempt) {
            this.position = position;
            this.kind = kind;
            this.def = def;
            this.last = last;
            this.stream = stream;
            this.attempt = attempt;
            this.rect = laid.biomes().get(position);
            this.entries = laid.entries().get(position);
            for (Clearing clearing : laid.clearings()) {
                if (rect.contains(clearing.col(), clearing.row())) {
                    clearings.add(clearing);
                }
            }
            for (Clearing nook : laid.nooks()) {
                if (rect.contains(nook.col(), nook.row())) {
                    nooks.add(nook);
                }
            }
        }

        Biome place(MissionConfig.Pressure pressure) {
            Randomness drawn = stream.derive(ROSTER);
            int[] creatures = new int[def.roster().size()];
            for (int i = 0; i < creatures.length; i++) {
                // A house line is counted house by house where its packs are laid (G0112), never here: drawing
                // nothing for it keeps every other line's count as it was.
                Roster line = def.roster().get(i);
                creatures[i] = line instanceof Roster.House ? 0 : line.count(drawn, pressure);
            }
            Randomness counted = stream.derive(FURNITURE);
            Furniture furniture = def.furniture();
            int drawnSites = furniture.sites(counted);
            // A biome whose sites are in its clearings has one in each, and when last one more in its approach columns
            // (G0115); counted here, after the draw, so every other count is drawn as it was.
            int sites =
                    furniture.sitesInClearings() ? Math.max(drawnSites, clearings.size() + (last ? 1 : 0)) : drawnSites;
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
            // After turrets, for their reason: a biome with none draws every other count as it did before (G0089).
            int geysers = furniture.geysers(counted);
            // Drawn after that, for the same reason: a biome with no stones draws everything as it did before them.
            int stones = furniture.stones(counted);
            // Last of all, for the same reason: a biome with no spires draws everything as it did before them (G0113).
            int spires = furniture.spires().draw(counted);
            // After them, for the same reason: a biome with no relics draws everything as it did before them (G0114).
            int relics = furniture.relics().draw(counted);

            // Drawn from the biome's own stream, never the attempt's: whether it has a mainframe, in which house and
            // against
            // which wall is the same on every attempt and every tier (G0111).
            long housed = housedSites(sites, stream.derive(WORKED), attempt.derive(WORKED));
            // Which bands a relic takes is the biome's own stream's too; where in its band, the attempt's (G0114).
            long taken = openSites(
                    WorkedSiteKind.RELIC, relics, sites, housed, stream.derive(OPEN_BANDS), attempt.derive(OPEN_SITES));
            if (furniture.sitesInClearings()) {
                taken = clearingSites(sites, taken, attempt.derive(CLEARING_SITES));
            }
            sites(sites, taken, attempt.derive(POINTS));
            houseStock(stock, attempt.derive(STOCK));
            housePacks(pressure, stream.derive(HOUSE_PACKS), attempt.derive(HOUSED));
            unhoused(stock);
            nests(creatures, attempt.derive(NESTS));
            packs(creatures, attempt.derive(PACKS));
            turrets(turrets, attempt.derive(TURRETS));
            geysers(geysers, attempt.derive(GEYSERS));
            emplacements(EmplacementKind.SPIRE, spires, attempt.derive(EMPLACEMENTS));
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
         * The biome's sites (G0110): {@code count} equal bands of columns, so the goal line leads east - the last
         * biome's last band its {@code approachColumns} - and a control point in each band but those {@code others}
         * marks, one bit a band, which another kind of site takes. Today no kind does.
         */
        private void sites(int count, long others, Randomness rng) {
            Scatter scatter = scatter(rng, 0f);
            for (Placed site : worked) {
                // A worked site already standing in its band keeps the points of the others as far off as they keep
                // each other (G0111).
                scatter.reserve(site.x(), site.y(), rules.pointApart());
            }
            for (Placed point : points) {
                // So does a point already standing in a clearing (G0115).
                scatter.reserve(point.x(), point.y(), rules.pointApart());
            }
            float radius = config.controlPoint().radius();
            List<TileRect> bands = bands(count);
            for (int b = 0; b < bands.size(); b++) {
                if ((others & (1L << b)) != 0) {
                    continue;
                }
                int got = scatter.scatter(bands.get(b), 1, radius, (x, y) -> {
                    put(new Placed.Point(x, y, position));
                    scatter.reserve(x, y, rules.pointApart());
                });
                shortOf("point in band " + b, 1, got);
            }
        }

        /**
         * A control point at the centre of each clearing (G0115): on the tile nearest it, jittered inside the tile as
         * every scatter is, kept {@code pointApart} off any site already down; and the bit of the band each stands in,
         * which the ordinary points then leave to it. The clearings are laid so each falls in a band of its own.
         */
        private long clearingSites(int count, long taken, Randomness rng) {
            Scatter scatter = scatter(rng, 0f);
            for (Placed site : worked) {
                scatter.reserve(site.x(), site.y(), rules.pointApart());
            }
            float radius = config.controlPoint().radius();
            List<TileRect> bands = bands(count);
            long laid = taken;
            for (Clearing clearing : clearings) {
                float x = map.tileCentreX(clearing.col());
                float y = map.tileCentreY(clearing.row());
                int got = scatter.scatterNearest(
                        TileRect.around(map, x, y, map.tileSize()), x, y, 1, radius, (px, py) -> {
                            put(new Placed.Point(px, py, position));
                            scatter.reserve(px, py, rules.pointApart());
                        });
                shortOf("point in the clearing at " + clearing.col() + "," + clearing.row(), 1, got);
                int band = bandOf(bands, clearing.col());
                if (band >= 0) {
                    laid |= 1L << band;
                }
            }
            return laid;
        }

        /**
         * The worked sites that stand in a house (G0111), each on its own chance - at most one of a kind a biome - in a
         * house of this biome, never a gatehouse, against a wall and clear of its doorways; and the bit of the band
         * each stands in, which a control point then leaves to it. Put down here, first of everything.
         */
        private long housedSites(int count, Randomness rng, Randomness spot) {
            List<House> houses = new ArrayList<>();
            for (House house : laid.houses()) {
                if (inThisBiome(house)) {
                    houses.add(house);
                }
            }
            long taken = NO_OTHER_SITES;
            if (houses.isEmpty()) {
                return taken;
            }
            List<TileRect> bands = bands(count);
            // Kind after kind from the one stream, so a kind added after the last deals the earlier ones as before.
            for (WorkedSiteKind site : WorkedSiteKind.values()) {
                WorkedSiteDef def = config.workedSite(site);
                if (!def.housed() || rng.nextInt(PERCENT) >= def.chance()) {
                    continue;
                }
                House house = houses.get(rng.nextInt(houses.size()));
                List<float[]> spots = againstAWall(house, def.radius());
                if (spots.isEmpty()) {
                    continue;
                }
                float[] at = spots.get(spot.nextInt(spots.size()));
                int band = bandOf(bands, map.colAt(at[0]));
                if (band < 0 || (taken & (1L << band)) != 0) {
                    continue;
                }
                taken |= 1L << band;
                put(new Placed.Worked(site, at[0], at[1], position));
            }
            return taken;
        }

        /**
         * The worked sites of one kind that stand in the open (G0114): {@code count} of them, each in a band of its own
         * that no housed site took - dealt from {@code bands}, the biome's own stream, never the last biome's last band,
         * which leads to the finale - wherever in it its whole room is open ground: every tile it could reach its
         * biome's base, none kept, no rock, off every way in and the finale's watch, and {@code pointApart} off every
         * other site. A biome with fewer free bands lays fewer. Returns the bands taken, housed and open.
         */
        private long openSites(
                WorkedSiteKind which, int count, int sites, long taken, Randomness bands, Randomness rng) {
            if (count == 0) {
                return taken;
            }
            WorkedSiteDef site = config.workedSite(which);
            float clearing = site.clearing();
            List<TileRect> all = bands(sites);
            List<Integer> free = new ArrayList<>();
            for (int b = 0; b < all.size(); b++) {
                if ((taken & (1L << b)) == 0 && !(last && b == all.size() - 1)) {
                    free.add(b);
                }
            }
            Scatter scatter = scatter(rng, 0f).excluding(openGround(clearing));
            keepOffEntries(scatter, 0f);
            keepOffTheWatch(scatter, 0f);
            for (Placed other : worked) {
                scatter.reserve(other.x(), other.y(), rules.pointApart());
            }
            long laid = taken;
            for (int k = 0; k < count && !free.isEmpty(); k++) {
                int band = free.remove(/* index */ bands.nextInt(free.size()));
                laid |= 1L << band;
                int got = scatter.scatter(all.get(band), 1, clearing, (x, y) -> {
                    put(new Placed.Worked(which, x, y, position));
                    scatter.reserve(x, y, rules.pointApart());
                });
                shortOf(which + " in band " + band, 1, got);
            }
            return laid;
        }

        /**
         * The tiles a thing needing {@code clearing} of open ground round it may not be anchored on: any tile within
         * that of anywhere on it - a jittered anchor may stand anywhere on its tile - that is off the map, kept, or not
         * the biome's base (G0114).
         */
        private Scatter.Exclusion openGround(float clearing) {
            float tile = map.tileSize();
            float reach = clearing + tile * HALF_DIAGONAL;
            int span = (int) Math.ceil(reach / tile);
            Terrain base = def.ground().base();
            return (col, row) -> {
                float x = map.tileCentreX(col);
                float y = map.tileCentreY(row);
                for (int r = row - span; r <= row + span; r++) {
                    for (int c = col - span; c <= col + span; c++) {
                        float dx = Math.max(Math.max(c * tile - x, 0f), x - (c + 1) * tile);
                        float dy = Math.max(Math.max(r * tile - y, 0f), y - (r + 1) * tile);
                        if (dx * dx + dy * dy > reach * reach) {
                            continue;
                        }
                        if (!map.inBounds(c, r) || laid.kept(c, r) || map.terrainAtTile(c, r) != base) {
                            return true;
                        }
                    }
                }
                return false;
            };
        }

        /**
         * Where a worked site this wide can stand inside a house: against one of its walls, at the middle of a tile
         * beside it and clear of the wall - and clear of every doorway by its body and a man's width, so it never stands
         * in a way in (G0111).
         */
        private List<float[]> againstAWall(House house, float radius) {
            TileRect inside = house.inside();
            List<float[]> spots = new ArrayList<>();
            for (int col = inside.col(); col < inside.endCol(); col++) {
                spots.add(new float[] {map.tileCentreX(col), map.tileCentreY(inside.row())});
                spots.add(new float[] {map.tileCentreX(col), map.tileCentreY(inside.endRow() - 1)});
            }
            for (int row = inside.row() + 1; row < inside.endRow() - 1; row++) {
                spots.add(new float[] {map.tileCentreX(inside.col()), map.tileCentreY(row)});
                spots.add(new float[] {map.tileCentreX(inside.endCol() - 1), map.tileCentreY(row)});
            }
            float clear = radius + config.trooper().radius() * 2f;
            // On the tile beside the wall and clear of it, as everything put down is (G0042): a body under half a tile.
            spots.removeIf(
                    spot -> !map.isClear(spot[0], spot[1], radius) || nearADoorway(house, spot[0], spot[1], clear));
            return spots;
        }

        /** Whether a place is within {@code clear} of any tile of any of the house's doorways. */
        private boolean nearADoorway(House house, float x, float y, float clear) {
            float tile = map.tileSize();
            for (TileRect doorway : house.doorways()) {
                float outX = Math.max(Math.max(doorway.col() * tile - x, 0f), x - doorway.endCol() * tile);
                float outY = Math.max(Math.max(doorway.row() * tile - y, 0f), y - doorway.endRow() * tile);
                if (outX * outX + outY * outY < clear * clear) {
                    return true;
                }
            }
            return false;
        }

        /** Which band a column falls in, or {@code -1}. */
        private int bandOf(List<TileRect> bands, int col) {
            for (int b = 0; b < bands.size(); b++) {
                if (col >= bands.get(b).col() && col < bands.get(b).endCol()) {
                    return b;
                }
            }
            return -1;
        }

        /** Whether a house stands wholly inside this biome: a biome's own, never a gatehouse in a ridge. */
        private boolean inThisBiome(House house) {
            TileRect walls = house.walls();
            return rect.contains(walls.col(), walls.row()) && rect.contains(walls.endCol() - 1, walls.endRow() - 1);
        }

        /** What a house holds: crates first, then whatever else is not food, up to {@code houseStock} a house. */
        private void houseStock(int[] stock, Randomness rng) {
            Scatter scatter = new Scatter(map, rng, 0f, 0f);
            // Round a worked site standing in a house: the stock is laid about it, never on it (G0111).
            for (Placed standing : here) {
                if (standing instanceof Placed.Worked) {
                    scatter.reserve(standing.x(), standing.y(), radius(standing));
                }
            }
            for (House house : laid.houses()) {
                if (!inThisBiome(house)) {
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

        /**
         * The house packs (G0112): each house of this biome - never a gatehouse - holds a pack of each house line on
         * {@code housePackChance}, rolled and counted house by house from {@code rolls}, the biome's own stream, so
         * whether a house holds one and how many is the same on every attempt; laid inside it, round its stock and any
         * worked site, from {@code rng}. A house not wholly {@code startKeepOff} off the start, or reaching into the
         * finale's watch, still rolls but holds none: nothing hostile stands there.
         */
        private void housePacks(MissionConfig.Pressure pressure, Randomness rolls, Randomness rng) {
            Scatter scatter = new Scatter(map, rng, 0f, 0f);
            for (Placed standing : here) {
                scatter.reserve(standing.x(), standing.y(), radius(standing));
            }
            for (House house : laid.houses()) {
                if (!inThisBiome(house)) {
                    continue;
                }
                // Off its doorways, where its doors stand shut: a body inside, never one the door is put down on.
                float tile = map.tileSize();
                for (TileRect doorway : house.doorways()) {
                    for (int row = doorway.row(); row < doorway.endRow(); row++) {
                        for (int col = doorway.col(); col < doorway.endCol(); col++) {
                            scatter.reserve(map.tileCentreX(col), map.tileCentreY(row), tile * HALF_DIAGONAL);
                        }
                    }
                }
                for (Roster line : def.roster()) {
                    if (!(line instanceof Roster.House pack) || rolls.nextInt(PERCENT) >= rules.housePackChance()) {
                        continue;
                    }
                    int count = pack.count(rolls, pressure);
                    if (count == 0 || !clearOfTheStartAndTheWatch(house)) {
                        continue;
                    }
                    int got = scatter.scatter(
                            house.inside(),
                            count,
                            config.unit(pack.strain()).radius(),
                            (x, y) -> put(HousePacks.in(house, map, pack.strain(), x, y, position)));
                    shortOf("house pack of " + pack.strain(), count, got);
                }
            }
        }

        /** Whether the whole of a house's inside is {@code startKeepOff} off the start and outside the finale's watch. */
        private boolean clearOfTheStartAndTheWatch(House house) {
            float tile = map.tileSize();
            TileRect inside = house.inside();
            if (position == 0) {
                for (TileRect entry : entries) {
                    float dx = Math.max(0f, Math.max(entry.col() - inside.endCol(), inside.col() - entry.endCol()));
                    float dy = Math.max(0f, Math.max(entry.row() - inside.endRow(), inside.row() - entry.endRow()));
                    if ((dx * dx + dy * dy) * tile * tile < rules.startKeepOff() * rules.startKeepOff()) {
                        return false;
                    }
                }
            }
            if (last) {
                float nearestX = Math.clamp(hold.x(), inside.col() * tile, inside.endCol() * tile);
                float nearestY = Math.clamp(hold.y(), inside.row() * tile, inside.endRow() * tile);
                float dx = nearestX - hold.x();
                float dy = nearestY - hold.y();
                if (dx * dx + dy * dy <= hold.watch() * hold.watch()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Holes: off the way in, off every point and off each other; never in a house, nor in the finale's watch. A biome
         * with nooks puts its holes at their centres first, west to east, each nook the keep-offs leave room in taking
         * one, and scatters the rest as any other biome does.
         */
        private void nests(int[] creatures, Randomness rng) {
            Scatter scatter = scatter(rng, 0f).keepingOffRock(rules.rockMargin());
            keepOffEntries(scatter, entryKeepOff(rules.nestKeepOff()));
            keepOffTheWatch(scatter, 0f);
            for (Placed point : points) {
                scatter.reserve(point.x(), point.y(), rules.pointKeepOff());
            }
            for (Placed site : worked) {
                // Starting a download is as loud as starting a capture (G0111).
                scatter.reserve(site.x(), site.y(), rules.pointKeepOff());
            }
            for (Placed stocked : here) {
                if (stocked instanceof Placed.Pickup) {
                    scatter.reserve(stocked.x(), stocked.y(), rules.pickupKeepOff());
                }
            }
            boolean[] used = new boolean[nooks.size()];
            for (int i = 0; i < creatures.length; i++) {
                if (def.roster().get(i) instanceof Roster.Nest nest) {
                    Scatter.Place hole = (x, y) -> {
                        Placed placed = new Placed.Nest(nest.strain(), x, y, position);
                        put(placed);
                        nests.add(placed);
                        scatter.reserve(x, y, rules.nestApart());
                    };
                    int got = 0;
                    for (int k = 0; k < nooks.size() && got < creatures[i]; k++) {
                        if (used[k]) {
                            continue;
                        }
                        float x = map.tileCentreX(nooks.get(k).col());
                        float y = map.tileCentreY(nooks.get(k).row());
                        int put = scatter.scatterNearest(
                                TileRect.around(map, x, y, map.tileSize()),
                                x,
                                y,
                                1,
                                config.nest().radius(),
                                hole);
                        used[k] = put > 0;
                        got += put;
                    }
                    got += scatter.scatter(
                            rect, creatures[i] - got, config.nest().radius(), hole);
                    shortOf("nests of " + nest.strain(), creatures[i], got);
                }
            }
        }

        /**
         * Each loose line in packs round anchors kept off the passes in, the start and each other: encounters, not a
         * carpet. A biome with clearings anchors {@code clearingPackShare} of its packs inside them, dealt evenly pack by
         * pack from the first and the clearings in turn (G0115); a pack a clearing has no room for goes anywhere.
         */
        private void packs(int[] creatures, Randomness rng) {
            int share = def.furniture().clearingPackShare();
            int dealt = 0;
            int inClearings = 0;
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
                    Scatter.Place anchored = (x, y) -> {
                        anchor[0] = x;
                        anchor[1] = y;
                        anchoring.reserve(x, y, rules.packApart());
                    };
                    int found = 0;
                    if (!clearings.isEmpty() && inClearings * PERCENT < share * (dealt + 1)) {
                        Clearing clearing = clearings.get(inClearings % clearings.size());
                        anchoring.excluding((col, row) -> laid.kept(col, row) || !clearing.contains(col, row));
                        found = anchoring.scatter(clearingBox(clearing), 1, radius, anchored);
                        anchoring.excluding(laid::kept);
                        inClearings += found;
                    }
                    dealt++;
                    if (found == 0) {
                        found = anchoring.scatter(rect, 1, radius, anchored);
                    }
                    int got = 0;
                    if (found > 0) {
                        anchors.add(anchor);
                        got = members.scatterNearest(
                                TileRect.around(map, anchor[0], anchor[1], spread),
                                anchor[0],
                                anchor[1],
                                size,
                                radius,
                                (x, y) -> put(new Placed.Loose(loose.strain(), x, y, position)));
                    }
                    if (got < size) {
                        crowded = true;
                        got += joinPacks(members, loose.strain(), size - got, radius, spread, rng);
                    }
                    shortOf("pack of " + loose.strain(), size, got);
                }
            }
        }

        /** The tiles round a clearing, its disc and the corners of the square it sits in. */
        private static TileRect clearingBox(Clearing clearing) {
            int r = clearing.radius();
            return new TileRect(clearing.col() - r, clearing.row() - r, 2 * r + 1, 2 * r + 1);
        }

        /**
         * What a pack the biome has no room for does: its creatures join the packs already down, round each in turn
         * from one drawn, so a crowded biome has bigger encounters rather than creatures missing from its roster's
         * last lines (G0106). Draws nothing while every pack finds room - the counts a tier deals never come here.
         *
         * @return how many were put down
         */
        private int joinPacks(Scatter members, UnitKind strain, int count, float radius, float spread, Randomness rng) {
            if (anchors.isEmpty()) {
                return 0;
            }
            int first = rng.nextInt(anchors.size());
            int got = 0;
            for (int k = 0; k < anchors.size() && got < count; k++) {
                float[] at = anchors.get((first + k) % anchors.size());
                got += members.scatterNearest(
                        TileRect.around(map, at[0], at[1], spread),
                        at[0],
                        at[1],
                        count - got,
                        radius,
                        (x, y) -> put(new Placed.Loose(strain, x, y, position)));
            }
            return got;
        }

        /**
         * Crates the houses did not take are not laid (G0110): a biome's crates are its houses' stock, and the field's
         * are what its taken sites leave.
         */
        private void unhoused(int[] stock) {
            for (int line = 0; line < stock.length; line++) {
                if (crate(kindOf(line))) {
                    stock[line] = 0;
                }
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
            if (points.isEmpty()) {
                // Every site a relic (G0114): no point to stand a gun by.
                shortOf("turret by a point", count, 0);
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
         * Geysers (G0089), each {@code geyserKeepOff} off every point, nest and way in, the start among them - its fire is
         * nobody's - and off everything already standing by its own radius, drawn from a stream of its own. Before the
         * pickups, barrels and boulders, which then keep off it, so nothing that goes off stands in its fire.
         */
        private void geysers(int count, Randomness rng) {
            if (count == 0) {
                return;
            }
            Scatter scatter = scatter(rng, 0f);
            keepOffEntries(scatter, rules.geyserKeepOff());
            for (Placed point : points) {
                scatter.reserve(point.x(), point.y(), rules.geyserKeepOff());
            }
            for (Placed site : worked) {
                scatter.reserve(site.x(), site.y(), rules.geyserKeepOff());
            }
            for (Placed nest : nests) {
                scatter.reserve(nest.x(), nest.y(), rules.geyserKeepOff());
            }
            Randomness sizes = rng.derive(GEYSER_SIZES);
            int got = 0;
            for (int k = 0; k < count; k++) {
                float radius = config.geyser().radius(sizes);
                got += scatter.scatter(rect, 1, radius, (x, y) -> put(new Placed.Geyser(x, y, radius, position)));
            }
            shortOf("geysers", count, got);
        }

        /**
         * Emplacements of one kind (G0113), standing as a boulder does - {@code rockMargin} off rock and off everything
         * standing - and {@code emplacementKeepOff} off every way in (the start's own distance in the first biome),
         * every nest and the finale's watch, and off every site's edge; never on a thicket's tile. Each one's cycle is
         * dealt as it goes down, from the attempt's stream, so it is the cycle it fires for the whole level.
         */
        private void emplacements(EmplacementKind which, int count, Randomness rng) {
            if (count == 0) {
                return;
            }
            float keepOff = rules.emplacementKeepOff();
            Scatter scatter = scatter(rng, rules.rockMargin()).keepingOffRock(rules.rockMargin());
            keepOffEntries(scatter, entryKeepOff(keepOff));
            keepOffTheWatch(scatter, keepOff);
            for (Placed point : points) {
                scatter.reserve(point.x(), point.y(), config.controlPoint().radius() + keepOff);
            }
            for (Placed site : worked) {
                scatter.reserve(site.x(), site.y(), radius(site) + keepOff);
            }
            for (Placed nest : nests) {
                scatter.reserve(nest.x(), nest.y(), keepOff);
            }
            EmplacementDef def = config.emplacement(which);
            Randomness cycles = rng.derive(CYCLES);
            int got = scatter.scatter(
                    rect,
                    count,
                    def.radius(),
                    OFF_THICKET,
                    (x, y) ->
                            put(new Placed.Emplacement(which, x, y, def.cycle().deal(cycles), position)));
            shortOf("emplacements of " + which, count, got);
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
            float tile = map.tileSize();
            for (TileRect run : laid.ice()) {
                if (!rect.contains(run.col(), run.row())) {
                    continue;
                }
                // Ice is solid ground until broken (ADR 0025): nothing stands in it, as nothing stands in rock.
                for (int row = run.row(); row < run.endRow(); row++) {
                    for (int col = run.col(); col < run.endCol(); col++) {
                        scatter.reserve(map.tileCentreX(col), map.tileCentreY(row), tile * HALF_DIAGONAL);
                    }
                }
            }
            return scatter;
        }

        private void put(Placed thing) {
            here.add(thing);
            if (thing instanceof Placed.Point) {
                points.add(thing);
            } else if (thing instanceof Placed.Worked) {
                worked.add(thing);
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
            case Placed.Housed housed -> config.unit(housed.strain()).radius();
            case Placed.Pickup pickup -> config.pickup(pickup.kind()).radius();
            case Placed.Barrel _ -> config.pickup(PickupKind.BARREL).radius();
            case Placed.Boulder _ -> config.obstacle().radius();
            case Placed.Tree tree -> tree.trunk();
            case Placed.Geyser geyser -> geyser.radius();
            case Placed.Stone stone -> stone.radius();
            case Placed.Rabbit _ -> config.pickup(PickupKind.RABBIT).radius();
            case Placed.Hold _ -> config.controlPoint().radius();
            case Placed.Turret _ -> config.turret().radius();
            case Placed.Mine _ -> config.mine().radius();
            // Its room, so nothing laid after it stands among a relic's wardens (G0114).
            case Placed.Worked worked -> config.workedSite(worked.kind()).clearing();
            case Placed.Emplacement emplacement ->
                config.emplacement(emplacement.kind()).radius();
        };
    }

    private static EnumSet<Terrain> offThicket() {
        EnumSet<Terrain> ground = EnumSet.noneOf(Terrain.class);
        for (Terrain terrain : Terrain.values()) {
            if (terrain.passable() && terrain != Terrain.THICKET) {
                ground.add(terrain);
            }
        }
        return ground;
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
