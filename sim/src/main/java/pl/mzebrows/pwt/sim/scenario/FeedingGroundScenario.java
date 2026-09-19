package pl.mzebrows.pwt.sim.scenario;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.EnumSet;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A field of growth between the commander and a handful of grunts walking towards him.
 *
 * <p>Built to make evolution visible rather than to be fair. The food is laid out in a band across
 * the middle precisely so that a grunt closing on the commander walks over it: each mouthful it eats
 * brightens the core inside it, and the third turns it into a brute in front of you.
 *
 * <p>The band is <em>organic ground</em>, not just a row of objects: the mushrooms growing through it
 * are scattered on that terrain and nowhere else, which is the whole of what terrain-gated spawning
 * means. Walk to the edge of the green and the food stops.
 *
 * <p>What to look at: the mark growing inside a grunt as it feeds, the moment one changes shape and
 * colour mid-walk, and whether shooting the fed ones first is worth more than denying them the band.
 * The flowers on the flanks are the shortcut - one of those turns a grunt at once, with no warning at
 * all. The armoury by the commander is the answer to what that produces.
 */
final class FeedingGroundScenario implements Scenario {

    private static final int GRUNTS = 5;

    /** The planted band, as a grid: enough mouthfuls on a grunt's line to turn it before it arrives. */
    private static final int BAND_ROWS = 3;

    private static final int BAND_COLUMNS = 4;

    /** Where things sit across the arena, as fractions of its width. */
    private static final float COMMANDER_X = 0.16f;

    private static final float ARMOURY_X = 0.28f;
    private static final float BAND_START_X = 0.44f;
    private static final float BAND_END_X = 0.66f;
    private static final float FLOWER_X = 0.72f;
    private static final float GRUNT_X = 0.92f;

    /** How much of the arena's height the band and the grunt line spread over. */
    private static final float SPREAD = 0.7f;

    /** Growth on the organic band. Free food for anything that reaches it. */
    private static final int MUSHROOMS = 14;

    private static final float CLEARANCE = 2f;

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        SimConfig.MapConfig shape = config.map();
        TileMap.Builder builder = TileMap.builder(shape.cols(), shape.rows(), shape.tileSize());
        Maps.partitionRegions(builder, shape.regions(), randomness);

        int from = (int) (shape.cols() * BAND_START_X) - 1;
        int width = (int) (shape.cols() * (FLOWER_X - BAND_START_X)) + 3;
        return builder.rect(from, 0, width, shape.rows(), Terrain.ORGANIC).build();
    }

    @Override
    public String name() {
        return "Feeding Ground";
    }

    @Override
    public String description() {
        return "Grunts eat their way to you through a band of growth. Watch one fill up and turn.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return base.withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(GRUNTS))
                .withUnit(UnitKind.BRUTE, brute -> brute.withCount(0))
                .withObstacle(base.obstacle().withCount(0));
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        float width = map.worldWidth();
        float height = map.worldHeight();

        simulation.spawnCommander(width * COMMANDER_X, height * 0.5f);
        simulation.spawnPickup(PickupKind.ARMOURY_RAILGUN, width * ARMOURY_X, height * 0.5f);

        for (int row = 0; row < BAND_ROWS; row++) {
            for (int column = 0; column < BAND_COLUMNS; column++) {
                simulation.spawnPickup(
                        PickupKind.MUSHROOM,
                        width * spread(BAND_START_X, BAND_END_X, column, BAND_COLUMNS),
                        height * spread(0.5f - SPREAD * 0.5f, 0.5f + SPREAD * 0.5f, row, BAND_ROWS));
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

        growMushrooms(simulation, map);
    }

    /**
     * Mushrooms grow on organic ground and nowhere else. Everything already standing is reserved
     * first, so nothing sprouts underneath the planted band or on top of the commander.
     */
    private static void growMushrooms(Simulation simulation, TileMap map) {
        float radius = simulation.config().pickup(PickupKind.MUSHROOM).radius();
        Scatter scatter = new Scatter(map, simulation.rng(), CLEARANCE, radius * 2f);
        for (Entity standing : simulation
                .engine()
                .getEntitiesFor(Family.all(Position.class, Body.class).get())) {
            Position position = standing.getComponent(Position.class);
            scatter.reserve(position.x, position.y, standing.getComponent(Body.class).radius);
        }
        scatter.scatter(
                MUSHROOMS,
                radius,
                EnumSet.of(Terrain.ORGANIC),
                (x, y) -> simulation.spawnPickup(PickupKind.MUSHROOM, x, y));
    }

    /** Evenly spaces {@code count} things between two fractions, both ends included. */
    private static float spread(float from, float to, int index, int count) {
        return count <= 1 ? (from + to) * 0.5f : from + (to - from) * index / (count - 1);
    }
}
