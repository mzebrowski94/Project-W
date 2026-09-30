package pl.mzebrows.pwt.net.session;

import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * Worlds built from the simulation's own defaults: the config text is the client's to parse, not a test's.
 *
 * <p>A scenario named with {@link #DRAFTING} after it is that scenario plus points that take themselves:
 * each covers the whole map and is taken after a set time with anybody on it, a couple of seconds apart,
 * so drafts are earned on every machine on the same ticks once everybody has had time to sit down.
 */
final class TestWorlds implements WorldFactory {

    /** Added to a scenario's name for the variant that earns drafts on a timer. */
    static final String DRAFTING = " (drafting)";

    /** When the first point is taken, and how far apart the rest are, in seconds. */
    static final float FIRST_TAKEN = 10f;

    static final float APART = 2f;

    /** Five points: drafts at the first, the third and the fifth (sim.upgrade.draft.pointsPerDraft=2). */
    static final int POINTS = 5;

    /** A radius past any map a test plays on. */
    private static final float EVERYWHERE = 10_000f;

    @Override
    public Simulation fresh(SessionSetup setup) {
        SimConfig config = SimConfig.defaults().withSeed(setup.seed());
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
        return Scenarios.blank(scenario(setup), SimConfig.defaults().withSeed(setup.seed()));
    }

    private static Scenario scenario(SessionSetup setup) {
        String name = setup.scenario();
        return Scenarios.named(name.endsWith(DRAFTING) ? name.substring(0, name.length() - DRAFTING.length()) : name);
    }
}
