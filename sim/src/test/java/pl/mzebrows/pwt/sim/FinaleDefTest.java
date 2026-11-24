package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.map.Clearings;
import pl.mzebrows.pwt.map.Corridors;
import pl.mzebrows.pwt.map.Crags;
import pl.mzebrows.pwt.map.Houses;
import pl.mzebrows.pwt.map.Ice;
import pl.mzebrows.pwt.map.Outcrops;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.FinaleKind;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WaveRole;

/**
 * A finale is a constant and a block (G0064): a fixed skeleton of offsets from its anchor and the rules that place
 * everything else round it (G0120), its waves each from mouths it has, flipped north-south and never east-west.
 */
class FinaleDefTest {

    @Test
    void bindsTheRedoubtFromItsBlock() {
        FinaleDef redoubt = SimConfig.defaults().finales().of(FinaleKind.REDOUBT);

        assertThat(redoubt.ground().widthMin())
                .isEqualTo(34)
                .isEqualTo(redoubt.ground().widthMax());
        assertThat(redoubt.ground().pieces()).isEmpty();
        assertThat(redoubt.hold().post()).isEqualTo(new Offset(0, 0));
        assertThat(redoubt.hold().watch()).isEqualTo(118f);
        assertThat(redoubt.hold().mouths()).contains(new Mouth("nw", -14, -13, 4, 4));
        assertThat(redoubt.rules().turrets().max()).isEqualTo(4);
        assertThat(redoubt.rules().mines().max()).isBetween(2, 8);
        assertThat(redoubt.rules().pickupKinds())
                .extracting(FinaleRules.Pick::kind)
                .containsExactly(PickupKind.CRATE, PickupKind.ARMOURY_FLAMER);
        assertThat(redoubt.rules().shapes())
                .contains(new FinaleRules.CoverShape.Bar(3, 1), new FinaleRules.CoverShape.Ell(3, 2));
        assertThat(redoubt.rules().inReach()).isFalse();
        assertThat(redoubt.waves()).first().isEqualTo(new WaveLine(6f, WaveRole.FODDER, "nw"));
        assertThat(redoubt.roles().of(WaveRole.FODDER)).contains(new WaveLine.Member(UnitKind.GRUNT, 9, 11));
        for (WaveRole role : WaveRole.values()) {
            assertThat(redoubt.roles().of(role)).as("%s", role).hasSizeGreaterThan(1);
        }
        assertThat(redoubt.lair()).isEmpty();
    }

    /** Its cover is ground once dealt (G0120): its pieces added to the ground's, nothing else of it moved. */
    @Test
    void coversAFinaleWithTheRockItsRulesDealt() {
        FinaleDef redoubt = SimConfig.defaults().finales().of(FinaleKind.REDOUBT);
        List<TileRect> cover = List.of(new TileRect(5, -6, 3, 1), new TileRect(5, 6, 3, 1));

        FinaleDef covered = redoubt.covered(cover);

        assertThat(covered.ground().pieces()).containsExactlyElementsOf(cover);
        assertThat(covered.hold()).isEqualTo(redoubt.hold());
        assertThat(covered.rules()).isEqualTo(redoubt.rules());
    }

