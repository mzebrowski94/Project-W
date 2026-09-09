package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A near-empty arena and a handful of things that jump at you, half of them too heavy to punch down.
 *
 * <p>Built to make the leap and the hand strike readable rather than to be fair. There is almost no
 * cover, so nothing interrupts a leap except the player, and the two enemy kinds are mixed on purpose:
 * an enemy dies to one swing and a brute is only knocked back, so the difference between "answered
 * it" and "bought a second" is visible in the same fight.
 *
 * <p>What to look at: the moment a unit stops dead before it jumps, and whether a swing timed to that
 * moment lands.
 */
final class DuelScenario implements Scenario {

    /** Gap left between anything placed and anything already there, in world units. */
    private static final float CLEARANCE = 2f;

    /** Far enough that the first leap is something the player sees coming rather than wakes up inside. */
    private static final float STANDOFF = 26f;

    private static final int ENEMIES = 5;
    private static final int BRUTES = 3;
    private static final int OBSTACLES = 2;

    @Override
    public String name() {
        return "Duel";
    }

    @Override
    public String description() {
        return "Leaping enemies and brutes: watch for the wind-up, right-click to answer it.";
    }

    @Override
    public SimConfig configure(SimConfig config) {
        return config.withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(ENEMIES))
                .withUnit(UnitKind.BRUTE, brute -> brute.withCount(BRUTES))
                .withObstacle(config.obstacle().withCount(OBSTACLES));
    }

    @Override
    public void populate(Simulation simulation) {
        SimConfig config = simulation.config();
        TileMap map = simulation.map();
        float centreX = map.worldWidth() * 0.5f;
        float centreY = map.worldHeight() * 0.5f;

        simulation.spawnCommander(centreX, centreY);

        float wallMargin = config.commander().radius() * 2f + CLEARANCE;
        Scatter scatter = new Scatter(map, simulation.rng(), CLEARANCE, wallMargin);
        scatter.reserve(centreX, centreY, config.commander().radius() + STANDOFF);

        scatter.scatter(config.obstacle().count(), config.obstacle().radius(), simulation::spawnObstacle);
        spawn(simulation, scatter, UnitKind.GRUNT);
        spawn(simulation, scatter, UnitKind.BRUTE);
    }

    /** Scatters a strain's configured count wherever there is room for one. */
    private static void spawn(Simulation simulation, Scatter scatter, UnitKind kind) {
        UnitDef def = simulation.config().unit(kind);
        scatter.scatter(def.count(), def.radius(), (x, y) -> simulation.spawnUnit(kind, x, y));
    }
}
