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
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.PickupKind;

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
