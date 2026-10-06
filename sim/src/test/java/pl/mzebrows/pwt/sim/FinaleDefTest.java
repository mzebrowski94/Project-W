package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.BiomeGround;
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
 * A finale is a constant and a block (G0064): a set piece of offsets from its anchor, its waves each from a mouth it
 * has, flipped north-south and never east-west.
 */
class FinaleDefTest {

    @Test
    void bindsTheRedoubtFromItsBlock() {
        FinaleDef redoubt = SimConfig.defaults().finales().of(FinaleKind.REDOUBT);

        assertThat(redoubt.ground().widthMin())
                .isEqualTo(34)
                .isEqualTo(redoubt.ground().widthMax());
        assertThat(redoubt.ground().pieces()).contains(new TileRect(-9, -3, 2, 3));
        assertThat(redoubt.hold().post()).isEqualTo(new Offset(0, 0));
        assertThat(redoubt.hold().watch()).isEqualTo(118f);
        assertThat(redoubt.hold().turrets()).hasSize(4).contains(new Offset(-5, -5));
        assertThat(redoubt.hold().pickups()).contains(new LairLine.Pickup(PickupKind.ARMOURY_FLAMER, 3, 0));
        assertThat(redoubt.hold().mouths()).contains(new Mouth("nw", -14, -13, 4, 4));
        assertThat(redoubt.waves()).first().isEqualTo(new WaveLine(6f, UnitKind.GRUNT, 9, 11, "nw"));
        assertThat(redoubt.lair()).isEmpty();
    }

    @Test
    void refusesAWaveFromAMouthItsHoldLacks() {
        assertThatThrownBy(() -> new FinaleDef(
                        ground(List.of()), hold(), List.of(new WaveLine(5f, UnitKind.GRUNT, 1, 2, "north")), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("north, a mouth the hold lacks");
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
                30, 38, 3, Terrain.DUST, Terrain.ASH, Houses.off(), List.of(), Crags.off(), Outcrops.off(), List.of());

        assertThatThrownBy(() -> new FinaleDef(dealt, hold(), List.of(), List.of()))
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
        FinaleDef redoubt = SimConfig.defaults().finales().of(FinaleKind.REDOUBT);
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
                34, 34, 3, Terrain.DUST, Terrain.ASH, Houses.off(), List.of(), Crags.off(), Outcrops.off(), pieces);
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
