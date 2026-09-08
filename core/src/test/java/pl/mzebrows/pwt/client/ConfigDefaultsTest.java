package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.def.Look;

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
        assertThat(config.palette()).isNotNull();
        assertThat(config.stateTint()).isNotNull();
        assertThat(config.chargeMark()).isNotNull();
    }

    @Test
    void simConfigFallsBackToTheSimulationsOwnDefaults() {
        assertThat(SimConfigLoader.load()).isEqualTo(SimConfig.defaults());
    }

    @Test
    void mapsEveryLookToAColour() {
        Palette palette = GraphicConfig.load().palette();

        for (Look look : Look.values()) {
            assertThat(palette.colorFor(look)).isNotNull();
        }
    }

    @Test
    void mapsEveryStateToAShift() {
        StateTint tint = GraphicConfig.load().stateTint();

        for (Appearance.State state : Appearance.State.values()) {
            assertThat(tint.forState(state)).isBetween(-1f, 1f);
        }
    }

    /** A tell the player cannot see is not a tell, so the wind-up must shift the colour at all. */
    @Test
    void shiftsTheColourOfAUnitWindingUpToLeap() {
        assertThat(GraphicConfig.load().stateTint().forState(Appearance.State.WINDING_UP))
                .isNotZero();
    }

    @Test
    void leavesTheColourOfAUnitDoingNothingAlone() {
        assertThat(GraphicConfig.load().stateTint().forState(Appearance.State.NORMAL))
                .isZero();
    }

    @Test
    void backgroundColourStringsParse() {
        assertThat(Color.valueOf("101018ff")).isEqualTo(new Color(0x101018ff));
    }

    @Test
    void rejectsANonPositiveVirtualSize() {
        assertThatThrownBy(() -> new GraphicConfig(0f, 90f, 1280, 720, true, Color.BLACK, palette(), tint(), mark()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Palette palette() {
        Color[] colors = new Color[Look.values().length];
        java.util.Arrays.fill(colors, Color.WHITE);
        return new Palette(colors);
    }

    /** A palette missing a look cannot be built, so no renderer has to cope with one. */
    @Test
    void refusesAPaletteThatIsMissingALook() {
        assertThatThrownBy(() -> new Palette(new Color[] {Color.WHITE})).isInstanceOf(IllegalArgumentException.class);
    }

    private static StateTint tint() {
        return new StateTint(0.85f, 0.5f, -0.3f, 0.35f, -0.7f);
    }

    private static ChargeMark mark() {
        return new ChargeMark(0.55f, 0.8f);
    }
}
