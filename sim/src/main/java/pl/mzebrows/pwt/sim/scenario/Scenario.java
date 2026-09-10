package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;

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
}
