package pl.mzebrows.pwt.client;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.config.SimConfigLoader;
import pl.mzebrows.pwt.net.session.SessionSetup;
import pl.mzebrows.pwt.net.session.WorldFactory;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * The worlds of an online session, built from what the host shipped (ADR 0012): its {@code
 * assets/sim/*.properties} text parsed with this build's loader, its scenario, its seed. Every peer builds from
 * the host's tuning, not its own file - which is what lets a tuning playtest need no file copied.
 */
public final class ScenarioWorlds implements WorldFactory {

    private @Nullable String parsedText;
    private @Nullable SimConfig parsed;

    @Override
    public Simulation fresh(SessionSetup setup) {
        return Scenarios.world(Scenarios.named(setup.scenario()), config(setup));
    }

    @Override
    public Simulation blank(SessionSetup setup) {
        return Scenarios.blank(Scenarios.named(setup.scenario()), config(setup));
    }

    /** Parsed once per text: a restart or a heal builds from the same config again. */
    private SimConfig config(SessionSetup setup) {
        SimConfig config = parsed;
        if (config == null || !setup.configText().equals(parsedText)) {
            config = SimConfigLoader.parseFromHost(setup.configText());
            parsed = config;
            parsedText = setup.configText();
        }
        return config.withSeed(setup.seed());
    }
}
