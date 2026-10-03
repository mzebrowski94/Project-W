package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.sim.SimConfig;

/**
 * The config files live in assets/, which is only on the launcher's classpath - so here every loader
 * takes the fallback path, the shipped files compiled in. That is exactly the path worth testing: a
 * shipped build with a missing or corrupt config must still start. What each record falls back to is
 * tested beside it ({@code GraphicConfigTest}, {@code ControlsConfigTest}).
 */
class ConfigDefaultsTest {

    @Test
    void simConfigFallsBackToTheSimulationsOwnDefaults() {
        assertThat(SimConfigLoader.load()).isEqualTo(SimConfig.defaults());
    }

    @Test
    void netConfigFallsBackToTheNetModulesOwnDefaults() {
        assertThat(NetConfigLoader.load()).isEqualTo(NetConfig.defaults());
    }

    @Test
    void graphicAndControlsConfigFallBackToWhatShips() {
        assertThat(GraphicConfig.load()).isEqualTo(GraphicConfig.defaults());
        assertThat(ControlsConfig.load()).isEqualTo(ControlsConfig.defaults());
    }
}
