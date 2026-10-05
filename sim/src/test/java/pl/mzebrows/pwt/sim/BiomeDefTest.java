package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Houses;
import pl.mzebrows.pwt.map.Patch;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/** A biome is a constant and a block (missions ticket 03): counts, never places, and never a gun's cache. */
class BiomeDefTest {

    private static final int SEEDS = 400;

    @Test
    void bindsEveryBiomeInThePoolFromItsBlock() {
        SimConfig shipped = SimConfig.defaults();

        for (BiomeKind kind : BiomeKind.values()) {
            BiomeDef biome = shipped.biomes().of(kind);
            assertThat(biome.roster()).as("%s's roster", kind).isNotEmpty();
            assertThat(biome.furniture().pointsMin()).as("%s's points", kind).isGreaterThanOrEqualTo(1);
        }
        BiomeDef quarry = shipped.biomes().of(BiomeKind.QUARRY);
        assertThat(quarry.ground().houses()).isEqualTo(new Houses(1, 2));
        assertThat(quarry.roster()).contains(new Roster.Nest(UnitKind.BRUTE, 1, 1));
        assertThat(quarry.furniture().pickups()).contains(new Stock.Pickup(PickupKind.CRATE, 4, 5));
        assertThat(shipped.biomes().of(BiomeKind.BRAKE).ground().patches())
                .anySatisfy(patch -> assertThat(patch).isInstanceOf(Patch.Blob.class));
        assertThat(shipped.biomes().of(BiomeKind.ANTHILL).ground().base()).isEqualTo(Terrain.ORGANIC);
    }

    @Test
    void dealsARosterCountBetweenItsFewestAndMostAtThePressureOfTheFirstBiomeOnMedium() {
        Roster line = new Roster.Loose(UnitKind.GRUNT, 2, 5);
        MissionConfig.Pressure written = new MissionConfig.Pressure(100, 100);
        boolean[] seen = new boolean[6];

        for (long seed = 0; seed < SEEDS; seed++) {
            seen[line.count(new Rng(seed), written)] = true;
        }

        assertThat(seen).containsExactly(false, false, true, true, true, true);
    }

    /** Both percents meet the drawn count once (G0063): four grunts at half again are six. */
    @Test
    void scalesTheDrawnCountByThePressureOnce() {
        Roster line = new Roster.Nest(UnitKind.ANT, 4, 4);

        assertThat(line.count(new Rng(1L), new MissionConfig.Pressure(125, 120)))
                .isEqualTo(6);
    }

    @Test
    void refusesARosterLineThatRunsBackwards() {
        assertThatThrownBy(() -> new Roster.Loose(UnitKind.GRUNT, 5, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loose GRUNT");
    }

    /** A gun lying in a biome is a finale's (missions ticket 01); the pickup's own grant says it is one. */
    @Test
    void refusesABiomeThatStocksAGunsCache() {
        SimConfig shipped = SimConfig.defaults();

        assertThatThrownBy(() -> shipped.withBiomes(shipped.biomes()
                        .map(
                                BiomeKind.PLAIN,
                                plain -> new BiomeDef(
                                        plain.ground(),
                                        plain.roster(),
                                        stocking(plain.furniture(), PickupKind.ARMOURY_FLAMER)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gun's cache");
    }

    @Test
    void refusesAMissionThatDealsMoreBiomesThanThePoolHolds() {
        SimConfig shipped = SimConfig.defaults();

        assertThatThrownBy(() -> shipped.withMission(shipped.mission().withBiomes(BiomeKind.values().length + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from a pool of");
    }

    private static Furniture stocking(Furniture furniture, PickupKind kind) {
        return new Furniture(
                furniture.pointsMin(),
                furniture.pointsMax(),
                furniture.barrelsMin(),
                furniture.barrelsMax(),
                furniture.rabbitsMin(),
                furniture.rabbitsMax(),
                furniture.bouldersMin(),
                furniture.bouldersMax(),
                List.of(new Stock.Pickup(kind, 1, 1)));
    }
}
