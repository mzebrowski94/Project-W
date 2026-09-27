package pl.mzebrows.pwt.sim.scenario;

import java.util.List;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.RngStream;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;

/**
 * Every scenario the menu offers, in the order it offers them.
 *
 * <p>This list is the whole registry. A new scenario is a class plus a line here - if you add one
 * and it does not appear in the menu, this is the line you forgot.
 */
public final class Scenarios {

    private static final List<Scenario> ALL = List.of(
            new AdventureScenario(),
            new AdventureTwoScenario(),
            new AdvanceScenario(),
            new SkirmishScenario(),
            new DefendScenario(),
            new ThicketScenario(),
            new ExpanseScenario(),
            new DuelScenario(),
            new FeedingGroundScenario(),
            new DemolitionScenario(),
            new CasualtyScenario(),
            new CrowdScenario());

    private Scenarios() {}

    /** Every registered scenario. Immutable. */
    public static List<Scenario> all() {
        return ALL;
    }

    /**
     * The registered scenario with this name - how an online session names the one it plays (ADR 0012).
     *
     * @throws IllegalArgumentException if there is none; peers on one build know the same scenarios
     */
    public static Scenario named(String name) {
        for (Scenario scenario : ALL) {
            if (scenario.name().equals(name)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("no scenario named \"" + name + "\"");
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
        TileMap map = scenario.map(config, Rng.stream(config.seed(), RngStream.MAP));
        Simulation simulation = new Simulation(config, map);
        scenario.populate(simulation);
        // After the scenario, because it is the scenario that says where the control points are.
        scenario.deployReinforcements(simulation);
        return simulation;
    }

    /**
     * The world a scenario describes with nothing in it but the level: its config and its map, and no
     * contents. What a snapshot is restored into (ADR 0014) - the map is rebuilt from the seed on every
     * machine rather than carried, and everything standing on it comes from the snapshot.
     */
    public static Simulation blank(Scenario scenario, SimConfig base) {
        SimConfig config = scenario.configure(base);
        TileMap map = scenario.map(config, Rng.stream(config.seed(), RngStream.MAP));
        return new Simulation(config, map);
    }
}
