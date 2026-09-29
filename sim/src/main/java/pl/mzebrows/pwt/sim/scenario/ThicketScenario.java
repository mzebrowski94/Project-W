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
 * Three chambers joined by two corridors, with a few things asleep in them and rabbits between.
 *
 * <p>Exercises everything a unit does when it is <em>not</em> already fighting you. What to look at,
 * roughly in the order you will meet it:
 *
 * <ul>
 *   <li>Grunts start dimmed - that is the tell for one that has not noticed you. Walk towards one and
 *       watch it light up at its detection range, then get well clear and watch it dim again at a
 *       longer one. It does not troop back to where it started; it idles where you lost it, which is
 *       what makes leading a pack somewhere and slipping away worth doing.
 *   <li>Let one kill you and watch the whole chamber stand down while you are on the ground. The
 *       seconds after a death are a beat, not a guard of honour.
 *   <li>Wake one in the far chamber and it comes round the corridor rather than standing against the
 *       wall between you. That is the flow field; a brute takes the same route because both corridors
 *       are two tiles wide, and a one-tile crack would take grunts and refuse it.
 *   <li>The blocks are obstacles rather than rock, so nothing plans around them - they are what the
 *       local avoidance is for. Watch a pack lean off one well before it reaches it.
 *   <li>Shoot at a rabbit and it steps aside, the same way every time. Learn it and you can lead one.
 *       Miss twice quickly and the second shot lands, because it cannot answer while it is committed
 *       to the first.
 *   <li>Leave the rabbits alone and the grunts eat them. A grunt that has fed three times is a brute.
 * </ul>
 *
 * <p>Fixes its own map size rather than taking the caller's, because the layout is hand-laid and its
 * shape <em>is</em> the scenario.
 */
final class ThicketScenario implements Scenario {

    private static final int COLS = 32;
    private static final int ROWS = 18;
    private static final float TILE = 5f;
    private static final int REGIONS = 5;

    /** The two dividing walls, each with a two-tile mouth left open at one end. */
    private static final int WEST_WALL_COL = 11;

    private static final int EAST_WALL_COL = 21;

    @Override
    public String name() {
        return "Thicket";
    }

    @Override
    public String description() {
        return "Sleeping grunts, corners to be seen round, and rabbits worth denying.";
    }

    /**
     * Detection off the leash. Every other arena was tuned when a strain hunted from the first tick,
     * so the switch stays on everywhere else and comes off here.
     */
    @Override
    public SimConfig configure(SimConfig base) {
        SimConfig config = Scenario.withoutMuster(base).withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
        for (UnitKind kind : UnitKind.values()) {
            config = config.withUnit(
                    kind, strain -> strain.withPerception(strain.perception().withAlwaysAggressive(false)));
        }
        return config;
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        Maps.partitionRegions(builder, REGIONS, randomness);
        return builder.border(1, Terrain.ROCK)
                .rect(WEST_WALL_COL, 1, 1, 12, Terrain.ROCK)
                .rect(EAST_WALL_COL, 5, 1, 12, Terrain.ROCK)
                .rect(5, 5, 5, 1, Terrain.ROCK)
                .rect(15, 10, 4, 1, Terrain.ROCK)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        simulation.spawnCommander(map.tileCentreX(3), map.tileCentreY(3));

        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(4), map.tileCentreY(9));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(8), map.tileCentreY(14));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(16), map.tileCentreY(3));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(18), map.tileCentreY(13));
        simulation.spawnUnit(UnitKind.BRUTE, map.tileCentreX(26), map.tileCentreY(3));

        simulation.spawnRabbit(map.tileCentreX(14), map.tileCentreY(7));
        simulation.spawnRabbit(map.tileCentreX(18), map.tileCentreY(4));
        simulation.spawnRabbit(map.tileCentreX(26), map.tileCentreY(10));
        simulation.spawnRabbit(map.tileCentreX(28), map.tileCentreY(14));

        simulation.spawnObstacle(map.tileCentreX(7), map.tileCentreY(11));
        simulation.spawnObstacle(map.tileCentreX(16), map.tileCentreY(6));
        simulation.spawnObstacle(map.tileCentreX(25), map.tileCentreY(7));
        simulation.spawnObstacle(map.tileCentreX(28), map.tileCentreY(11));

        simulation.spawnPickup(PickupKind.MUSHROOM, map.tileCentreX(9), map.tileCentreY(15));
        simulation.spawnPickup(PickupKind.ARMOURY_RAILGUN, map.tileCentreX(13), map.tileCentreY(15));
    }
}
