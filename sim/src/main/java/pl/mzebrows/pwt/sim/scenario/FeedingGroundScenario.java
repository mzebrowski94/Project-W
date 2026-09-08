package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A field of frozen troopers between the commander and a handful of grunts walking towards him.
 *
 * <p>Built to make evolution visible rather than to be fair. The troopers are laid out in a band
 * across the middle precisely so that a grunt closing on the commander walks over them: each one it
 * eats brightens the core inside it, and the third turns it into a brute in front of you. Reach a
 * trooper first and it is a reinforcement instead - the same object, read differently by each side.
 *
 * <p>What to look at: the mark growing inside a grunt as it feeds, the moment one changes shape and
 * colour mid-walk, and whether shooting the fed ones first is worth more than racing them for the
 * troopers. The flowers on the flanks are the shortcut - one of those turns a grunt at once, with no
 * warning at all. The armoury by the commander is the answer to what that produces.
 */
final class FeedingGroundScenario implements Scenario {

    private static final int GRUNTS = 5;
    private static final int TROOPER_ROWS = 3;
    private static final int TROOPER_COLUMNS = 4;

    /** Where things sit across the arena, as fractions of its width. */
    private static final float COMMANDER_X = 0.16f;

    private static final float ARMOURY_X = 0.28f;
    private static final float BAND_START_X = 0.44f;
    private static final float BAND_END_X = 0.66f;
    private static final float FLOWER_X = 0.72f;
    private static final float GRUNT_X = 0.92f;

    /** How much of the arena's height the band and the grunt line spread over. */
    private static final float SPREAD = 0.7f;

    @Override
    public String name() {
        return "Feeding Ground";
    }

    @Override
    public String description() {
        return "Grunts eat their way to you through frozen troopers. Watch one fill up and turn.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return base.withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(GRUNTS))
                .withUnit(UnitKind.BRUTE, brute -> brute.withCount(0))
                .withObstacle(base.obstacle().withCount(0));
    }

    @Override
    public void populate(Simulation simulation) {
        SimConfig.ArenaConfig arena = simulation.config().arena();
        float width = arena.width();
        float height = arena.height();

        simulation.spawnCommander(width * COMMANDER_X, height * 0.5f);
        simulation.spawnPickup(PickupKind.ARMOURY, width * ARMOURY_X, height * 0.5f);

        for (int row = 0; row < TROOPER_ROWS; row++) {
            for (int column = 0; column < TROOPER_COLUMNS; column++) {
                simulation.spawnPickup(
                        PickupKind.FROZEN_TROOPER,
                        width * spread(BAND_START_X, BAND_END_X, column, TROOPER_COLUMNS),
                        height * spread(0.5f - SPREAD * 0.5f, 0.5f + SPREAD * 0.5f, row, TROOPER_ROWS));
            }
        }

        simulation.spawnPickup(PickupKind.FLOWER, width * FLOWER_X, height * (0.5f - SPREAD * 0.5f));
        simulation.spawnPickup(PickupKind.FLOWER, width * FLOWER_X, height * (0.5f + SPREAD * 0.5f));

        for (int i = 0; i < simulation.config().unit(UnitKind.GRUNT).count(); i++) {
            simulation.spawnUnit(
                    UnitKind.GRUNT,
                    width * GRUNT_X,
                    height * spread(0.5f - SPREAD * 0.5f, 0.5f + SPREAD * 0.5f, i, GRUNTS));
        }
    }

    /** Evenly spaces {@code count} things between two fractions, both ends included. */
    private static float spread(float from, float to, int index, int count) {
        return count <= 1 ? (from + to) * 0.5f : from + (to - from) * index / (count - 1);
    }
}
