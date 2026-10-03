package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.client.config.SimConfigLoader;
import pl.mzebrows.pwt.net.session.SessionSetup;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WorldCodec;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

class ScenarioWorldsTest {

    @Test
    void readsTheShippedTextToWhatTheFileLoadsTo() {
        assertThat(SimConfigLoader.parse(SimConfigLoader.text())).isEqualTo(SimConfigLoader.load());
    }

    @Test
    void shipsTheTextTheLoadedConfigWasBoundFrom() {
        SimConfigLoader.Loaded loaded = SimConfigLoader.loaded();

        assertThat(SimConfigLoader.parseFromHost(loaded.shipped())).isEqualTo(loaded.config());
    }

    @Test
    void buildsTheHostsScenarioWithTheHostsSeed() {
        SessionSetup setup = new SessionSetup(SimConfigLoader.text(), "Skirmish", 42L);

        Simulation world = new ScenarioWorlds().fresh(setup);

        assertThat(world.config().seed()).isEqualTo(42L);
        assertThat(WorldCodec.hash(world))
                .isEqualTo(WorldCodec.hash(Scenarios.world(
                        Scenarios.named("Skirmish"), SimConfigLoader.load().withSeed(42L))));
    }

    @Test
    void playsTheHostsTuningRatherThanItsOwn() {
        SessionSetup tuned = new SessionSetup(SimConfigLoader.text() + "\nsim.maxTicksPerFrame=9\n", "Duel", 1L);

        assertThat(new ScenarioWorlds().fresh(tuned).config().maxTicksPerFrame())
                .isEqualTo(9);
    }

    @Test
    void restoresASnapshotIntoTheSameBlankMapTheHostHas() {
        SessionSetup setup = new SessionSetup(SimConfigLoader.text(), "Thicket", 7L);
        ScenarioWorlds worlds = new ScenarioWorlds();
        Simulation host = worlds.fresh(setup);

        Simulation joiner = worlds.blank(setup);
        WorldCodec.restore(joiner, WorldCodec.snapshot(host));

        assertThat(WorldCodec.hash(joiner)).isEqualTo(WorldCodec.hash(host));
    }
}
