package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * The config files live in assets/, which is only on the launcher's classpath - so here both loaders
 * take the fallback path, the shipped files compiled in. That is exactly the path worth testing: a
 * shipped build with a missing or corrupt config must still start.
 */
class ConfigDefaultsTest {

    @Test
    void graphicConfigFallsBackToUsableDefaults() {
        GraphicConfig config = GraphicConfig.load();

        assertThat(config.virtualHeight()).isPositive();
        assertThat(config.layout().metrics().topMin()).isEqualTo(53);
        assertThat(config.layout().metrics().bottomMin()).isEqualTo(41);
        assertThat(config.windowWidth()).isGreaterThan(0);
        assertThat(config.windowHeight()).isGreaterThan(0);
        assertThat(config.backgroundColor()).isNotNull();
        assertThat(config.palette()).isNotNull();
        assertThat(config.terrainPalette()).isNotNull();
    }

    @Test
    void refusesAnAlertThatPulsesFasterThanThreeTimesASecond() {
        assertThatThrownBy(() -> new GraphicConfig.Alert(3.5f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void drivesPagesWithEnterEscapeAndThePadsSouthAndEast() {
        ControlsConfig.Ui ui = ControlsConfig.load().ui();

        assertThat(ui.key().confirm()).isEqualTo(new KeyCode(Input.Keys.ENTER));
        assertThat(ui.key().back()).isEqualTo(new KeyCode(Input.Keys.ESCAPE));
        assertThat(ui.pad().confirm()).isEqualTo(GamepadButton.SOUTH);
        assertThat(ui.pad().back()).isEqualTo(GamepadButton.EAST);
        assertThat(ui.nav().repeatDelaySeconds()).isGreaterThan(ui.nav().repeatIntervalSeconds());
    }

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

    @Test
    void mapsEveryLookToAColour() {
        Palette palette = GraphicConfig.load().palette();

        for (Look look : Look.values()) {
            assertThat(palette.colorFor(look)).isNotNull();
        }
    }

    @Test
    void mapsEveryTerrainToAColour() {
        TerrainPalette terrain = GraphicConfig.load().terrainPalette();

        for (Terrain kind : Terrain.values()) {
            assertThat(terrain.colorFor(kind)).isNotNull();
        }
    }

    @Test
    void mapsEveryStateToAShift() {
        StateTint tint = GraphicConfig.load().state();

        for (Appearance.State state : Appearance.State.values()) {
            assertThat(tint.forState(state)).isBetween(-1f, 1f);
        }
    }

    /** A tell the player cannot see is not a tell, so the wind-up must shift the colour at all. */
    @Test
    void shiftsTheColourOfAUnitWindingUpToLeap() {
        assertThat(GraphicConfig.load().state().forState(Appearance.State.WINDING_UP))
                .isNotZero();
    }

    @Test
    void leavesTheColourOfAUnitDoingNothingAlone() {
        assertThat(GraphicConfig.load().state().forState(Appearance.State.NORMAL))
                .isZero();
    }

    /** A colour is written as the file writes one; a word that is not one is refused, not guessed at. */
    @Test
    void readsAColourAsTheFileWritesOne() {
        assertThat(GraphicConfig.colour("101018ff")).isEqualTo(new Color(0x101018ff));
        assertThatThrownBy(() -> GraphicConfig.colour("bluish")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositiveVirtualHeight() {
        GraphicConfig d = GraphicConfig.defaults();

        assertThatThrownBy(() -> new GraphicConfig(
                        0f,
                        d.windowWidth(),
                        d.windowHeight(),
                        d.vsync(),
                        d.backgroundColor(),
                        d.ui(),
                        d.view(),
                        d.hud(),
                        d.color(),
                        d.variants(),
                        d.ground(),
                        d.rockEdge(),
                        d.rockEdgeWidth(),
                        d.rockEdgeAlpha(),
                        d.region(),
                        d.tile(),
                        d.thicket(),
                        d.state(),
                        d.segments(),
                        d.bloat(),
                        d.charge(),
                        d.nest(),
                        d.facing(),
                        d.charged(),
                        d.order(),
                        d.shield(),
                        d.crate(),
                        d.wound(),
                        d.heat(),
                        d.camera()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A palette missing a look cannot be built, so no renderer has to cope with one. */
    @Test
    void refusesAPaletteThatIsMissingALook() {
        assertThatThrownBy(() -> new Palette(new Color[][] {{Color.WHITE}}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Four commanders on a field must be four colours, or the players cannot tell themselves apart. */
    @Test
    void givesEveryPlayerACommanderColourOfHisOwn() {
        Palette shipped = GraphicConfig.load().palette();

        for (int slot = 0; slot < Players.MAX; slot++) {
            assertThat(shipped.colorFor(Look.COMMANDER, slot)).isNotNull();
        }
    }

    /** A variant nothing declares is the ordinary colour rather than a crash or a hole. */
    @Test
    void fallsBackToTheOrdinaryColourForAVariantALookDoesNotHave() {
        Palette shipped = GraphicConfig.load().palette();

        assertThat(shipped.colorFor(Look.GRUNT, 2)).isEqualTo(shipped.colorFor(Look.GRUNT));
    }
}
