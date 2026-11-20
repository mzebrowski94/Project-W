package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Every slot a game holds taken at once: a commander for every slot the world holds - eight in a game -, each with a squad, round the
 * middle of open ground, and a thin ring of enemies well out.
 *
 * <p>Built for the interface, not the fight. Offline only the keyboard and any pads plugged in drive a
 * commander; the rest stand where they were put. What to look at: the bottom bar falling back to this
 * machine's seats, the roll call holding all eight, the eight colours side by side, and the top bar's
 * player tallies.
 */
final class FullHouseScenario implements Scenario {

    /** Gap left between anything placed and anything already there, in world units. */
    private static final float CLEARANCE = 1.5f;

    /** How far from the middle the commanders stand: close enough to share the opening view. */
    private static final float RING = 10f;

    /** Room round one commander his squad is scattered in, in world units. */
    private static final float SQUAD_REACH = 4f;

    /** How far from the middle an enemy must start, so the opening is the HUD rather than a fight. */
    private static final float ENEMY_STANDOFF = 30f;

    /**
     * Where the other commanders stand round slot 0, as the eight cells round the middle of a
     * three-by-three grid; seven are taken in a game of eight, and more slots than that double up.
     */
    private static final float[][] AROUND = {
        {-1f, 1f}, {0f, 1f}, {1f, 1f}, {-1f, 0f}, {1f, 0f}, {-1f, -1f}, {0f, -1f}, {1f, -1f}
    };

    @Override
    public String name() {
        return "Full House";
    }

    @Override
    public String description() {
        return "Eight commanders and squads at once: watch the panels, the roll call and the colours.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return Scenario.withoutMuster(base);
    }

    @Override
    public void populate(Simulation simulation) {
        SimConfig config = simulation.config();
        TileMap map = simulation.map();
        float centreX = map.worldWidth() * 0.5f;
        float centreY = map.worldHeight() * 0.5f;
        float trooperRadius = config.trooper().radius();

        // Slot 0 first and in the middle: the keyboard player's, and the one every scenario has.
        simulation.spawnCommander(centreX, centreY, 0);
        int slots = simulation.capacity();
        float[] xs = new float[slots];
        float[] ys = new float[slots];
        xs[0] = centreX;
        ys[0] = centreY;
        for (int slot = 1; slot < slots; slot++) {
            int cell = (slot - 1) % AROUND.length;
            xs[slot] = centreX + RING * AROUND[cell][0];
            ys[slot] = centreY + RING * AROUND[cell][1];
            simulation.spawnCommander(xs[slot], ys[slot], slot);
        }

        Scatter scatter =
                new Scatter(map, simulation.rng(), CLEARANCE, config.commander().radius() * 2f + CLEARANCE);
        for (int slot = 0; slot < slots; slot++) {
            scatter.reserve(xs[slot], ys[slot], config.commander().radius());
        }
        for (int slot = 0; slot < slots; slot++) {
            scatter.scatter(
                    TileRect.around(map, xs[slot], ys[slot], SQUAD_REACH),
                    config.trooper().squadSize(),
                    trooperRadius,
                    simulation::spawnTrooper);
        }

        scatter.reserve(centreX, centreY, RING + ENEMY_STANDOFF);
        UnitDef grunt = config.unit(UnitKind.GRUNT);
        scatter.scatter(grunt.count(), grunt.radius(), (x, y) -> simulation.spawnUnit(UnitKind.GRUNT, x, y));
    }
}