    /** "Turret counts dealt" is ruled out (G0120): a finale's guns are its backbone. */
    @Test
    void refusesTurretsThatAreDealt() {
        assertThatThrownBy(() -> rules(new FinaleRules.Lot(2, 4, 3, 8, -8, 8), lot(0), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("turrets are fixed");
    }

    /** Everything comes in mirrored pairs (G0120), so a lot's ends are even. */
    @Test
    void refusesALotThatIsNotDealtInPairs() {
        assertThatThrownBy(() -> new FinaleRules.Lot(3, 4, 3, 8, -8, 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mirrored pairs");
    }

    /** One of each pickup it names: the hold's crate and cache are not dealt. */
    @Test
    void refusesPickupsThatAreNotOneOfEachKindNamed() {
        assertThatThrownBy(() -> rules(lot(4), lot(4), List.of(new FinaleRules.Pick(PickupKind.CRATE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one of each kind named");
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
        assertThat(new WaveLine(5f, WaveRole.FODDER, "nw+se").mouthNames()).containsExactly("nw", "se");
        assertThat(new WaveLine(5f, WaveRole.FODDER, "nw").mouthNames()).containsExactly("nw");
    }

    @Test
    void refusesAWaveThatNamesAMouthTwice() {
        assertThatThrownBy(() -> new WaveLine(5f, WaveRole.FODDER, "nw+nw"))
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
                            rules(),
                            List.of(new WaveLine(5f, WaveRole.FODDER, mouths)),
                            roles(),
                            List.of(),
                            FinaleDef.Pool.off()))
                    .as(mouths)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("north, a mouth the hold lacks");
        }
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
                Corridors.off(),
                Outcrops.off(),
                Clearings.off(),
                Ice.off(),
                List.of());

        assertThatThrownBy(() ->
                        new FinaleDef(dealt, hold(), rules(), List.of(), roles(), List.of(), FinaleDef.Pool.off()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("width is fixed");
    }

    /**
     * The keep is the redoubt's ground with a lair instead of waves: holes and the boss at fixed spots (G0048, G0064),
     * two guns and its cover inside his barrage, and the lair's caches placed by its rules (G0120).
     */
    @Test
    void bindsTheKeepFromItsBlock() {
        FinaleDef keep = SimConfig.defaults().finales().of(FinaleKind.KEEP);

        assertThat(keep.ground().base()).isEqualTo(Terrain.ASH);
        assertThat(keep.hold().watch()).isEqualTo(118f);
        assertThat(keep.waves()).isEmpty();
        assertThat(keep.hold().mouths()).isEmpty();
        assertThat(keep.rules().turrets().max()).isEqualTo(2);
        assertThat(keep.rules().inReach()).isTrue();
        assertThat(keep.rules().barrels().east()).isLessThan(12);
        assertThat(keep.lair()).hasSize(7);
        assertThat(keep.lair().getFirst()).isEqualTo(new LairLine.Hole(13, -6));
        assertThat(keep.lair().stream().filter(line -> line instanceof LairLine.Hole))
                .hasSize(6);
        assertThat(keep.pool().strains()).hasSizeGreaterThanOrEqualTo(8);
        assertThat(keep.pool().most()).isEqualTo(2);
        assertThat(keep.rules().lairKinds())
                .hasSizeGreaterThan(keep.rules().lair().max());
        assertThat(keep.lair().getLast()).isEqualTo(new LairLine.Unit(UnitKind.BOSS_GRUNT, 13, 0));
    }

    /** The flip negates every dy of the skeleton and turns nothing east-west, so west stays the way in (G0064). */
    @Test
    void flipsEveryPositionNorthSouthAndNoneEastWest() {
        FinaleDef redoubt = SimConfig.defaults()
                .finales()
                .of(FinaleKind.REDOUBT)
                .covered(List.of(new TileRect(-9, -3, 2, 3), new TileRect(-1, -10, 3, 1)));
        FinaleDef flipped = redoubt.flipped();

        assertThat(flipped.ground().pieces()).containsExactly(new TileRect(-9, 1, 2, 3), new TileRect(-1, 10, 3, 1));
        assertThat(flipped.hold().mouth("nw")).isEqualTo(new Mouth("nw", -14, 10, 4, 4));
        assertThat(flipped.rules()).isEqualTo(redoubt.rules());
        assertThat(flipped.waves()).isEqualTo(redoubt.waves());
        assertThat(flipped.flipped()).isEqualTo(redoubt);
        FinaleDef keep = SimConfig.defaults().finales().of(FinaleKind.KEEP);
        assertThat(keep.flipped().lair()).contains(new LairLine.Hole(13, 6));
        assertThat(keep.flipped().flipped()).isEqualTo(keep);
    }

    /** A wave names a role its block fills (G0120): one it left empty is refused. */
    @Test
    void refusesAWaveOfARoleWithNoMembers() {
        Defs<WaveRole, List<WaveLine.Member>> none = Defs.<WaveRole, List<WaveLine.Member>>builder(WaveRole.class)
                .put(WaveRole.FODDER, List.of())
                .put(WaveRole.FAST, List.of())
                .put(WaveRole.RANGED, List.of())
                .put(WaveRole.HEAVY, List.of())
                .build();

        assertThatThrownBy(() -> new FinaleDef(
                        ground(List.of()),
                        hold(),
                        rules(),
                        List.of(new WaveLine(5f, WaveRole.HEAVY, "nw")),
                        none,
                        List.of(),
                        FinaleDef.Pool.off()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a role with no members");
    }

    /** No strain more than its pool's most (G0120): six holes from two strains, two each, cannot be dealt. */
    @Test
    void refusesALairOfMoreHolesThanItsPoolCanDeal() {
        List<LairLine> holes = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            holes.add(new LairLine.Hole(10, i - 3));
        }
        FinaleDef.Pool two = new FinaleDef.Pool(
                List.of(new FinaleDef.Pooled(UnitKind.GRUNT), new FinaleDef.Pooled(UnitKind.ANT)), 2);

        assertThatThrownBy(() -> new FinaleDef(ground(List.of()), hold(), rules(), List.of(), roles(), holes, two))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a pool that can deal them");
    }

    /** A wave counts like a roster line: drawn, then the tier's percent once (G0063), and no ramp. */
    @Test
    void countsAWaveThroughTheTiersPressureWithNoRamp() {
        MissionConfig mission = SimConfig.defaults().mission();
        WaveLine.Member line = new WaveLine.Member(UnitKind.GRUNT, 8, 8);

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
                Corridors.off(),
                Outcrops.off(),
                Clearings.off(),
                Ice.off(),
                pieces);
    }

    private static Hold hold() {
        return new Hold(new Offset(0, 0), 118f, List.of(new Mouth("nw", -14, -13, 4, 4)));
    }

    private static Defs<WaveRole, List<WaveLine.Member>> roles() {
        Defs.Builder<WaveRole, List<WaveLine.Member>> roles = Defs.builder(WaveRole.class);
        for (WaveRole role : WaveRole.values()) {
            roles.put(role, List.of(new WaveLine.Member(UnitKind.GRUNT, 1, 2)));
        }
        return roles.build();
    }

    private static FinaleRules rules() {
        return rules(
                lot(4),
                lot(2),
                List.of(new FinaleRules.Pick(PickupKind.CRATE), new FinaleRules.Pick(PickupKind.CRATE)));
    }

    private static FinaleRules rules(FinaleRules.Lot turrets, FinaleRules.Lot pickups, List<FinaleRules.Pick> kinds) {
        return new FinaleRules(
                1.5f,
                2f,
                List.of(new FinaleRules.CoverShape.Bar(3, 1)),
                lot(2),
                turrets,
                lot(4),
                lot(2),
                pickups,
                kinds,
                lot(0),
                List.of(),
                false);
    }

    private static FinaleRules.Lot lot(int count) {
        return new FinaleRules.Lot(count, count, 3, 8, -8, 8);
    }
}
