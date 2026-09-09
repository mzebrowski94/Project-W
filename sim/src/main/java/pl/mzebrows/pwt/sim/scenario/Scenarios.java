package pl.mzebrows.pwt.sim.scenario;

import java.util.List;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;

/**
 * Every scenario the menu offers, in the order it offers them.
 *
 * <p>This list is the whole registry. A new scenario is a class plus a line here - if you add one
 * and it does not appear in the menu, this is the line you forgot.
 */
public final class Scenarios {

    /**
     * Mixed into the level seed to give map generation a stream of its own.
     *
     * <p>One stream per concern is what stops re-tuning a layout from reshuffling everything the
     * simulation later draws - the leap wind-ups, the scatter - which is the usual reason procedural
     * generation is miserable to iterate on. The whole level is still a function of one seed, so
     * pillar 6 holds.
     */
    private static final long MAP_STREAM = 0x51ED_270B_6EFC_2FB7L;

    private static final List<Scenario> ALL = List.of(
            new SkirmishScenario(),
            new WarrenScenario(),
            new ExpanseScenario(),
            new DuelScenario(),
            new FeedingGroundScenario(),
            new CrowdScenario());

    private Scenarios() {}

    /** Every registered scenario. Immutable. */
    public static List<Scenario> all() {
        return ALL;
    }

    /**
     * Builds the world a scenario describes: its config, then its map, then a simulation on that map,
     * then its contents. <strong>This is the only place a playable world is assembled</strong>, so the
     * harness, the determinism test and the scenario tests cannot drift into building one differently
     * - which is exactly the kind of difference that makes a replay stop replaying.
     *
     * <p>The order is forced: the map's extent is the arena, so it has to exist before anything that
     * is sized against it.
     */
    public static Simulation world(Scenario scenario, SimConfig base) {
        SimConfig config = scenario.configure(base);
        TileMap map = scenario.map(config, new Rng(config.seed() ^ MAP_STREAM));
        Simulation simulation = new Simulation(config, map);
        scenario.populate(simulation);
        return simulation;
    }
}
