package pl.mzebrows.pwt.net.session;

import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * Worlds built from the simulation's own defaults: the config text is the client's to parse, not a test's.
 *
 * <p>A scenario named with {@link #DRAFTING} after it is that scenario plus points that take themselves:
 * each covers the whole map and is taken after a set time with anybody on it, a couple of seconds apart,
 * so drafts are earned on every machine on the same ticks once everybody has had time to sit down. Its aliens notice
 * nobody, are roused by nothing and bite for nothing, so whether a draft comes is never a question of who survived
 * the scenario's fight - that is the balance's business, not the network's.
 */
final class TestWorlds implements WorldFactory {

    /** Added to a scenario's name for the variant that earns drafts on a timer. */
    static final String DRAFTING = " (drafting)";

    /** When the first point is taken, and how far apart the rest are, in seconds. */
    static final float FIRST_TAKEN = 10f;

    static final float APART = 2f;

    /** Nine points: drafts at the first, the fifth and the ninth (sim.upgrade.draft.pointsPerDraft=4). */
    static final int POINTS = 9;

    /** A radius past any map a test plays on. */
    private static final float EVERYWHERE = 10_000f;

    @Override
    public Simulation fresh(SessionSetup setup) {
        SimConfig config = config(setup);
        Simulation world = Scenarios.world(scenario(setup), config);
        if (setup.scenario().endsWith(DRAFTING)) {
            float middleX = world.map().worldWidth() / 2f;
            float middleY = world.map().worldHeight() / 2f;
            for (int i = 0; i < POINTS; i++) {
                world.spawnControlPoint(middleX, middleY)
                        .getComponent(ControlPoint.class)
                        .set(EVERYWHERE, Ticks.of(FIRST_TAKEN + i * APART, config.tickSeconds()), 0f);
            }
        }
        return world;
    }

    @Override
    public Simulation blank(SessionSetup setup) {
        return Scenarios.blank(scenario(setup), config(setup));
    }

    /** The defaults, and for a drafting variant aliens that cannot end the run before its points are taken. */
    private static SimConfig config(SessionSetup setup) {
        SimConfig config = SimConfig.defaults().withSeed(setup.seed());
        if (!setup.scenario().endsWith(DRAFTING)) {
            return config;
        }
        SimConfig quiet = config.withControlPoint(config.controlPoint().withAggroRadius(0f));
        for (UnitKind kind : UnitKind.values()) {
            quiet = quiet.withUnit(
                    kind,
                    strain -> strain.withContactDamage(0)
                            .withPerception(strain.perception()
                                    .withAlwaysAggressive(false)
                                    .withDetectionRange(0f)
                                    .withLoseRange(0f)
                                    .withSecondaryRange(0f)
                                    .withAllRoundRange(0f)));
        }
        return quiet;
    }

    private static Scenario scenario(SessionSetup setup) {
        String name = setup.scenario();
        return Scenarios.named(name.endsWith(DRAFTING) ? name.substring(0, name.length() - DRAFTING.length()) : name);
    }
}
