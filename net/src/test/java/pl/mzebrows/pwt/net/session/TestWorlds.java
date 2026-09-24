package pl.mzebrows.pwt.net.session;

import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/** Worlds built from the simulation's own defaults: the config text is the client's to parse, not a test's. */
final class TestWorlds implements WorldFactory {

    @Override
    public Simulation fresh(SessionSetup setup) {
        return Scenarios.world(
                Scenarios.named(setup.scenario()), SimConfig.defaults().withSeed(setup.seed()));
    }

    @Override
    public Simulation blank(SessionSetup setup) {
        return Scenarios.blank(
                Scenarios.named(setup.scenario()), SimConfig.defaults().withSeed(setup.seed()));
    }
}
