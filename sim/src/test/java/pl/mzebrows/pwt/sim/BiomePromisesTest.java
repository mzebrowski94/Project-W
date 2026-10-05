package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.map.Patch;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.def.BiomeKind;

/**
 * What the design says of the five biomes in a number (missions ticket 03), held against the shipped blocks: each a
 * place, told apart by its ground. Connectivity and placement are the sweeps'; a biome's numbers are its block's.
 */
class BiomePromisesTest {

    private static final SimConfig SHIPPED = SimConfig.defaults();

    /** "Open, pebbles of rock." */
    @Test
    void thePlainIsTheLeastRockyBiome() {
        for (BiomeKind kind : BiomeKind.values()) {
            if (kind != BiomeKind.PLAIN) {
                assertThat(rock(BiomeKind.PLAIN))
                        .as("the plain against %s", kind)
                        .isLessThan(rock(kind));
            }
        }
    }

    /** "Crags at both ends of their range": mounds round the anthill's holes, masses in the badlands. */
    @Test
    void cragsRiseFromMoundsInTheAnthillToMassesInTheBadlands() {
        for (BiomeKind kind : BiomeKind.values()) {
            boolean crags = ground(kind).crags().density() > 0f;
            assertThat(crags).as("%s's crags", kind).isEqualTo(kind == BiomeKind.ANTHILL || kind == BiomeKind.BADLANDS);
        }
        assertThat(ground(BiomeKind.ANTHILL).crags().density())
                .isLessThan(ground(BiomeKind.BADLANDS).crags().density());
        for (BiomeKind kind : BiomeKind.values()) {
            assertThat(rock(kind)).as("%s against the badlands", kind).isLessThanOrEqualTo(rock(BiomeKind.BADLANDS));
        }
    }

    /** "Undergrowth over a fifth of it": the brake is the one biome grown over. */
    @Test
    void theBrakeIsTheOneBiomeOvergrownWithThicket() {
        for (BiomeKind kind : BiomeKind.values()) {
            float thicket = 0f;
            for (Patch patch : ground(kind).patches()) {
                if (patch.ground().terrain() == Terrain.THICKET) {
                    thicket += patch.density();
                }
            }
            if (kind == BiomeKind.BRAKE) {
                assertThat(thicket).as("the brake's thicket").isGreaterThanOrEqualTo(0.18f);
            } else {
                assertThat(thicket).as("%s's thicket", kind).isZero();
            }
        }
    }

    /** "A quarry with a base in it" (ticket 12): the one biome certain of a house. */
    @Test
    void theQuarryIsTheOneBiomeCertainOfAHouse() {
        for (BiomeKind kind : BiomeKind.values()) {
            assertThat(ground(kind).houses().min() > 0).as("%s's houses", kind).isEqualTo(kind == BiomeKind.QUARRY);
        }
    }

    private static BiomeGround ground(BiomeKind kind) {
        return SHIPPED.biomes().of(kind).ground();
    }

    private static float rock(BiomeKind kind) {
        return ground(kind).crags().density() + ground(kind).outcrops().density();
    }
}
