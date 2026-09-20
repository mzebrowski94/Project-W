package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.UnitKind;

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
        return base.withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(ENEMIES))
                .withObstacle(base.obstacle().withCount(0));
    }

    @Override
    public void populate(Simulation simulation) {
        SimConfig config = simulation.config();
        TileMap map = simulation.map();
        float centreX = map.worldWidth() * 0.5f;
        float centreY = map.worldHeight() * 0.5f;

        simulation.spawnCommander(centreX, centreY);

        // On top of each other, yes - that is the whole scenario - but not inside the wall. The ring
        // is placed a fixed fraction of the way towards the southern edge, and on a map no taller
        // than the one the game ships that fraction plus the ring's own width reaches past it: a
        // grunt's body ended up partly outside the arena, which the solver then shoved back in on
        // the first tick. Overlapping is what this is for; starting in rock is a different thing,
        // and it is an enforced invariant.
        float margin = RING_RADIUS + config.unit(UnitKind.GRUNT).radius();
        float ringY = Math.max(margin, centreY - map.worldHeight() * 0.35f);
        for (int i = 0; i < config.unit(UnitKind.GRUNT).count(); i++) {
            float x = centreX + simulation.rng().nextFloat(-RING_RADIUS, RING_RADIUS);
            float y = ringY + simulation.rng().nextFloat(-RING_RADIUS, RING_RADIUS);
            simulation.spawnEnemy(x, y);
        }
    }
}
