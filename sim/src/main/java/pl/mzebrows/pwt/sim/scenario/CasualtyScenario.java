package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A firing line across open ground, with cover halfway and nothing but grass in between.
 *
 * <p>Built to make the price of a man visible. Everything in it serves one of these:
 *
 * <ul>
 *   <li><strong>The spitters.</strong> They hold their line rather than coming at you, so the ground
 *       between is ground you have to cross. Their cone is wide enough that a shot meant for you
 *       reaches the men beside you - which is how the squad starts taking hits without anything ever
 *       having aimed at a trooper.
 *   <li><strong>The open ground.</strong> Long, and deliberately empty. A man who goes down in the
 *       middle of it is a decision: stand over him in the open for a second and a half, or haul him
 *       back to the rock and pay double for it at the other end.
 *   <li><strong>The two stones.</strong> The only thing between the line and the start. They are
 *       where a drag is meant to end, and they are far enough apart that backing to one of them takes
 *       long enough to be caught doing it.
 *   <li><strong>The control point in the middle.</strong> Taking it is loud: every alien inside its
 *       rouse radius is provoked on the tick the capture starts, and a shooter answers that by
 *       walking to the place - it does not pursue anybody, but it can be summoned. So the point is
 *       a choice about <em>when</em> the line comes to you, which is the only initiative the player
 *       has against something that will not close on its own.
 *   <li><strong>Barrels for half the line, and no more.</strong> Every spitter is a problem and only
 *       some of them come with the answer standing in front of them. Which half is which is the
 *       thing to read before committing, because the ones without a barrel have to be taken the
 *       expensive way.
 *   <li><strong>One of those spitters standing against its barrel.</strong> It swallows the thing in
 *       the first second and spends the rest of the level firing shots that go off where they land -
 *       ringed in the barrel's own orange-red, and unmistakable. It is there to be identified and
 *       dealt with first, and to show what every other barrel on the field is worth to the other
 *       side if you leave it standing.
 *   <li><strong>The barrel by the start.</strong> Draggable. Haul it up the field and shoot it, or
 *       leave it; either way it shows that hauling is about a load rather than about a man.
 *   <li><strong>Two troopers, not four.</strong> A squad small enough that losing one is the level
 *       rather than an inconvenience - which is the only size at which going back for a man is a
 *       decision rather than arithmetic.
 * </ul>
 *
 * <p>Worth watching for: a man shot down twice in a row. The first hit puts him on the ground and the
 * second finishes him, because he is already at zero - so a casualty left lying in the line of fire
 * is not merely on a clock, he is a target. That is the rule that makes dragging worth its cost.
 */
final class CasualtyScenario implements Scenario {

    private static final int SPITTERS = 5;

    /** How many of them get a barrel. Half, rounded down, so the last man on the line has none. */
    private static final int BARRELS = SPITTERS / 2;

    /** Small on purpose: losing one of two men is the level going wrong, not a rounding error. */
    private static final int SQUAD = 2;

    /** Where the line stands, as a fraction of the arena's width. */
    private static final float LINE_X = 0.72f;

    /** The two stones that are the only cover on the field. */
    private static final float COVER_X = 0.34f;

    private static final float COVER_TOP = 0.3f;
    private static final float COVER_BOTTOM = 0.7f;

    /** How many tiles across each stone is. Big enough to get a man behind, small enough to flank. */
    private static final int COVER_TILES = 2;

    /** The barrel sits beside where the squad starts, within reach of a first drag. */
    private static final float BARREL_X = 0.18f;

    /**
     * Where the point sits, as a fraction of the arena's width.
     *
     * <p>Between the cover and the line, and inside the point's own rouse radius of the line, so
     * starting the capture is what brings the shooters - which is the whole reason the point is here
     * rather than somewhere safe.
     */
    private static final float POINT_X = 0.52f;

