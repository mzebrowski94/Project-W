package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;

/**
 * The config files live in assets/, which is only on the launcher's classpath - so here both loaders
 * take the fallback path. That is exactly the path worth testing: a shipped build with a missing or
 * corrupt config must still start.
 */
class ConfigDefaultsTest {

    @Test
    void graphicConfigFallsBackToUsableDefaults() {
        GraphicConfig config = GraphicConfig.load();

        assertThat(config.virtualWidth()).isPositive();
        assertThat(config.virtualHeight()).isPositive();
        assertThat(config.windowWidth()).isGreaterThan(0);
        assertThat(config.windowHeight()).isGreaterThan(0);
        assertThat(config.backgroundColor()).isNotNull();
    }

    @Test
    void simConfigFallsBackToTheSimulationsOwnDefaults() {
        assertThat(SimConfigLoader.load()).isEqualTo(SimConfig.defaults());
    }

    @Test
    void backgroundColourStringsParse() {
        assertThat(Color.valueOf("101018ff")).isEqualTo(new Color(0x101018ff));
    }

    @Test
    void rejectsANonPositiveVirtualSize() {
        assertThatThrownBy(() -> new GraphicConfig(0f, 90f, 1280, 720, true, Color.BLACK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
