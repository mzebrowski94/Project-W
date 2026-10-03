package pl.mzebrows.pwt.sim.scenario;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.map.Bag;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.RngStream;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.system.upgrade.AdaptationDef;

/**
 * One arena the alpha-phase menu can launch, built to demonstrate and test a system by hand.
 *
 * <p>A class, not a data file, so it can script a situation. Content, not design: numbers are chosen
 * to make a system visible, not to balance the game.
 */
public interface Scenario {

    /** Short name, as it appears in the menu. */
    String name();

    /** One line saying what this scenario is for - which system it exercises, and what to look at. */
    String description();

    /**
     * Where it is set, for the top bar; empty says nothing and the bar shows only the mission. Whether
     * a level has a planet at all - content, generated from the seed, or dropped - is still open (plan
     * Q9), so no scenario names one yet.
     */
    default String planet() {
        return "";
    }

    /**
     * Adjusts the config this scenario runs with. The default changes nothing; override to vary one
     * thing - a crowd of enemies, an empty arena - without restating the rest.
     */
    default SimConfig configure(SimConfig base) {
        return base;
    }

    /**
     * The ground this scenario is played on. Built before the simulation exists, because the map's
     * extent is the arena and everything in the world is sized against it.
     *
     * <p>The default is open ground the size {@code config.map()} asks for, in regions.
     * {@code randomness} is map generation's own stream.
     */
    default TileMap map(SimConfig config, Randomness randomness) {
        SimConfig.MapConfig shape = config.map();
        return Maps.regions(shape.cols(), shape.rows(), shape.tileSize(), shape.regions(), randomness);
    }

    /**
     * This config with the reserve's muster switched off - for an arena built round the commander alone,
     * where a squad turning up every half minute would be a different scenario (G0055). The reserve
     * itself stays: a commander coming back still costs a man.
     */
    static SimConfig withoutMuster(SimConfig config) {
        return config.withReserve(config.reserve().withMusterSeconds(0f));
    }

    /**
     * This config with the swarm adapting (ADR 0017): one adaptation every three minutes of the level, and one
     * for every third card the players take. What ships leaves both off; the Adventure levels turn them on
     * so the schedule can be felt, and it runs dry once every adaptation is at its last rank.
     */
    static SimConfig adapting(SimConfig config) {
        float everySeconds = 180f;
        int perPlayerUpgrades = 3;
        return config.withAdaptation(new AdaptationDef(
                everySeconds, perPlayerUpgrades, config.adaptation().answerWeight()));
    }

    /** Fills an empty simulation. Spawn the commander first, so it is always present. */
    void populate(Simulation simulation);

    /**
     * Puts the player's side on the field: the commander and his squad, in the level's spawn area.
     *
     * <p><strong>Everyone is jittered inside the area's tiles</strong> (G0009), the commander first. If
     * he does not fit, he goes at its centre: every scenario has exactly one commander.
     */
    default void deployPlayerSide(Simulation simulation) {
        SimConfig config = simulation.config();
        TileMap map = simulation.map();
        TileRect area = config.spawn().on(map);
        float trooperRadius = config.trooper().radius();
        float commanderRadius = config.commander().radius();

        Scatter scatter = new Scatter(map, simulation.rng(), trooperRadius, commanderRadius);
        boolean[] placed = {false};
        scatter.scatter(area, 1, commanderRadius, (x, y) -> {
            simulation.spawnCommander(x, y);
            placed[0] = true;
        });
        if (!placed[0]) {
            simulation.spawnCommander(area.centreX(map), area.centreY(map));
        }
        scatter.scatter(area, config.trooper().squadSize(), trooperRadius, simulation::spawnTrooper);
    }

    /**
     * How many medkits this level scatters beside its control points (G0053). None unless the level asks:
     * the Adventures do.
     */
    default int medkits(SimConfig config) {
        return 0;
    }

