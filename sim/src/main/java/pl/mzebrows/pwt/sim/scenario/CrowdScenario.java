package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;

/**
 * Sixty enemies spawned deliberately on top of each other, with no obstacles.
 *
 * <p>This one exists to watch the constraint solver work. The crowd starts overlapping and untangles
 * itself over the first few ticks; then it converges on the commander, which is where separation is
 * under the most pressure. If units ever jitter, tunnel through each other, or squeeze into the
 * arena edge, it shows here first.
 */
final class CrowdScenario implements Scenario {

    private static final int ENEMIES = 60;
    private static final float RING_RADIUS = 12f;

    @Override
    public String name() {
        return "Crowd";
    }

    @Override
    public String description() {
        return "Sixty enemies spawned overlapping. Watch them untangle, then converge.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return base.with(base.enemy().withCount(ENEMIES)).with(base.obstacle().withCount(0));
    }

    @Override
    public void populate(Simulation simulation) {
        SimConfig config = simulation.config();
        SimConfig.ArenaConfig arena = config.arena();
        float centreX = arena.width() * 0.5f;
        float centreY = arena.height() * 0.5f;

        simulation.spawnCommander(centreX, centreY);

        for (int i = 0; i < config.enemy().count(); i++) {
            float x = centreX + simulation.rng().nextFloat(-RING_RADIUS, RING_RADIUS);
            float y = centreY - arena.height() * 0.35f + simulation.rng().nextFloat(-RING_RADIUS, RING_RADIUS);
            simulation.spawnEnemy(x, y);
        }
    }
}
