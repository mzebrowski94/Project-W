package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.ui.layout.LayoutSpec;

/**
 * The config files live in assets/, which is only on the launcher's classpath - so here both loaders
 * take the fallback path. That is exactly the path worth testing: a shipped build with a missing or
 * corrupt config must still start.
 */
class ConfigDefaultsTest {

    @Test
    void graphicConfigFallsBackToUsableDefaults() {
        GraphicConfig config = GraphicConfig.load();

        assertThat(config.virtualHeight()).isPositive();
        assertThat(config.layout().metrics().topMin()).isEqualTo(46);
        assertThat(config.layout().metrics().bottomMin()).isEqualTo(40);
        assertThat(config.hud()).isNotNull();
        assertThat(config.windowWidth()).isGreaterThan(0);
        assertThat(config.windowHeight()).isGreaterThan(0);
        assertThat(config.backgroundColor()).isNotNull();
        assertThat(config.palette()).isNotNull();
        assertThat(config.terrainPalette()).isNotNull();
        assertThat(config.stateTint()).isNotNull();
        assertThat(config.chargeMark()).isNotNull();
        assertThat(config.chargedMark()).isNotNull();
        assertThat(config.camera()).isNotNull();
    }

    @Test
    void refusesAnAlertThatPulsesFasterThanThreeTimesASecond() {
        HudStyle hud = GraphicConfig.load().hud();

        assertThatThrownBy(() -> new HudStyle(
                        hud.text(),
                        hud.textDim(),
                        hud.alert(),
                        hud.overlayFrame(),
                        hud.bar(),
                        hud.overlayWidthShare(),
                        hud.motionScale(),
                        hud.flashScale(),
                        hud.alarmSeconds(),
                        hud.alarmShake(),
                        3.5f,
                        hud.goalHoldSeconds(),
                        hud.reticleSize()))
                .isInstanceOf(IllegalArgumentException.class);
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
    void rejectsANonPositiveVirtualHeight() {
        LayoutSpec layout = GraphicConfig.load().layout();
        HudStyle hud = GraphicConfig.load().hud();
        assertThatThrownBy(() -> new GraphicConfig(
                        0f,
                        layout,
                        hud,
                        1280,
                        720,
                        true,
                        Color.BLACK,
                        palette(),
                        terrain(),
                        groundDetail(),
                        tint(),
                        mark(),
                        bloat(),
                        nest(),
                        facing(),
                        charged(),
                        ordered(),
                        heat(),
                        camera()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static HeatMark heat() {
        return new HeatMark(1.7f, 0.28f, 150f, 14);
    }

    private static OrderMark ordered() {
        return new OrderMark(1.55f, 0.75f);
    }

    private static ChargedMark charged() {
        return new ChargedMark(1.35f, Color.RED);
    }

    private static FacingMark facing() {
        return new FacingMark(1.35f, 0.34f, 0.55f);
    }

    private static Palette palette() {
        Color[][] colors = new Color[Look.values().length][];
        java.util.Arrays.fill(colors, new Color[] {Color.WHITE});
        return new Palette(colors);
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

    private static TerrainPalette terrain() {
        Color[] colors = new Color[Terrain.values().length];
        java.util.Arrays.fill(colors, Color.GRAY);
        return new TerrainPalette(colors, 0.05f, 0.03f, 4, 3);
    }

    private static GroundDetail groundDetail() {
        return new GroundDetail(3, 0.42f, Color.GREEN, 0.5f, Color.GRAY, 0.17f, 1f);
    }

    private static StateTint tint() {
        return new StateTint(-0.4f, 0.85f, 0.5f, -0.3f, 0.35f, -0.18f, -0.7f, 0.6f, 0.45f);
    }

    private static NestMark nest() {
        return new NestMark(4, 0.55f);
    }

    private static BloatMark bloat() {
        return new BloatMark(1.5f, 0.5f);
    }

    private static ChargeMark mark() {
        return new ChargeMark(0.55f, 0.8f);
    }

    private static CameraDirector.Settings camera() {
        return new CameraDirector.Settings(6f, 8f, 14f, 2f, 1f, 2.2f, 7f, 1.25f, 1f, 3f);
    }
}
