package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Ground you are told to hold, and six waves of things walking at it.
 *
 * <p>The first level that tells the <em>aliens</em> where to be: everything that has not noticed
 * anybody walks at the post, so the fighting comes to you rather than being found. What to look at,
 * roughly in the order you will meet it:
 *
 * <ul>
 *   <li>You start on the post with your squad and two guns already there. Press <kbd>E</kbd> beside
 *       one to get on it; press it again to get off. While you are on it you cannot be touched -
 *       everything that reaches you has to kill the gun first - and the gun fires where <em>it</em>
 *       is pointing rather than where you are, slower while you hold the trigger.
 *   <li>Watch the arc over the gun fill as you fire. Empty it and it stops, and it does not come back
 *       until it has cooled well down. Getting off and on again does not help: the heat belongs to
 *       the gun. The railgun in the cache behind the post runs hot in your hands the same way.
 *   <li><strong>Every lot of grunts has runners either side of it</strong>, from another gate and a
 *       few seconds apart: smaller, much faster, and they weave the whole way in. Lead them, or waste
 *       the bar. The slow half arrives while you are still dealing with them, which is the level.
 *   <li>Leave a trooper near a free gun with something in its reach and it will get on it - and get
 *       off again once there has been nothing to shoot at for a few seconds.
 *   <li>The aliens no longer come only for you. Stand behind your men and watch what happens to
 *       them; a gun standing empty is a target too, and a wave will stop and take one apart.
 *   <li>Press <kbd>2</kbd>. On this level "forward" means away from the post - the squad pushes the
 *       line out into the approach rather than walking to a square.
 *   <li>Barrels are laid along the approach and beside the post. Shooting one as a wave reaches it is
 *       most of what a defence has instead of a big gun; standing next to one when it goes is the
 *       rest of it.
 *   <li>The console says which wave has walked in as it arrives - <code>wave 3/6</code> - so an empty
 *       field is readable as a lull rather than as a win.
 *   <li>Left alone, the squad stays around the post rather than roaming off: on this level its patch
 *       is the thing you were told to hold.
 *   <li>The level is done when the last wave has been sent in and nothing hostile is standing. Until
 *       then an empty field means the next lot are on their way.
 * </ul>
 *
 * <p>Fixes its own map size, because the length of the approach <em>is</em> the scenario: the waves
 * need ground to cross while you decide which gun to be on.
 */
final class DefendScenario implements Scenario {

    private static final int COLS = 60;
    private static final int ROWS = 34;
    private static final float TILE = 5f;
    private static final int REGIONS = 8;

    /** The post, and the middle of everything. The squad starts around it. */
    private static final int POST_COL = 13;

    private static final int POST_ROW = 17;

    /** Where each wave is put down: three mouths at the far end, so they do not all arrive together. */
    private static final TileRect EAST_GATE = new TileRect(54, 15, 4, 5);

    private static final TileRect NORTH_GATE = new TileRect(50, 3, 5, 4);

    private static final TileRect SOUTH_GATE = new TileRect(50, 27, 5, 4);

    @Override
    public String name() {
        return "Defend";
    }

    @Override
    public String description() {
        return "Hold the post: man a gun, mind the heat, and see off six waves that come to you.";
    }

    /**
     * Detection on for everything, a squad worth having, and the spawn area on top of the post.
     *
     * <p>Always-aggressive is the one switch that has to be off: a level about things walking at the
     * post is a level where nothing may set off at the commander from the far end on the first tick.
     */
    @Override
    public SimConfig configure(SimConfig base) {
        SimConfig config = base.withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
        for (UnitKind kind : UnitKind.values()) {
            config = config.withUnit(
                    kind, strain -> strain.withPerception(strain.perception().withAlwaysAggressive(false)));
        }
        return config.withTrooper(config.trooper().withSquadSize(8))
                .withSpawn(new SimConfig.SpawnConfig(POST_COL - 3, 4, 7));
    }

    /**
     * Two long walls with their gaps at opposite ends, so a wave has to commit to a side.
     *
     * <p>The approach is deliberately not a corridor: the post has to be defensible from two
     * directions at once, or there is one place to stand and no decision to make.
     */
    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        Maps.partitionRegions(builder, REGIONS, randomness);
        return builder.border(1, Terrain.ROCK)
                .rect(30, 1, 1, 13, Terrain.ROCK)
                .rect(30, 21, 1, 12, Terrain.ROCK)
                .rect(44, 8, 1, 18, Terrain.ROCK)
                .rect(22, 9, 3, 3, Terrain.ROCK)
                .rect(22, 22, 3, 3, Terrain.ROCK)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();

