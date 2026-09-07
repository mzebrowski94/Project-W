package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;

/**
 * The default arena: a commander in the middle, obstacles scattered around, enemies further out.
 *
 * <p>Exercises everything at once - movement, aiming, firing, chasing, separation and arena bounds -
 * which makes it the scenario to open when checking that nothing is obviously broken, and the wrong
 * one for isolating a single system.
 */
final class SkirmishScenario implements Scenario {

    /** Gap left between anything placed and anything already there, in world units. */
    private static final float CLEARANCE = 1.5f;

    /** How far from the commander an enemy must start, so the scenario does not open mid-collision. */
    private static final float ENEMY_STANDOFF = 18f;

    @Override
    public String name() {
        return "Skirmish";
    }

    @Override
    public String description() {
        return "Everything at once: move, aim, fire, get chased, bump into things.";
    }

    @Override
    public void populate(Simulation simulation) {
        SimConfig config = simulation.config();
        SimConfig.ArenaConfig arena = config.arena();
        float centreX = arena.width() * 0.5f;
        float centreY = arena.height() * 0.5f;

        simulation.spawnCommander(centreX, centreY);

        float wallMargin = config.commander().radius() * 2f + CLEARANCE;
        Placement placement = new Placement(simulation.rng(), arena, CLEARANCE, wallMargin);
        placement.reserve(centreX, centreY, config.commander().radius() + ENEMY_STANDOFF);

        for (int i = 0; i < config.obstacle().count(); i++) {
            Placement.Spot spot = placement.find(config.obstacle().radius());
            if (spot != null) {
                simulation.spawnObstacle(spot.x(), spot.y());
            }
        }
        for (int i = 0; i < config.enemy().count(); i++) {
            Placement.Spot spot = placement.find(config.enemy().radius());
            if (spot != null) {
                simulation.spawnEnemy(spot.x(), spot.y());
            }
        }
    }
}
