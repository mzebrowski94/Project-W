package pl.mzebrows.pwt.client.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.ConfigFile;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * What {@code graphic.properties} holds beyond what every config file is held to, and what it falls back
 * to: the file lives in assets/, only on the launcher's classpath, so here it is the shipped one compiled
 * in - the path a build with a missing or corrupt file takes.
 */
class GraphicConfigTest {

    @Test
    void namesAColourThatIsNotOneAndKeepsTheShippedColour() {
        ConfigFile.Parsed<GraphicConfig> parsed = GraphicConfig.FILE.read("graphic.shield.color=bluish\n");

        assertThat(parsed.problems()).contains("cannot read graphic.shield.color=bluish as Color (not a colour)");
        assertThat(parsed.config().shield().color())
                .isEqualTo(GraphicConfig.defaults().shield().color());
    }

    /** A colour for every player the game can hold, so the ceiling cannot move without the file following. */
    @Test
    void holdsAColourForEveryPlayerAGameCanSeat() {
        assertThat(GraphicConfig.Variants.class.getRecordComponents()).hasSize(Players.MAX - 1);
    }

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

    /** A take by touch is seen: it rises off the taker and starts out visible (G0078). */
    @Test
    void readsAPopThatRisesAndIsSeen() {
        PopMark pop = GraphicConfig.load().pop();

        assertThat(pop.rise()).isPositive();
        assertThat(pop.alpha()).isPositive();
        assertThat(pop.riseFor(1f, 1f))
                .as("nothing risen the instant it appears")
                .isZero();
        assertThatThrownBy(() -> new PopMark(1f, 1.5f)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The down mark (G0109) is a red X on a black disc, see-through - red on a black ground, so it reads as the one red
     * mark over the view, and its bars are diagonal by how it is drawn, so it is never a red cross.
     */
    @Test
    void marksAManDownWithASeeThroughRedXOnABlackDisc() {
        DownMark mark = GraphicConfig.load().downMark();

        assertThat(mark.alpha()).isEqualTo(0.65f);
        assertThat(mark.ring()).isEqualTo(Color.BLACK);
        assertThat(mark.cross().r).isGreaterThan(mark.cross().g * 2f).isGreaterThan(mark.cross().b * 2f);
        assertThat(mark.barReach()).isLessThan(mark.radius());
        assertThat(mark.topAbove(1f)).isGreaterThan(mark.centreAbove(1f)).isGreaterThan(1f);
    }

    /** A dash's thrusters are seen while it runs and die away over the tail; its puff grows and is seen (G0107). */
    @Test
    void readsADashsThrustersAndPuff() {
        DashMark dash = GraphicConfig.load().dash();

        assertThat(dash.thruster().strength(true, 5f)).isEqualTo(1f);
        assertThat(dash.thruster().strength(false, 0f)).isEqualTo(1f);
        assertThat(dash.thruster().strength(false, dash.thruster().tailSeconds()))
                .isZero();
        assertThat(dash.puff().alpha()).isPositive();
        assertThat(dash.puff().radiusFor(0f)).isGreaterThan(dash.puff().radiusFor(1f));
        assertThatThrownBy(() -> new DashMark.Puff(1f, 0.5f, 0.5f)).isInstanceOf(IllegalArgumentException.class);
    }

    /** A Rocketman's surge is drawn growing out to its whole reach as it fades (G0108). */
    @Test
    void readsASurgeGrowingOutToItsReach() {
        DashMark.Surge surge = GraphicConfig.load().dash().surge();

        assertThat(surge.radiusFor(10f, 1f)).isLessThan(10f);
        assertThat(surge.radiusFor(10f, 0f)).isEqualTo(10f);
        assertThatThrownBy(() -> new DashMark.Surge(1.5f)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Every commander's rim is drawn, thin and opaque, its colour whatever is tuned; a negative width is refused. */
    @Test
    void readsACommandersRim() {
        BorderMark border = GraphicConfig.load().border();

        assertThat(border.drawn()).isTrue();
        assertThat(border.width()).isLessThan(1f);
        assertThat(border.color().a).isEqualTo(1f);
        assertThatThrownBy(() -> new BorderMark(-0.1f, Color.WHITE)).isInstanceOf(IllegalArgumentException.class);
    }

    /** One shadow for whatever is off the ground - a slung rider, a thrown grenade (G0077) - and it is seen. */
    @Test
    void readsOneShadowForEverythingOffTheGround() {
        assertThat(GraphicConfig.load().shadow().alpha()).isPositive();
    }

    @Test
    void refusesAShadowMoreThanOpaque() {
        assertThatThrownBy(() -> new ShadowMark(0.9f, 0.9f, 1.5f, Color.BLACK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** G0098: the action's marks have their own distance and colours, and a prompt its own see-through backing. */
    @Test
    void readsTheActionsMarksAndThePrompt() {
        GraphicConfig config = GraphicConfig.load();

        assertThat(config.action().noticeDistance()).isPositive();
        assertThat(config.action().chip().occludedAlpha())
                .isLessThan(config.action().chip().alpha());
        assertThat(config.actionMark()).isNotEqualTo(config.pickupMark());
        assertThat(config.hud().upgrade().promptSeconds()).isPositive();
        assertThat(config.action().tick()).isLessThan(config.action().corner());
    }

    /** Zero means off, as everywhere: no notice distance brackets nothing faintly. */
    @Test
    void readsANoticeDistanceOfZeroAsOff() {
        ActionMark off = GraphicConfig.FILE
                .read("graphic.action.noticeDistance=0\n")
                .config()
                .action();

        assertThat(off.notices()).isFalse();
        assertThatThrownBy(() -> new ActionMark(
                        -1f, 0.5f, 0.3f, 0.3f, 0.4f, 0.4f, 0.2f, 0.2f, new ActionMark.Chip(0.6f, 0.25f, 0.15f, 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAnAlertThatPulsesFasterThanThreeTimesASecond() {
        assertThatThrownBy(() -> new GraphicConfig.Alert(3.5f)).isInstanceOf(IllegalArgumentException.class);
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
                        d.door(),
                        d.post(),
                        d.flag(),
                        d.siteRing(),
                        d.mainframe(),
                        d.burst(),
                        d.daze(),
                        d.tree(),
                        d.boulder(),
                        d.sling(),
                        d.shadow(),
                        d.pop(),
                        d.geyser(),
                        d.slither(),
                        d.burrow(),
                        d.devour(),
                        d.limpet(),
                        d.dash(),
                        d.border(),
                        d.downMark(),
                        d.action(),
                        d.actionMark(),
                        d.pickupMark(),
                        d.camera()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The inner foliage hides more than the trunk, and the see-through ring reaches past it (G0072). */
    @Test
    void hidesMoreThanTheTrunkUnderFoliageThatHalfHidesFurtherOut() {
        TreeMark tree = GraphicConfig.defaults().tree();

        assertThat(tree.innerFor(2f)).isGreaterThan(2f);
        assertThat(tree.outerFor(2f)).isGreaterThan(tree.innerFor(2f));
        assertThat(tree.inner().a).as("the inner foliage is opaque").isEqualTo(1f);
        assertThatThrownBy(() -> new TreeMark(2.2f, 2f, 0.5f, Color.GREEN, Color.GREEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Every shading disc of a boulder stays inside its outline, so shading never changes its size on screen. */
    @Test
    void keepsEveryShadingDiscOfABoulderInsideItsOutline() {
        BoulderMark boulder = new BoulderMark(6, 0.4f, 0.7f);

        for (int step = 1; step <= boulder.steps(); step++) {
            float reach = boulder.offsetAt(1f, step) * (float) Math.sqrt(2.0) + boulder.radiusAt(1f, step);

            assertThat(reach).as("step %d", step).isLessThanOrEqualTo(1.0001f);
        }
        assertThat(boulder.lightAt(boulder.steps())).isEqualTo(0.4f);
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