        // The post first: the renderer draws in spawn order, and it is ground rather than a thing
        // standing on ground.
        simulation.spawnPost(map.tileCentreX(POST_COL), map.tileCentreY(POST_ROW));

        deployPlayerSide(simulation);

        // Two guns covering the approach, far enough apart that one man cannot work both.
        simulation.spawnTurret(map.tileCentreX(POST_COL + 4), map.tileCentreY(POST_ROW - 5));
        simulation.spawnTurret(map.tileCentreX(POST_COL + 4), map.tileCentreY(POST_ROW + 5));
        simulation.spawnTurret(map.tileCentreX(POST_COL + 12), map.tileCentreY(POST_ROW));

        // A better gun behind the post, and two men to go and fetch out of the approach.
        simulation.spawnPickup(PickupKind.ARMOURY_RAILGUN, map.tileCentreX(POST_COL - 4), map.tileCentreY(POST_ROW));
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, map.tileCentreX(26), map.tileCentreY(6));
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, map.tileCentreX(26), map.tileCentreY(28));

        // Along the approach, and two within reach of the post for when it gets that far.
        simulation.spawnBarrel(map.tileCentreX(POST_COL + 7), map.tileCentreY(POST_ROW - 2));
        simulation.spawnBarrel(map.tileCentreX(POST_COL + 7), map.tileCentreY(POST_ROW + 2));
        simulation.spawnBarrel(map.tileCentreX(33), map.tileCentreY(17));
        simulation.spawnBarrel(map.tileCentreX(36), map.tileCentreY(9));
        simulation.spawnBarrel(map.tileCentreX(36), map.tileCentreY(25));
        simulation.spawnBarrel(map.tileCentreX(47), map.tileCentreY(17));

        // Food out in the approach, so what walks past it comes on bigger than it set off.
        simulation.spawnPickup(PickupKind.MUSHROOM, map.tileCentreX(38), map.tileCentreY(14));
        simulation.spawnPickup(PickupKind.MUSHROOM, map.tileCentreX(38), map.tileCentreY(20));
        simulation.spawnPickup(PickupKind.FLOWER, map.tileCentreX(50), map.tileCentreY(17));
        simulation.spawnRabbit(map.tileCentreX(20), map.tileCentreY(14));
        simulation.spawnRabbit(map.tileCentreX(20), map.tileCentreY(20));

        // What is coming, and when. The gaps are the level: the ground in front of you empties, and
        // then it does not.
        //
        // Runners lead or follow every lot of them, from a different gate, a few seconds apart - which
        // is what the strain is for. They arrive first because they are fast, they weave the whole way
        // in, and they pull the squad off the line the slow half is walking up. A hundred of them
        // altogether; this is meant to be lost a few times before it is held.
        simulation
                .waves()
                .add(8f, UnitKind.GRUNT, 10, EAST_GATE)
                .add(4f, UnitKind.RUNNER, 5, NORTH_GATE)
                .add(8f, UnitKind.GRUNT, 10, SOUTH_GATE)
                .add(4f, UnitKind.RUNNER, 7, NORTH_GATE)
                .add(9f, UnitKind.SPITTER, 4, EAST_GATE)
                .add(4f, UnitKind.RUNNER, 8, SOUTH_GATE)
                .add(9f, UnitKind.GRUNT, 12, EAST_GATE)
                .add(4f, UnitKind.RUNNER, 8, NORTH_GATE)
                .add(9f, UnitKind.BRUTE, 4, EAST_GATE)
                .add(4f, UnitKind.RUNNER, 10, SOUTH_GATE)
                .add(9f, UnitKind.GRUNT, 12, NORTH_GATE)
                .add(4f, UnitKind.RUNNER, 10, SOUTH_GATE)
                .add(8f, UnitKind.BRUTE, 5, EAST_GATE)
                .add(3f, UnitKind.RUNNER, 12, EAST_GATE);

        // One objective, and it names both halves: every wave sent, and nothing left standing.
        Level level = simulation.level();
        level.require(Objective.HOLD_POINT);
    }
}
