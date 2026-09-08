package pl.mzebrows.pwt.sim.scenario;

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
        SimConfig.ArenaConfig arena = config.arena();
        float centreX = arena.width() * 0.5f;
        float centreY = arena.height() * 0.5f;

        simulation.spawnCommander(centreX, centreY);

        float wallMargin = config.commander().radius() * 2f + CLEARANCE;
        Placement placement = new Placement(simulation.rng(), arena, CLEARANCE, wallMargin);
        placement.reserve(centreX, centreY, config.commander().radius() + STANDOFF);

        for (int i = 0; i < config.obstacle().count(); i++) {
            Placement.Spot spot = placement.find(config.obstacle().radius());
            if (spot != null) {
                simulation.spawnObstacle(spot.x(), spot.y());
            }
        }
        spawn(simulation, placement, UnitKind.GRUNT);
        spawn(simulation, placement, UnitKind.BRUTE);
    }

    /** Scatters a strain's configured count wherever there is room for one. */
    private static void spawn(Simulation simulation, Placement placement, UnitKind kind) {
        UnitDef def = simulation.config().unit(kind);
        for (int i = 0; i < def.count(); i++) {
            Placement.Spot spot = placement.find(def.radius());
            if (spot != null) {
                simulation.spawnUnit(kind, spot.x(), spot.y());
            }
        }
    }
}