    /**
     * Scatters this level's medkits, each beside a control point spread along the level's own order of
     * them - so they fall along the march rather than in a heap - and clear of the points themselves and
     * of every pickup already lying there. Never hand-placed: where the rock is decided by a seed, so is
     * where there is room. Called by {@code Scenarios.world} after the reinforcements.
     */
    default void deployMedkits(Simulation simulation) {
        SimConfig config = simulation.config();
        int count = medkits(config);
        ImmutableArray<Entity> points = simulation
                .engine()
                .getEntitiesFor(Family.all(ControlPoint.class, Position.class).get());
        if (count <= 0 || points.size() == 0) {
            return;
        }
        TileMap map = simulation.map();
        float radius = config.pickup(PickupKind.MEDKIT).radius();
        float reach = config.controlPoint().reinforcementRadius();
        Scatter scatter = new Scatter(map, simulation.stream(RngStream.MEDKIT), radius, radius);
        for (int i = 0; i < points.size(); i++) {
            Position at = points.get(i).getComponent(Position.class);
            scatter.reserve(at.x, at.y, config.controlPoint().radius());
        }
        ImmutableArray<Entity> lying = simulation
                .engine()
                .getEntitiesFor(Family.all(Pickup.class, Position.class).get());
        for (int i = 0; i < lying.size(); i++) {
            Position at = lying.get(i).getComponent(Position.class);
            scatter.reserve(
                    at.x,
                    at.y,
                    config.pickup(lying.get(i).getComponent(Pickup.class).kind).radius());
        }
        for (int i = 0; i < count; i++) {
            // Evenly along the points, the middle of each share: three medkits on nine points go by the
            // second, the fifth and the eighth.
            Position at =
                    points.get(((2 * i + 1) * points.size()) / (2 * count)).getComponent(Position.class);
            scatter.scatter(
                    TileRect.around(map, at.x, at.y, reach),
                    1,
                    radius,
                    (x, y) -> simulation.spawnPickup(PickupKind.MEDKIT, x, y));
        }
    }

    /**
     * Lays a handful of reinforcements beside every control point the level named.
     *
     * <p><strong>Men are where the ground you have to take is</strong> (pillar 3): just outside each
     * point's radius, jittered (G0009), a drawn number per point. Called by {@code Scenarios.world}.
     */
    default void deployReinforcements(Simulation simulation) {
        SimConfig config = simulation.config();
        TileMap map = simulation.map();
        ImmutableArray<Entity> points = simulation
                .engine()
                .getEntitiesFor(Family.all(ControlPoint.class, Position.class).get());
        if (points.size() == 0) {
            return;
        }
        float crateRadius = config.pickup(PickupKind.SUPPLY_DROP).radius();
        float reach = config.controlPoint().reinforcementRadius();
        int fewest = config.controlPoint().reinforcementsMin();
        int spread = config.controlPoint().reinforcementsMax() - fewest + 1;
        // The reinforcement stream, shared with ReinforcementSystem: one concern, one sequence.
        Rng rng = simulation.stream(RngStream.REINFORCEMENT);
        Scatter scatter = new Scatter(map, rng, crateRadius, crateRadius);
        // Every point is reserved first, so no crate lands on the next point.
        for (int i = 0; i < points.size(); i++) {
            Position at = points.get(i).getComponent(Position.class);
            scatter.reserve(at.x, at.y, config.controlPoint().radius());
        }
        // From a bag, so points differ.
        long dealt = 0L;
        for (int i = 0; i < points.size(); i++) {
            Position at = points.get(i).getComponent(Position.class);
            int extra = Bag.draw(rng, dealt, spread);
            dealt = Bag.take(dealt, extra, spread);
            scatter.scatter(
                    TileRect.around(map, at.x, at.y, reach),
                    fewest + extra,
                    crateRadius,
                    (x, y) -> simulation.spawnPickup(PickupKind.SUPPLY_DROP, x, y));
        }
    }
}
