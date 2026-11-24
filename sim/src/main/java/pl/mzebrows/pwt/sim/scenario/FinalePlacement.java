package pl.mzebrows.pwt.sim.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.Strip;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.FinaleDef;
import pl.mzebrows.pwt.sim.FinaleRules;
import pl.mzebrows.pwt.sim.FinaleRules.CoverShape;
import pl.mzebrows.pwt.sim.FinaleRules.Lot;
import pl.mzebrows.pwt.sim.LairLine;
import pl.mzebrows.pwt.sim.Mouth;
import pl.mzebrows.pwt.sim.Offset;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.Arrival;
import pl.mzebrows.pwt.sim.def.BarrageDef;
import pl.mzebrows.pwt.sim.def.PatternDef;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * A finale placed by its rules round its skeleton (G0120): its cover first, in tile offsets from the anchor on its bare
 * ground before the strip is laid, then everything that stands round the post on the strip as laid - guns on mounts,
 * pickups, the lair's gun caches, barrels, mines - each through {@link Scatter} in mirrored north-south pairs about the
 * anchor's row, from a stream of its own derived from the finale's. Nothing here reads the tier.
 *
 * <p>A lot that cannot place all it was dealt reports it ({@link #shortfalls}), a generator fault the sweep fails.
 */
final class FinalePlacement {

    private static final long COUNTS = 0x51C3_A90E_7B24_D6F1L;
    private static final long COVER = 0x9E06_4D7B_2A1F_C385L;
    private static final long SHAPES = 0x3F8A_D152_6C09_B7E4L;
    private static final long TURRETS = 0xC74B_0E39_F612_8A5DL;
    private static final long PICKUPS = 0x6D21_8FB4_03CE_957AL;
    private static final long LAIR = 0xA5F9_3C60_D84B_1E27L;
    private static final long BARRELS = 0x1B6E_C7A2_95F0_4D38L;
    private static final long MINES = 0xE839_52D1_4AB7_06FCL;

    /** How many times the cover is dealt again, from the next attempt's stream, before it reports a shortfall (G0070). */
    private static final int ATTEMPTS = 32;

    /** No limit of reach: everywhere is within it. */
    private static final float[] ANYWHERE = {0f, 0f, Float.POSITIVE_INFINITY};

    /** A point of a route reserved every so much of a tile, so nothing laid between two is on it. */
    private static final float ROUTE_STEP = 0.5f;

    private final SimConfig config;
    private final FinaleDef def;
    private final FinaleRules rules;
    private final Randomness stream;
    private final float tile;
    private final int coverPairs;
    private final int barrelPairs;
    private final int minePairs;
    private final List<String> shortfalls = new ArrayList<>();

    /** @param def the finale's skeleton as it is laid - flipped, if it is - with no cover yet */
    FinalePlacement(SimConfig config, FinaleDef def, Randomness stream) {
        this.config = config;
        this.def = def;
        this.rules = def.rules();
        this.stream = stream;
        this.tile = config.map().tileSize();
        // Drawn once, in this order, before anything is placed: a lot that falls short moves no other's count.
        Randomness counts = stream.derive(COUNTS);
        this.coverPairs = rules.cover().pairs(counts);
        this.barrelPairs = rules.barrels().pairs(counts);
        this.minePairs = rules.mines().pairs(counts);
    }

    /** Every lot that fell short: none, on a finale whose rules fit its ground. */
    List<String> shortfalls() {
        return shortfalls;
    }

    /**
     * The cover, in tile offsets from the anchor: dealt on the finale's bare ground - its base, a border of rock and its
     * own pieces, as wide as it is and as tall as the strip - so it is ground before the strip is laid. Each piece is a
     * shape from the block's list, in the cover's band, off the post, the mouths, the lair and the routes between them
     * by the rules' gap, its mirror north of the anchor's row; an L turns its corner to what it shelters from. A deal
     * that cannot lay every pair is dealt again from the next attempt's stream, its count kept, as a biome is (G0070).
     */
    List<TileRect> cover() {
        int width = def.ground().widthMin();
        int rows = config.mission().ground().rows();
        TileRect anchor = Strip.anchor(new TileRect(0, 0, width, rows));
        TileMap.Builder ground =
                TileMap.builder(width, rows, tile).fill(def.ground().base()).border(1, Terrain.ROCK);
        for (TileRect piece : def.ground().pieces()) {
            ground.rect(
                    anchor.col() + piece.col(),
                    anchor.row() + piece.row(),
                    piece.width(),
                    piece.height(),
                    Terrain.ROCK);
        }
        TileMap bare = ground.build();
        List<TileRect> best = List.of();
        int bestPairs = -1;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            List<TileRect> cover = new ArrayList<>();
            int pairs = cover(bare, anchor, stream.derive(COVER).derive(attempt), cover);
            if (pairs == coverPairs) {
                return List.copyOf(cover);
            }
            if (pairs > bestPairs) {
                best = cover;
                bestPairs = pairs;
            }
        }
        shortOf("pieces of cover", coverPairs * 2, bestPairs * 2);
        return List.copyOf(best);
    }

    /** One attempt at the cover, into {@code cover}: how many of its pairs it laid. */
    private int cover(TileMap bare, TileRect anchor, Randomness attempt, List<TileRect> cover) {
        float row = bare.tileCentreY(anchor.row());
        // A tile between anything and a piece's own circle, so the ring round every piece is open ground; the rules'
        // gap
        // between pieces is reserved round each as it is laid, the piece where it fell rather than where it was asked.
        Scatter.Exclusion band = band(bare, anchor, rules.cover(), rules.inReach() ? barrage(bare, anchor) : ANYWHERE);
        Scatter scatter = new Scatter(bare, attempt, tile, 0f)
                .keepingOffRock(rules.gap() * tile)
                .mirroredAbout(row);
        reserveSkeleton(scatter, bare, anchor);
        reserveRoutes(scatter, bare, anchor);
        Randomness shapes = attempt.derive(SHAPES);
        TileRect everywhere = new TileRect(0, 0, bare.cols(), bare.rows());
        int laid = 0;
        for (int k = 0; k < coverPairs; k++) {
            CoverShape shape = rules.shapes().get(shapes.nextInt(rules.shapes().size()));
            float radius = halfDiagonal(shape.width(), shape.height()) * tile;
            // Far enough south of the anchor's row that the rows between a piece and its mirror are at least the gap,
            // wherever in its tile the scatter puts it: never a one-tile slot between the two.
            int northmost = (int) Math.floor(-(rules.gap() + 1f) / 2f - shape.height() / 2f - 0.5f);
            scatter.excluding((col, r) -> r - anchor.row() > northmost || band.excludes(col, r));
            int got = scatter.scatter(everywhere, 2, radius, (x, y) -> {
                if (y < row) {
                    List<TileRect> piece = piece(shape, bare, anchor, x, y);
                    cover.addAll(piece);
                    for (TileRect rect : piece) {
                        cover.add(Offset.flipped(rect));
                    }
                    // Its box as laid, and its mirror's, kept the gap off whatever comes after.
                    float boxX = (anchor.col() + piece.getFirst().col()) * tile + shape.width() * tile * 0.5f;
                    float boxY = (anchor.row() + bottom(piece)) * tile + shape.height() * tile * 0.5f;
                    float off = radius + (rules.gap() - 1f) * tile;
                    scatter.reserve(boxX, boxY, off);
                    scatter.reserve(boxX, 2f * row - boxY, off);
                }
            });
            laid += got / 2;
        }
        return laid;
    }

    /**
     * Everything that stands round the post, on the strip as laid: so many pairs of each lot, in its band, off the
     * mouths, the lair's spots and one another by the rules' {@code apart}, off kept ground and the last biome.
     *
     * @param finale the finale's rect on the strip
     * @param position the finale's position, after the last biome
     * @param lair where its nests and its boss rise, as placed
     */
    Items items(Strip.Laid laid, TileRect finale, int position, List<Arrival> lair) {
        TileMap map = laid.map();
        TileRect anchor = Strip.anchor(finale);
        List<float[]> standing = new ArrayList<>();
        List<Placed> placed = new ArrayList<>();
        List<Arrival> caches = new ArrayList<>();
        for (Arrival arrival : lair) {
            standing.add(new float[] {arrival.x(), arrival.y(), radius(arrival)});
        }
        float[] barrage = rules.inReach() ? barrage(map, anchor) : ANYWHERE;

        lot(
                map,
                laid,
                finale,
                anchor,
                standing,
                TURRETS,
                rules.turrets(),
                rules.turrets().max() / 2,
                config.turret().radius(),
                barrage,
                (x, y) -> placed.add(new Placed.Turret(x, y, position)),
                "turrets");
        int[] next = {0};
        lot(
                map,
                laid,
                finale,
                anchor,
                standing,
                PICKUPS,
                rules.pickups(),
                rules.pickups().max() / 2,
                widest(rules.pickupKinds()),
                ANYWHERE,
                (x, y) -> {
                    PickupKind kind = rules.pickupKinds().get(next[0]++).kind();
                    placed.add(new Placed.Pickup(kind, x, y, position));
                },
                "pickups");
        int[] cache = {0};
        lot(
                map,
                laid,
                finale,
                anchor,
                standing,
                LAIR,
                rules.lair(),
                rules.lair().max() / 2,
                widest(rules.lairKinds()),
                barrage,
                (x, y) -> caches.add(
                        Arrival.pickup(rules.lairKinds().get(cache[0]++).kind(), x, y)),
                "the lair's caches");
        lot(
                map,
                laid,
                finale,
                anchor,
                standing,
                BARRELS,
                rules.barrels(),
                barrelPairs,
                config.pickup(PickupKind.BARREL).radius(),
                ANYWHERE,
                (x, y) -> placed.add(new Placed.Barrel(x, y, position)),
                "barrels");
        lot(
                map,
                laid,
                finale,
                anchor,
                standing,
                MINES,
                rules.mines(),
                minePairs,
                config.mine().radius(),
                ANYWHERE,
                (x, y) -> placed.add(new Placed.Mine(x, y, position)),
                "mines");
        return new Items(placed, caches);
    }

    /**
     * What {@link #items} put down.
     *
     * @param placed the guns, pickups, barrels and mines round the post, in that order
     * @param caches the gun caches the lair raises, in the order its kinds are named
     */
    record Items(List<Placed> placed, List<Arrival> caches) {}

    /** One lot's pairs through a scatter of its own stream, off everything standing; what it puts down stands too. */
    private void lot(
            TileMap map,
            Strip.Laid laid,
            TileRect finale,
            TileRect anchor,
            List<float[]> standing,
            long salt,
            Lot lot,
            int pairs,
            float radius,
            float[] within,
            Scatter.Place place,
            String what) {
        if (pairs == 0) {
            return;
        }
        Scatter.Exclusion inBand = band(map, anchor, lot, within);
        Scatter scatter = new Scatter(map, stream.derive(salt), rules.apart() * tile, 0f)
                .mirroredAbout(map.tileCentreY(anchor.row()))
                .excluding(
                        (col, row) -> !finale.contains(col, row) || laid.kept(col, row) || inBand.excludes(col, row));
        for (float[] circle : standing) {
            scatter.reserve(circle[0], circle[1], circle[2]);
        }
        for (Mouth mouth : def.hold().mouths()) {
            TileRect rect = mouth.from(anchor);
            for (int row = rect.row(); row < rect.endRow(); row++) {
                for (int col = rect.col(); col < rect.endCol(); col++) {
                    scatter.reserve(map.tileCentreX(col), map.tileCentreY(row), tile * 0.75f);
                }
            }
        }
        int got = scatter.scatter(finale, pairs * 2, radius, (x, y) -> {
            standing.add(new float[] {x, y, radius});
            place.at(x, y);
        });
        shortOf(what, pairs * 2, got);
    }

    /**
     * Where a lot's first of a pair may stand: south of the anchor's row - its mirror goes north - in its band round
     * the post, and within {@code reach} ({@code x y distance}) of a place: the boss's barrage, or {@link #ANYWHERE}.
     */
    private Scatter.Exclusion band(TileMap map, TileRect anchor, Lot lot, float[] reach) {
        Offset post = def.hold().post();
        return (col, row) -> {
            int dx = col - anchor.col();
            int dy = row - anchor.row();
            if (dy >= 0) {
                return true;
            }
            float fromX = dx - post.dx();
            float fromY = dy - post.dy();
            if (!lot.holds((float) Math.sqrt(fromX * fromX + fromY * fromY), dx)) {
                return true;
            }
            float toX = map.tileCentreX(col) - reach[0];
            float toY = map.tileCentreY(row) - reach[1];
            return toX * toX + toY * toY > reach[2] * reach[2];
        };
    }

    /** The post, every mouth's tiles and every spot of the lair, each as wide as it stands. */
    private void reserveSkeleton(Scatter scatter, TileMap map, TileRect anchor) {
        TileRect post = def.hold().post().from(anchor);
        scatter.reserve(map.tileCentreX(post.col()), map.tileCentreY(post.row()), 0f);
        for (Mouth mouth : def.hold().mouths()) {
            TileRect rect = mouth.from(anchor);
            for (int row = rect.row(); row < rect.endRow(); row++) {
                for (int col = rect.col(); col < rect.endCol(); col++) {
                    scatter.reserve(map.tileCentreX(col), map.tileCentreY(row), tile * 0.5f);
                }
            }
        }
        for (LairLine line : def.lair()) {
            TileRect at = line.at().from(anchor);
            scatter.reserve(map.tileCentreX(at.col()), map.tileCentreY(at.row()), radius(line));
        }
    }

    /** A straight line from every mouth's middle to the post, and from the post to every unit the lair raises. */
    private void reserveRoutes(Scatter scatter, TileMap map, TileRect anchor) {
        TileRect post = def.hold().post().from(anchor);
        float postX = map.tileCentreX(post.col());
        float postY = map.tileCentreY(post.row());
        for (Mouth mouth : def.hold().mouths()) {
            TileRect rect = mouth.from(anchor);
            route(scatter, rect.centreX(map), rect.centreY(map), postX, postY);
        }
        for (LairLine line : def.lair()) {
            if (line instanceof LairLine.Unit) {
                TileRect at = line.at().from(anchor);
                route(scatter, postX, postY, map.tileCentreX(at.col()), map.tileCentreY(at.row()));
            }
        }
    }

    private void route(Scatter scatter, float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(1, (int) (length / (tile * ROUTE_STEP)));
        for (int i = 0; i <= steps; i++) {
            scatter.reserve(fromX + dx * i / steps, fromY + dy * i / steps, 0f);
        }
    }

    /**
     * The rects of one piece centred here, in tile offsets from the anchor. An L's corner is on the side of what it
     * shelters from - the lair's unit, or else the nearest mouth, or else the post - so its lee is inside the L.
     */
    private List<TileRect> piece(CoverShape shape, TileMap map, TileRect anchor, float x, float y) {
        int col = Math.round(x / tile - shape.width() * 0.5f);
        int row = Math.round(y / tile - shape.height() * 0.5f);
        int dx = col - anchor.col();
        int dy = row - anchor.row();
        if (shape instanceof CoverShape.Bar) {
            return List.of(new TileRect(dx, dy, shape.width(), shape.height()));
        }
        float[] threat = threat(map, anchor, x, y);
        int armRow = threat[1] > y ? dy + shape.height() - 1 : dy;
        int armCol = threat[0] > x ? dx + shape.width() - 1 : dx;
        return List.of(
                new TileRect(dx, armRow, shape.width(), 1),
                new TileRect(armCol, armRow == dy ? dy + 1 : dy, 1, shape.height() - 1));
    }

    /** Half a box's diagonal, in tiles. */
    private static float halfDiagonal(int width, int height) {
        return (float) Math.sqrt(width * width + height * height) * 0.5f;
    }

    /** The southmost row offset of a piece's rects. */
    private static int bottom(List<TileRect> piece) {
        int bottom = Integer.MAX_VALUE;
        for (TileRect rect : piece) {
            bottom = Math.min(bottom, rect.row());
        }
        return bottom;
    }

    /** What a piece here shelters from, in world units. */
    private float[] threat(TileMap map, TileRect anchor, float x, float y) {
        for (LairLine line : def.lair()) {
            if (line instanceof LairLine.Unit) {
                TileRect at = line.at().from(anchor);
                return new float[] {map.tileCentreX(at.col()), map.tileCentreY(at.row())};
            }
        }
        float[] nearest = null;
        float best = Float.MAX_VALUE;
        for (Mouth mouth : def.hold().mouths()) {
            TileRect rect = mouth.from(anchor);
            float mx = rect.centreX(map);
            float my = rect.centreY(map);
            float d = (mx - x) * (mx - x) + (my - y) * (my - y);
            if (d < best) {
                best = d;
                nearest = new float[] {mx, my};
            }
        }
        if (nearest != null) {
            return nearest;
        }
        TileRect post = def.hold().post().from(anchor);
        return new float[] {map.tileCentreX(post.col()), map.tileCentreY(post.row())};
    }

    /** Where the lair's unit with a barrage rises - the keep's boss - and how far it reaches: {@code x y distance}. */
    private float[] barrage(TileMap map, TileRect anchor) {
        LairLine.Unit unit = barrager();
        TileRect at = unit.at().from(anchor);
        return new float[] {map.tileCentreX(at.col()), map.tileCentreY(at.row()), reach()};
    }

    /**
     * How far the boss's barrage reaches: the shortest range of the guns its patterns fire - a shot that flies until it
     * hits something reaching any distance - so a man anywhere inside it takes every pattern.
     */
    private float reach() {
        BarrageDef barrage = config.unit(barrager().strain()).barrage();
        float reach = Float.MAX_VALUE;
        for (PatternDef pattern : List.of(barrage.ring(), barrage.fan(), barrage.spiral(), barrage.stream())) {
            float range = config.weapon(pattern.gun()).projectileRange();
            if (pattern.enabled() && range > 0f) {
                reach = Math.min(reach, range);
            }
        }
        return reach;
    }

    private LairLine.Unit barrager() {
        for (LairLine line : def.lair()) {
            if (line instanceof LairLine.Unit unit
                    && config.unit(unit.strain()).barrage().enabled()) {
                return unit;
            }
        }
        throw new IllegalStateException("a finale whose guns stand inside a barrage raises a unit with one");
    }

    private float radius(LairLine line) {
        return switch (line) {
            case LairLine.Unit unit -> config.unit(unit.strain()).radius();
            case LairLine.Nest nest -> config.nest().radius();
            case LairLine.Pickup pickup -> config.pickup(pickup.kind()).radius();
        };
    }

    private float radius(Arrival arrival) {
        return switch (arrival.form()) {
            case UNIT -> config.unit(Objects.requireNonNull(arrival.strain())).radius();
            case NEST -> config.nest().radius();
            case PICKUP ->
                config.pickup(Objects.requireNonNull(arrival.pickup())).radius();
        };
    }

    private float widest(List<FinaleRules.Pick> kinds) {
        float widest = 0f;
        for (FinaleRules.Pick pick : kinds) {
            widest = Math.max(widest, config.pickup(pick.kind()).radius());
        }
        return widest;
    }

    private void shortOf(String what, int wanted, int got) {
        if (got < wanted) {
            shortfalls.add("finale (" + def.ground().base() + "): " + got + " of " + wanted + " " + what);
        }
    }
}
