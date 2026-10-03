package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.PickupKind;
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
 *   <li><strong>What it leaves burning.</strong> Every one of those circles now leaves ground on fire
 *       for five to ten seconds (G0035), and the fire is on nobody's side exactly as the blast was.
 *       The cascade is the thing to watch: five barrels going up a tick apart leave a strip of
 *       burning ground across the stack that has to go out before anybody can walk through it.
 *   <li><strong>The guns.</strong> Two crates of each on the left, before the wall, so the same
 *       throw and the same stack can be answered with a railgun, a flamethrower, a shotgun, the
 *       beam, a bazooka, a pulse rifle or a disc gun. Two of each because a gun is lost with a death and this arena is one of the easier
 *       places to die in. The flamethrower is the one gun that leaves no fire, because it leaves no
 *       blast. The bazooka's rocket is a barrel you carry - a blast on nobody's side, so the doorway
 *       is a bad place to fire it from - the pulse rifle holds a brute still for the throw, and the
 *       disc gun's disc comes off the wall and the ranks and back, harming none of his own.
 *   <li><strong>The beam, and what it does to what it has already killed.</strong> It stops at the
 *       first thing it touches, so in ranks like these it is a gun for taking one thing apart while
 *       the rest walk in - and what it leaves lying about can be inflated with the same beam until
 *       it goes off on its own, which is how it answers the rest of them. Both come out of one heat
 *       bar, and that is the decision the gun is really made of.
 *   <li><strong>The rest of the roster.</strong> Spitters behind the stack shooting out through the
 *       doorway, runners that arrive before you have lined the throw up, and frogs ruining the ground
 *       you were going to stand on. A stack of barrels among things that only walk at you is a
 *       puzzle; a stack among things that shoot back is the mechanic.
 * </ul>
 *
 * <p>Also worth watching: the view. The shake is felt well past the killing circle, so a barrel going
 * off behind the wall tells you about it even when you were looking somewhere else - and it is far
 * subtler in the pulled-back view, because the jolt is a distance in the world rather than in pixels.
 */
final class DemolitionScenario implements Scenario {

    private static final int GRUNTS = 6;
    private static final int BRUTES = 4;
    private static final int SPITTERS = 1;
    private static final int RUNNERS = 1;
    private static final int FROGS = 2;
    private static final int BARRELS_IN_THE_STACK = 5;

    /** Two of every gun there is, so one throw can be answered seven ways and twice each. */
    private static final PickupKind[] CACHES = {
        PickupKind.ARMOURY_RAILGUN,
        PickupKind.ARMOURY_FLAMER,
        PickupKind.ARMOURY_SHOTGUN,
        PickupKind.ARMOURY_LASER,
        PickupKind.ARMOURY_BAZOOKA,
        PickupKind.ARMOURY_PULSE,
        PickupKind.ARMOURY_DISC
    };

    /** The two columns the crates stand in, as fractions of the arena's width. Clear of the spawn. */
    private static final float NEAR_CACHE_X = 0.14f;

    private static final float FAR_CACHE_X = 0.24f;

    /** How much of the arena's height the crates spread over. */
    private static final float CACHE_SPREAD = 0.5f;

    /** Where the wall stands, as a fraction of the arena's width. */
    private static final float WALL_X = 0.62f;

    /** How many tile rows the doorway is. Wide enough that a brute fits through it. */
    private static final int DOORWAY_ROWS = 4;

    /** Where things sit across the arena, as fractions of its width. */
    private static final float LOOSE_BARREL_X = 0.4f;

    private static final float STACK_X = 0.78f;
    private static final float GRUNT_X = 0.9f;
    private static final float BRUTE_X = 0.95f;

    /** The ones that do not simply walk at you, in front of the stack rather than behind it. */
    private static final float SPITTER_X = 0.71f;

    private static final float RUNNER_X = 0.86f;

    private static final float FROG_X = 0.67f;

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
        return "Bank a grenade through the door, watch the stack cascade, and wait for the fire to go out.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return base.withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(GRUNTS))
                .withUnit(UnitKind.BRUTE, brute -> brute.withCount(BRUTES))
                .withUnit(UnitKind.SPITTER, spitter -> spitter.withCount(SPITTERS))
                .withUnit(UnitKind.RUNNER, runner -> runner.withCount(RUNNERS))
                .withUnit(UnitKind.FROG, frog -> frog.withCount(FROGS))
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

        rank(simulation, UnitKind.GRUNT, GRUNT_X);
        rank(simulation, UnitKind.BRUTE, BRUTE_X);
        // In front of the stack rather than behind it, so what they do reaches this side of the wall:
        // a spitter's cone comes out through the doorway, a runner is on you before the throw is
        // lined up, and what a frog leaves is on the ground you were going to stand on.
        rank(simulation, UnitKind.SPITTER, SPITTER_X);
        rank(simulation, UnitKind.RUNNER, RUNNER_X);
        rank(simulation, UnitKind.FROG, FROG_X);

        caches(simulation, width, height);
    }

    /**
     * Two crates of every gun, in two columns on the near side of the wall.
     *
     * <p>Before the wall rather than past it, because the point is to answer the <em>same</em> stack
     * seven ways: walking to a crate must not be a fight in itself, or what is being compared is the
     * walk. Two of each because a gun is lost with a death and standing next to a barrel is how this
     * arena teaches its radius.
     */
    private static void caches(Simulation simulation, float width, float height) {
        for (int i = 0; i < CACHES.length; i++) {
            float y = height * spread(0.5f - CACHE_SPREAD * 0.5f, 0.5f + CACHE_SPREAD * 0.5f, i, CACHES.length);
            simulation.spawnPickup(CACHES[i], width * NEAR_CACHE_X, y);
            simulation.spawnPickup(CACHES[i], width * FAR_CACHE_X, y);
        }
    }

    /** One strain's whole count, spread up the arena at the same distance across it. */
    private static void rank(Simulation simulation, UnitKind kind, float acrossTheArena) {
        float width = simulation.map().worldWidth();
        float height = simulation.map().worldHeight();
        int count = simulation.config().unit(kind).count();
        for (int i = 0; i < count; i++) {
            simulation.spawnUnit(
                    kind,
                    width * acrossTheArena,
                    height * spread(0.5f - SPREAD * 0.5f, 0.5f + SPREAD * 0.5f, i, count));
        }
    }

    /** Evenly spaces {@code count} things between two fractions, both ends included. */
    private static float spread(float from, float to, int index, int count) {
        return count <= 1 ? (from + to) * 0.5f : from + (to - from) * index / (count - 1);
    }
}
