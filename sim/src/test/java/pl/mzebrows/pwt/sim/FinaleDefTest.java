package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.map.Clearings;
import pl.mzebrows.pwt.map.Crags;
import pl.mzebrows.pwt.map.Houses;
import pl.mzebrows.pwt.map.Outcrops;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.def.FinaleKind;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A finale is a constant and a block (G0064): a set piece of offsets from its anchor, one of a few hand-made layouts
 * (G0087), its waves each from mouths it has, flipped north-south and never east-west.
 */
class FinaleDefTest {

    @Test
    void bindsTheRedoubtFromItsBlock() {
        FinaleDef redoubt = SimConfig.defaults().finales().of(FinaleKind.REDOUBT);

        assertThat(redoubt.ground().widthMin())
                .isEqualTo(34)
                .isEqualTo(redoubt.ground().widthMax());
        assertThat(redoubt.layouts()).hasSize(3);
        FinaleDef square = redoubt.laid(0);
        assertThat(square.ground().pieces()).contains(new TileRect(-9, -3, 2, 3));
        assertThat(square.hold().post()).isEqualTo(new Offset(0, 0));
        assertThat(square.hold().watch()).isEqualTo(118f);
        assertThat(square.hold().turrets()).hasSize(4).contains(new Offset(-5, -5));
        assertThat(square.hold().pickups()).contains(new LairLine.Pickup(PickupKind.ARMOURY_FLAMER, 3, 0));
        assertThat(square.hold().mouths()).contains(new Mouth("nw", -14, -13, 4, 4));
        assertThat(redoubt.waves()).first().isEqualTo(new WaveLine(6f, UnitKind.GRUNT, 9, 11, "nw"));
        assertThat(redoubt.lair()).isEmpty();
    }

    /**
     * A layout is laid over what every layout shares (G0087): its own rock and guns added to the finale's post, watch,
     * mouths and pickups, which stay where they are whichever is dealt. Every layout of the redoubt has its four guns.
     */
    @Test
    void laysEachLayoutOverWhatEveryLayoutShares() {
        FinaleDef redoubt = SimConfig.defaults().finales().of(FinaleKind.REDOUBT);

        for (int layout = 0; layout < redoubt.layoutCount(); layout++) {
            FinaleDef laid = redoubt.laid(layout);

            assertThat(laid.layouts()).isEmpty();
            assertThat(laid.ground().pieces())
                    .isEqualTo(redoubt.layouts().get(layout).pieces());
            assertThat(laid.hold().turrets()).hasSize(4);
            assertThat(laid.hold().post()).isEqualTo(redoubt.hold().post());
            assertThat(laid.hold().mouths()).isEqualTo(redoubt.hold().mouths());
            assertThat(laid.hold().pickups()).containsAll(redoubt.hold().pickups());
            assertThat(laid.waves()).isEqualTo(redoubt.waves());
        }
        assertThat(redoubt.laid(1).hold().turrets())
                .isNotEqualTo(redoubt.laid(0).hold().turrets());
    }

    /** A finale as laid is a finale of one layout, its own. */
    @Test
    void laysAFinaleWithNoLayoutsAsItself() {
        FinaleDef plain = new FinaleDef(ground(List.of()), hold(), List.of(), List.of(), List.of());

        assertThat(plain.layoutCount()).isEqualTo(1);
        assertThat(plain.laid(0)).isEqualTo(plain);
    }

    /** Half the redoubt's waves come from two mouths at once (G0087), more of them late than early. */
    @Test
    void sendsHalfTheRedoubtsWavesFromTwoMouthsMostlyLate() {
        List<WaveLine> waves =
                SimConfig.defaults().finales().of(FinaleKind.REDOUBT).waves();
        int half = waves.size() / 2;

        long early = waves.subList(0, half).stream()
                .filter(wave -> wave.mouthNames().size() == 2)
                .count();
        long late = waves.subList(half, waves.size()).stream()
                .filter(wave -> wave.mouthNames().size() == 2)
                .count();

        assertThat(early + late).isEqualTo(half);
        assertThat(late).isGreaterThan(early);
    }

    @Test
    void readsAWaveFromSeveralMouthsJoinedByAPlus() {
        assertThat(new WaveLine(5f, UnitKind.GRUNT, 1, 2, "nw+se").mouthNames()).containsExactly("nw", "se");
        assertThat(new WaveLine(5f, UnitKind.GRUNT, 1, 2, "nw").mouthNames()).containsExactly("nw");
    }

