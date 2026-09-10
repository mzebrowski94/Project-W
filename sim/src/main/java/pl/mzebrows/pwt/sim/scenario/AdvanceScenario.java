package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A squad, four points to take, and a level that wants the map cleared as well.
 *
 * <p>The arena for the squad: everything a trooper does is visible in it, and so is everything you
 * can do about it. What to look at, roughly in the order you will meet it:
 *
 * <ul>
 *   <li>You start on the left with your men around you, jittered inside the spawn area rather than
 *       lined up on it. Everything has a spoke showing which way it is looking - yours follows your
 *       aim and never your feet, theirs follows their attention.
 *   <li>Left alone, the squad advances on the nearest point and sweeps its arcs on the way. That
 *       weave is <strong>urgency</strong>, the number that sets the game's pace.
 *   <li>The wall is long and its only way round is at the top, so the squad walks the long way rather
 *       than at the rock. That is route distance rather than straight-line distance.
 *   <li>Stand on a point and it fills up. A trooper that reaches one <em>stops where it is</em> and
 *       watches outward instead of shuffling to the middle - everyone inside the radius counts the
 *       same. Step off before it completes and it resets to nothing rather than pausing.
 *   <li>Taking a point is loud, and now carries a long way. Expect company.
 *   <li>Shoot a grunt from range and watch it come at <em>where you shot from</em>, not at wherever
 *       the bullet hit it.
 *   <li>Troopers engage only inside their arc and through open ground, and stand still to shoot. Put
 *       one behind a wall from a grunt and it holds fire.
 *   <li>Press <kbd>1</kbd>, <kbd>2</kbd>, <kbd>3</kbd>, <kbd>4</kbd>. Everything in earshot takes a
 *       ring, and the overlay shows what you last said. Men who stop each take a bearing of their
 *       own, so a halted squad watches outwards rather than all staring at you.
 *   <li><strong>The last two points are a deliberate tie.</strong> They sit the same number of tiles
 *       from the middle point, by road as well as by line. A trooper picks the shorter walk and
 *       breaks a tie towards whichever point was spawned first - but each man measures from where
 *       <em>he</em> is standing, and a squad spread across a point is not all standing in one place.
 *       Expect it to split, and decide whether that is what you want; nothing coordinates them.
 *   <li>Both objectives are named, so taking the points is not enough while anything is still alive.
 *       Nothing happens when you meet them: the level knows and, in the alpha, does nothing.
 * </ul>
 *
 * <p>Fixes its own map size, because the wall and the symmetry of the last two points <em>are</em>
 * the scenario.
 */
final class AdvanceScenario implements Scenario {

    private static final int COLS = 44;
    private static final int ROWS = 26;
    private static final float TILE = 5f;
    private static final int REGIONS = 6;

    /** The long wall. Its only way round is above it, which is what makes the first walk a decision. */
    private static final int WALL_COL = 12;

    private static final int WALL_ROW = 1;

    private static final int WALL_HEIGHT = 18;

    /** The middle point, and the two the tie is measured from. */
    private static final int MIDDLE_COL = 27;

    private static final int MIDDLE_ROW = 13;

    private static final int FAR_COL = 38;

    private static final int NORTH_ROW = 6;

    private static final int SOUTH_ROW = 20;

    @Override
    public String name() {
        return "Advance";
    }

    @Override
    public String description() {
        return "A squad, four points to take, and two of them exactly as far away as each other.";
    }

    /**
     * Detection on for everything, and a spawn area wide enough for a squad: a scenario about
     * troopers noticing things is not one to run with every alien awake from the first tick.
     */
    @Override
    public SimConfig configure(SimConfig base) {
        SimConfig config = base.withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
        for (UnitKind kind : UnitKind.values()) {
            config = config.withUnit(
                    kind, strain -> strain.withPerception(strain.perception().withAlwaysAggressive(false)));
        }
        return config.withTrooper(config.trooper().withSquadSize(4)).withSpawn(new SimConfig.SpawnConfig(2, 3, 6));
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        Maps.partitionRegions(builder, REGIONS, randomness);
        return builder.border(1, Terrain.ROCK)
                .rect(WALL_COL, WALL_ROW, 1, WALL_HEIGHT, Terrain.ROCK)
                // Centred on the middle point's row, so it costs the two far points the same detour
                // as each other and the tie stays a tie.
                .rect(32, MIDDLE_ROW - 1, 1, 3, Terrain.ROCK)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();

        // Points first: the renderer draws in spawn order, and a point is ground to stand on rather
        // than a thing standing on the ground. Spawn order is also the tie-break, so the north point
        // is deliberately the earlier of the two.
        simulation.spawnControlPoint(map.tileCentreX(17), map.tileCentreY(NORTH_ROW));
        simulation.spawnControlPoint(map.tileCentreX(MIDDLE_COL), map.tileCentreY(MIDDLE_ROW));
        simulation.spawnControlPoint(map.tileCentreX(FAR_COL), map.tileCentreY(NORTH_ROW));
        simulation.spawnControlPoint(map.tileCentreX(FAR_COL), map.tileCentreY(SOUTH_ROW));

        deployPlayerSide(simulation);

        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(15), map.tileCentreY(10));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(16), map.tileCentreY(4));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(19), map.tileCentreY(8));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(18), map.tileCentreY(21));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(21), map.tileCentreY(16));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(24), map.tileCentreY(9));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(25), map.tileCentreY(22));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(29), map.tileCentreY(4));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(30), map.tileCentreY(18));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(34), map.tileCentreY(9));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(35), map.tileCentreY(16));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(40), map.tileCentreY(13));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(40), map.tileCentreY(3));
        simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(40), map.tileCentreY(23));
        simulation.spawnUnit(UnitKind.BRUTE, map.tileCentreX(20), map.tileCentreY(12));
        simulation.spawnUnit(UnitKind.BRUTE, map.tileCentreX(31), map.tileCentreY(11));
        simulation.spawnUnit(UnitKind.BRUTE, map.tileCentreX(37), map.tileCentreY(NORTH_ROW - 3));
        simulation.spawnUnit(UnitKind.BRUTE, map.tileCentreX(37), map.tileCentreY(SOUTH_ROW + 3));

        // Rabbits by the squad, where you can watch one dodge your own shots, and rabbits out among
        // the grunts, where leaving them alone turns a grunt into a brute.
        simulation.spawnRabbit(map.tileCentreX(6), map.tileCentreY(11));
        simulation.spawnRabbit(map.tileCentreX(7), map.tileCentreY(16));
        simulation.spawnRabbit(map.tileCentreX(9), map.tileCentreY(7));
        simulation.spawnRabbit(map.tileCentreX(18), map.tileCentreY(6));
        simulation.spawnRabbit(map.tileCentreX(23), map.tileCentreY(19));
        simulation.spawnRabbit(map.tileCentreX(33), map.tileCentreY(13));
        simulation.spawnRabbit(map.tileCentreX(39), map.tileCentreY(9));

        // Both, so the scenario shows that a level requires the whole set it names.
        Level level = simulation.level();
        level.require(Objective.CAPTURE_POINTS);
        level.require(Objective.CLEAR_ENEMIES);
    }
}
