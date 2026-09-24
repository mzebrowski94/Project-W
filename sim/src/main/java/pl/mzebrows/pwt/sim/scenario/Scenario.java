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
 * <p>A scenario is a Java class rather than a data file on purpose: it can script a spawn sequence,
 * place things in a deliberately awkward arrangement, or set up the exact situation a system is
 * meant to handle - none of which a spawn list expresses. Adding one means a recompile, which costs
 * nothing here.
 *
 * <p>Scenarios are content, not design. Numbers in one are chosen to make a system visible, not to
 * balance the game, and a scenario is free to be unfair or absurd if that is what shows the system
 * working.
 */
public interface Scenario {

    /** Short name, as it appears in the menu. */
    String name();

    /** One line saying what this scenario is for - which system it exercises, and what to look at. */
    String description();

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
     * <p>The default is open ground the size {@code config.map()} asks for, partitioned into regions
     * so the level reads as places rather than one flat sheet. Override to lay out real terrain.
     *
     * <p>{@code randomness} is map generation's <em>own</em> stream, derived from the level seed and
     * separate from the one the simulation draws from - so changing a layout never reshuffles what
     * happens during play, and the level is still reproducible from one seed.
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
     * <p>One place doing this is what makes "the squad starts where the commander starts" a fact
     * rather than something each scenario re-implements slightly differently. A scenario that wants
     * the commander somewhere else simply spawns him itself instead of calling this.
     *
     * <p><strong>Everyone is jittered inside the area's tiles</strong>, the commander included
     * (G0009). A squad lined up on tile centres reads as a grid immediately, and the grid is a unit
     * of construction rather than of play. He is placed first so the squad forms up around him
     * rather than through him.
     *
     * <p>If the area is too small to hold the commander at all - a spawn area one tile across with a
     * body that does not fit in it - he goes at its centre rather than nowhere, because every
     * scenario must produce exactly one commander and a level with none cannot be played at all.
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
     * <p><strong>Men are where the ground you have to take is.</strong> A point already asks you to
     * walk into somewhere and stand in it while the level answers; putting the crate there makes the
     * squad you fight with the reward for the fighting, which is pillar 3 stated as a place on the
     * map rather than as a rule. Scattering them anywhere else would have made them litter.
     *
     * <p>Just outside the point's own radius, so it is a step off the ground you are holding rather
     * than something that lands in your lap for standing still - and jittered into its tiles like
     * everything else (G0009).
     *
     * <p><strong>How many is drawn per point</strong>, between the two numbers the config names. A
     * fixed number makes every point on a level worth exactly the same and the third one not worth
     * looking at; a range means walking in to see what is there, which is the same reason nothing
     * else in this game is laid out on a grid.
     *
     * <p>Called by {@code Scenarios.world} after the scenario has populated itself, so it is a fact
     * about every level rather than a line each one remembers. A scenario that wants none overrides
     * it; that is what a default method is for.
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
        // Its own stream: where a level lays its crates must not shift because something unrelated
        // drew a number first, and the layout is settled once, here. The stream itself is the
        // simulation's - the same instance the reinforcements will draw from afterwards, so the two
        // halves of one concern are one sequence rather than two copies of it.
        Rng rng = simulation.stream(RngStream.REINFORCEMENT);
        Scatter scatter = new Scatter(map, rng, crateRadius, crateRadius);
        // Every point is reserved before anything is placed, so a crate laid beside one point is not
        // laid on top of the next one along.
        for (int i = 0; i < points.size(); i++) {
            Position at = points.get(i).getComponent(Position.class);
            scatter.reserve(at.x, at.y, config.controlPoint().radius());
        }
        // From a bag, so the points on a level are worth different amounts rather than, by chance, all
        // the least.
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