    /**
     * How far in front of the line its barrels stand, as a fraction of the arena's width.
     *
     * <p>Well inside a barrel's blast and well outside anything a spitter will cross: it holds its
     * ground and it does not go looking for food it is wary of, so a barrel at this distance is the
     * player's to use and nobody else's.
     */
    private static final float BARREL_STANDOFF = 0.06f;

    /** Which man on the line is stood against his barrel, and therefore arrives carrying it. */
    private static final int CHARGED = 1;

    /** Close enough that the two bodies overlap on the first tick, so the swallow is immediate. */
    private static final float SWALLOWING_DISTANCE = 2.5f;

    /** How much of the arena's height the line spreads over. */
    private static final float SPREAD = 0.7f;

    @Override
    public String name() {
        return "Casualty";
    }

    @Override
    public String description() {
        return "A spitter line, barrels for half of it, and a point in the middle that calls them over.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return base.withUnit(UnitKind.SPITTER, spitter -> spitter.withCount(SPITTERS))
                .withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(0))
                .withUnit(UnitKind.BRUTE, brute -> brute.withCount(0))
                .withTrooper(base.trooper().withSquadSize(SQUAD))
                .withObstacle(base.obstacle().withCount(0));
    }

    /**
     * Two stones and nothing else. They are laid rather than scattered because where the cover is
     * <em>is</em> the scenario: the whole decision is how far back a man has to be hauled, and that
     * is a distance somebody has to choose.
     */
    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        SimConfig.MapConfig shape = config.map();
        TileMap.Builder builder = TileMap.builder(shape.cols(), shape.rows(), shape.tileSize());
        Maps.partitionRegions(builder, shape.regions(), randomness);

        int col = (int) (shape.cols() * COVER_X);
        return builder.rect(col, row(shape, COVER_TOP), COVER_TILES, COVER_TILES, Terrain.ROCK)
                .rect(col, row(shape, COVER_BOTTOM), COVER_TILES, COVER_TILES, Terrain.ROCK)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        float width = map.worldWidth();
        float height = map.worldHeight();

        // Before anything that stands on it: a point is ground, and the squad is put down next.
        simulation.spawnControlPoint(width * POINT_X, height * 0.5f);

        deployPlayerSide(simulation);

        simulation.spawnBarrel(width * BARREL_X, height * 0.5f);

        deployTheLine(simulation, width, height);

        simulation.level().require(Objective.CAPTURE_POINTS);
    }

    /**
     * The firing line: a barrel in front of some of them, one man standing on his, and the rest
     * with nothing.
     *
     * <p>Laid together rather than scattered, because the pairing <em>is</em> the level: a spitter
     * with a barrel in front of it is a problem with an answer a few strides away, the one already
     * standing on its barrel is a problem whose answer has been eaten, and the ones with nothing are
     * what the level costs if you spend the barrels badly.
     */
    private static void deployTheLine(Simulation simulation, float width, float height) {
        float lineX = width * LINE_X;
        for (int i = 0; i < SPITTERS; i++) {
            float y = height * spread(0.5f - SPREAD * 0.5f, 0.5f + SPREAD * 0.5f, i, SPITTERS);
            simulation.spawnUnit(UnitKind.SPITTER, lineX, y);
            if (i >= BARRELS) {
                continue;
            }
            simulation.spawnBarrel(i == CHARGED ? lineX - SWALLOWING_DISTANCE : lineX - width * BARREL_STANDOFF, y);
        }
    }

    /** The tile row a fraction of the way up the arena, with the stone's own height allowed for. */
    private static int row(SimConfig.MapConfig shape, float fraction) {
        int top = (int) (shape.rows() * fraction) - COVER_TILES / 2;
        return Math.max(0, Math.min(shape.rows() - COVER_TILES, top));
    }

    /** Evenly spaces {@code count} things between two fractions, both ends included. */
    private static float spread(float from, float to, int index, int count) {
        return count <= 1 ? (from + to) * 0.5f : from + (to - from) * index / (count - 1);
    }
}