    @Test
    void refusesAWaveThatNamesAMouthTwice() {
        assertThatThrownBy(() -> new WaveLine(5f, UnitKind.GRUNT, 1, 2, "nw+nw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("each of its mouths once");
    }

    /** Every mouth a wave names, the second of two as much as the first. */
    @Test
    void refusesAWaveFromAMouthItsHoldLacks() {
        for (String mouths : List.of("north", "north+nw", "nw+north")) {
            assertThatThrownBy(() -> new FinaleDef(
                            ground(List.of()),
                            hold(),
                            List.of(),
                            List.of(new WaveLine(5f, UnitKind.GRUNT, 1, 2, mouths)),
                            List.of()))
                    .as(mouths)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("north, a mouth the hold lacks");
        }
    }

    @Test
    void refusesAHoldPickupThatIsNotAPickup() {
        assertThatThrownBy(() -> new Hold(
                        new Offset(0, 0),
                        118f,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new LairLine.Unit(UnitKind.BRUTE, 3, 0)),
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a hold's pickups are pickups");
    }

    @Test
    void refusesAFinaleWhoseWidthIsDealt() {
        BiomeGround dealt = new BiomeGround(
                30,
                38,
                3,
                Terrain.DUST,
                Terrain.ASH,
                Houses.off(),
                List.of(),
                Crags.off(),
                Outcrops.off(),
                Clearings.off(),
                List.of());

        assertThatThrownBy(() -> new FinaleDef(dealt, hold(), List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("width is fixed");
    }

    /**
     * The keep is the redoubt's ground with a lair instead of waves or guns: holes, then the cache, then the boss, in
     * the order written (G0048, G0064).
     */
    @Test
    void bindsTheKeepFromItsBlock() {
        FinaleDef keep = SimConfig.defaults().finales().of(FinaleKind.KEEP);

        assertThat(keep.ground().base()).isEqualTo(Terrain.ASH);
        assertThat(keep.hold().watch()).isEqualTo(118f);
        assertThat(keep.hold().barrels()).doesNotContain(new Offset(12, 0));
        assertThat(keep.layouts()).hasSizeBetween(2, 3);
        assertThat(keep.layouts())
                .allSatisfy(layout -> assertThat(layout.mines()).isNotEmpty());
        assertThat(keep.waves()).isEmpty();
        assertThat(keep.hold().mouths()).isEmpty();
        assertThat(keep.hold().turrets()).isEmpty();
        assertThat(keep.lair()).hasSize(11);
        assertThat(keep.lair().getFirst()).isEqualTo(new LairLine.Nest(UnitKind.GRUNT, 13, -6));
        assertThat(keep.lair().getLast()).isEqualTo(new LairLine.Unit(UnitKind.BOSS_GRUNT, 13, 0));
    }

    /** The flip negates every dy and turns nothing east-west, so west stays the way in (G0064). */
    @Test
    void flipsEveryPositionNorthSouthAndNoneEastWest() {
        FinaleDef redoubt =
                SimConfig.defaults().finales().of(FinaleKind.REDOUBT).laid(0);
        FinaleDef flipped = redoubt.flipped();

        assertThat(flipped.ground().pieces()).contains(new TileRect(-9, 1, 2, 3), new TileRect(0, -9, 3, 1));
        assertThat(flipped.hold().turrets())
                .containsExactlyElementsOf(redoubt.hold().turrets().stream()
                        .map(at -> new Offset(at.dx(), -at.dy()))
                        .toList());
        assertThat(flipped.hold().pickups()).contains(new LairLine.Pickup(PickupKind.CRATE, -3, 0));
        assertThat(flipped.hold().mouth("nw")).isEqualTo(new Mouth("nw", -14, 10, 4, 4));
        assertThat(flipped.waves()).isEqualTo(redoubt.waves());
        assertThat(flipped.flipped()).isEqualTo(redoubt);
        FinaleDef keep = SimConfig.defaults().finales().of(FinaleKind.KEEP);
        assertThat(keep.flipped().lair()).contains(new LairLine.Nest(UnitKind.GRUNT, 13, 6));
        assertThat(keep.flipped().flipped()).isEqualTo(keep);
    }

    /** A layout dealt and then flipped is the flipped finale's same layout: the flip reaches every one (G0087). */
    @Test
    void flipsEveryLayoutAsItFlipsTheRest() {
        for (FinaleKind kind : FinaleKind.values()) {
            FinaleDef def = SimConfig.defaults().finales().of(kind);
            for (int layout = 0; layout < def.layoutCount(); layout++) {
                assertThat(def.flipped().laid(layout))
                        .as("%s layout %d", kind, layout)
                        .isEqualTo(def.laid(layout).flipped());
            }
        }
    }

    /** A wave counts like a roster line: drawn, then the tier's percent once (G0063), and no ramp. */
    @Test
    void countsAWaveThroughTheTiersPressureWithNoRamp() {
        MissionConfig mission = SimConfig.defaults().mission();
        WaveLine line = new WaveLine(5f, UnitKind.GRUNT, 8, 8, "nw");

        assertThat(mission.finale(TierKind.MEDIUM).rampedPercent()).isEqualTo(100);
        assertThat(line.count(new Rng(1L), new MissionConfig.Pressure(125, 100)))
                .isEqualTo(10);
    }

    private static BiomeGround ground(List<TileRect> pieces) {
        return new BiomeGround(
                34,
                34,
                3,
                Terrain.DUST,
                Terrain.ASH,
                Houses.off(),
                List.of(),
                Crags.off(),
                Outcrops.off(),
                Clearings.off(),
                pieces);
    }

    private static Hold hold() {
        return new Hold(
                new Offset(0, 0),
                118f,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new Mouth("nw", -14, -13, 4, 4)));
    }
}
