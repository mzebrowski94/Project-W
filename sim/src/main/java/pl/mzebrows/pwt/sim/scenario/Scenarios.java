package pl.mzebrows.pwt.sim.scenario;

import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.RngStream;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.Strength;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

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
            new CrowdScenario(),
            new FullHouseScenario(),
            new DashYardScenario());

    private Scenarios() {}

    /** Every registered scenario. Immutable. */
    public static List<Scenario> all() {
        return ALL;
    }

    /**
     * The proving ground built from one setup (G0060). Not in the registry: it is reached from its own
     * page, never from the campaign list, and is built through {@link #world} like everything else.
     */
    public static Scenario provingGround(ProvingSetup setup) {
        return new ProvingGroundScenario(setup);
    }

    /**
     * The proving ground page's window onto one strain: it on a patch of sand, nobody else. Not a level -
     * there is no commander - but a world built through {@link #world} all the same.
     */
    public static Scenario preview(UnitKind strain) {
        return new ProvingPreviewScenario(strain);
    }

    /**
     * A mission on one tier (G0065), built from the seed of the config it is given - the mission seed. Not in the
     * registry: it is reached by its code, never from the campaign list, and is built through {@link #world} like
     * everything else.
     */
    public static Scenario mission(TierKind tier) {
        return new MissionScenario(tier, MissionScenario.CONFIGURED, Strength.NORMAL);
    }

    /** A mission on one tier and {@code length} biomes long (G0102): refused when the world is built if the config has no such length. */
    public static Scenario mission(TierKind tier, int length) {
        return new MissionScenario(tier, length, Strength.NORMAL);
    }

    /** A mission with the strength its host gave it (G0106): refused when the world is built if the config has no such one. */
    public static Scenario mission(TierKind tier, int length, Strength strength) {
        return new MissionScenario(tier, length, strength);
    }

    /**
     * The registered scenario with this name, or a mission by its form, {@code mission <tier>} or {@code mission
     * <tier> <length>} - how an online session names the one it plays (ADR 0012, G0065, G0102). A mission's seed
     * travels as the session's.
     *
     * @throws IllegalArgumentException if there is none; peers on one build know the same scenarios
     */
    public static Scenario named(String name) {
        for (Scenario scenario : ALL) {
            if (scenario.name().equals(name)) {
                return scenario;
            }
        }
        MissionName mission = MissionName.parse(name);
        if (mission != null) {
            return mission(mission.tier(), mission.length(), mission.strength());
        }
        throw new IllegalArgumentException("no scenario named \"" + name + "\"");
    }

    /**
     * The registered scenario a developer meant by this name, typed loosely: case, spaces, hyphens and
     * underscores aside, so {@code full-house}, {@code FullHouse} and {@code Full House} are one name. Only the
     * registry - never a mission or the proving ground, which have their own ways in. What {@code --scenario}
     * opens; an online session names its scenario exactly, by {@link #named}.
     *
     * @return the scenario, or null if no registered one has this name
     */
    public static @Nullable Scenario looselyNamed(String name) {
        String wanted = loose(name);
        for (Scenario scenario : ALL) {
            if (loose(scenario.name()).equals(wanted)) {
                return scenario;
            }
        }
        return null;
    }

    /** A scenario's name as it is typed on a command line: lower case, a hyphen for each space. */
    public static String typedName(Scenario scenario) {
        return scenario.name().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private static String loose(String name) {
        StringBuilder kept = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != ' ' && c != '-' && c != '_') {
                kept.append(c);
            }
        }
        return kept.toString().toLowerCase(Locale.ROOT);
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
        // After the crates, so a medkit is laid clear of them.
        scenario.deployMedkits(simulation);
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
