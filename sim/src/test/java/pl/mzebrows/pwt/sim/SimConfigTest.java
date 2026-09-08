package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The one number here that fails silently. Everything else in {@code SimConfig} is validated at
 * construction or is visible the moment it is wrong; the grid's cell size is neither.
 */
class SimConfigTest {

    private static final float HUGE = 40f;

    /**
     * A strain no scenario spawns is still reachable by evolving, and a collision cell too small for
     * a grown body stops the grid reporting some overlapping pairs at all. Units then walk through
     * each other, with nothing else failing.
     */
    @Test
    void measuresTheLargestBodyEvenWhenOnlyEvolvingCanReachIt() {
        SimConfig config = SimConfig.defaults()
                .withUnit(UnitKind.BRUTE, brute -> brute.withRadius(HUGE).withCount(0));

        assertThat(config.largestRadius()).isEqualTo(HUGE);
    }

    @Test
    void variesOneStrainWithoutDisturbingTheRest() {
        SimConfig config = SimConfig.defaults();

        SimConfig changed = config.withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(1));

        assertThat(changed.unit(UnitKind.GRUNT).count()).isEqualTo(1);
        assertThat(changed.unit(UnitKind.BRUTE)).isEqualTo(config.unit(UnitKind.BRUTE));
    }
}
