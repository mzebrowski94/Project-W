package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
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
 *   <li><strong>The grunts.</strong> Four of them, behind the line, so that standing still to work on
 *       a man is a thing that can be punished. They kill outright - claws always have - which is the
 *       contrast the whole change is built on: what shoots you can be survived, what reaches you
 *       cannot.
 *   <li><strong>The barrel.</strong> Beside the start, and draggable. Haul it up the field and shoot
 *       it, or leave it; either way it shows that hauling is about a load rather than about a man.
 * </ul>
 *
 * <p>Worth watching for: a man shot down twice in a row. The first hit puts him on the ground and the
 * second finishes him, because he is already at zero - so a casualty left lying in the line of fire
 * is not merely on a clock, he is a target. That is the rule that makes dragging worth its cost.
 */
final class CasualtyScenario implements Scenario {

    private static final int SPITTERS = 5;
    private static final int GRUNTS = 4;

    /** Where the line stands and where the grunts wait behind it, as fractions of the arena's width. */
    private static final float LINE_X = 0.72f;

    private static final float GRUNT_X = 0.88f;

    /** The two stones that are the only cover on the field. */
    private static final float COVER_X = 0.34f;

    private static final float COVER_TOP = 0.3f;
    private static final float COVER_BOTTOM = 0.7f;

    /** How many tiles across each stone is. Big enough to get a man behind, small enough to flank. */
    private static final int COVER_TILES = 2;

    /** The barrel sits beside where the squad starts, within reach of a first drag. */
    private static final float BARREL_X = 0.18f;

    /** How much of the arena's height the line spreads over. */
    private static final float SPREAD = 0.7f;

    @Override
    public String name() {
        return "Casualty";
    }

    @Override
    public String description() {
        return "A spitter line across open ground. Men go down rather than dying - E to stabilise, F to haul them out.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return base.withUnit(UnitKind.SPITTER, spitter -> spitter.withCount(SPITTERS))
                .withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(GRUNTS))
                .withUnit(UnitKind.BRUTE, brute -> brute.withCount(0))
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

        deployPlayerSide(simulation);

        simulation.spawnBarrel(width * BARREL_X, height * 0.5f);

        place(simulation, UnitKind.SPITTER, width * LINE_X, height, SPITTERS);
        place(simulation, UnitKind.GRUNT, width * GRUNT_X, height, GRUNTS);
    }

    private static void place(Simulation simulation, UnitKind kind, float x, float height, int count) {
        for (int i = 0; i < count; i++) {
            simulation.spawnUnit(kind, x, height * spread(0.5f - SPREAD * 0.5f, 0.5f + SPREAD * 0.5f, i, count));
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
