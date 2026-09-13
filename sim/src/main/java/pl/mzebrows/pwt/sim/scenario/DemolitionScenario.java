package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A wall with one doorway, a stack of barrels behind it, and the aliens standing among them.
 *
 * <p>Built to make explosions visible rather than to be fair. Everything in it is one of the four
 * things worth looking at:
 *
 * <ul>
 *   <li><strong>The wall.</strong> A blast does not reach through rock, so the stack cannot be shot
 *       from this side of it - but a grenade comes off rock and keeps its full flight, so it can be
 *       banked through the doorway or round the end. That is the throw worth learning.
 *   <li><strong>The stack.</strong> Five barrels nine units apart and a blast twelve wide, so the
 *       first one takes the rest with it a tick at a time. Watch it travel rather than happen.
 *   <li><strong>The two barrels on this side.</strong> They are within a throw of where you are
 *       standing, and the blast is on nobody's side. Setting one off in your own face is how the
 *       radius gets learnt.
 *   <li><strong>The brutes.</strong> One circle, and death inside it - a brute dies to a blast exactly
 *       as readily as a grunt does, which no other weapon in the game can say.
 * </ul>
 *
 * <p>Also worth watching: the view. The shake is felt well past the killing circle, so a barrel going
 * off behind the wall tells you about it even when you were looking somewhere else - and it is far
 * subtler in the pulled-back view, because the jolt is a distance in the world rather than in pixels.
 */
final class DemolitionScenario implements Scenario {

    private static final int GRUNTS = 6;
    private static final int BRUTES = 2;
    private static final int BARRELS_IN_THE_STACK = 5;

    /** Where the wall stands, as a fraction of the arena's width. */
    private static final float WALL_X = 0.62f;

    /** How many tile rows the doorway is. Wide enough that a brute fits through it. */
    private static final int DOORWAY_ROWS = 4;

    /** Where things sit across the arena, as fractions of its width. */
    private static final float LOOSE_BARREL_X = 0.4f;

    private static final float STACK_X = 0.78f;
    private static final float GRUNT_X = 0.9f;
    private static final float BRUTE_X = 0.95f;

    /** The stack runs from here to here up the arena. Closer together than a blast is wide. */
    private static final float STACK_FROM = 0.3f;

    private static final float STACK_TO = 0.7f;

    /** How much of the arena's height the aliens spread over. */
    private static final float SPREAD = 0.6f;

    @Override
    public String name() {
        return "Demolition";
    }

    @Override
    public String description() {
        return "Barrels behind a wall a blast cannot cross. Bank a grenade through the door and watch it go up.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return base.withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(GRUNTS))
                .withUnit(UnitKind.BRUTE, brute -> brute.withCount(BRUTES))
                .withObstacle(base.obstacle().withCount(0));
    }

    /**
     * One wall, one doorway. Everything behind it has to stay reachable - a sealed pocket would fail
     * the standing check that nothing is spawned where the commander cannot get to it - so the gap is
     * cut before anything is placed rather than hoped for afterwards.
     */
    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        SimConfig.MapConfig shape = config.map();
        TileMap.Builder builder = TileMap.builder(shape.cols(), shape.rows(), shape.tileSize());
        Maps.partitionRegions(builder, shape.regions(), randomness);

        int col = (int) (shape.cols() * WALL_X);
        int doorway = Math.min(DOORWAY_ROWS, shape.rows());
        int door = Math.max(0, (shape.rows() - doorway) / 2);
        return builder.rect(col, 0, 1, shape.rows(), Terrain.ROCK)
                .rect(col, door, 1, doorway, Terrain.PLAIN)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        float width = map.worldWidth();
        float height = map.worldHeight();

        deployPlayerSide(simulation);

        simulation.spawnBarrel(width * LOOSE_BARREL_X, height * (0.5f - SPREAD * 0.5f));
        simulation.spawnBarrel(width * LOOSE_BARREL_X, height * (0.5f + SPREAD * 0.5f));

        for (int i = 0; i < BARRELS_IN_THE_STACK; i++) {
            simulation.spawnBarrel(width * STACK_X, height * spread(STACK_FROM, STACK_TO, i, BARRELS_IN_THE_STACK));
        }

        int grunts = simulation.config().unit(UnitKind.GRUNT).count();
        for (int i = 0; i < grunts; i++) {
            simulation.spawnUnit(
                    UnitKind.GRUNT,
                    width * GRUNT_X,
                    height * spread(0.5f - SPREAD * 0.5f, 0.5f + SPREAD * 0.5f, i, grunts));
        }

        int brutes = simulation.config().unit(UnitKind.BRUTE).count();
        for (int i = 0; i < brutes; i++) {
            simulation.spawnUnit(
                    UnitKind.BRUTE,
                    width * BRUTE_X,
                    height * spread(0.5f - SPREAD * 0.5f, 0.5f + SPREAD * 0.5f, i, brutes));
        }
    }

    /** Evenly spaces {@code count} things between two fractions, both ends included. */
    private static float spread(float from, float to, int index, int count) {
        return count <= 1 ? (from + to) * 0.5f : from + (to - from) * index / (count - 1);
    }
}
