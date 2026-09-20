package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The numbers here that fail silently. Everything else in {@code SimConfig} is validated at
 * construction or is visible the moment it is wrong; the grid's cell size and the clinch carry are
 * neither - one lets units walk through each other, the other leaves a freeze playing across a gap.
 */
class SimConfigTest {

    private static final float HUGE = 40f;

    /** Brings the carry to a halt almost instantly, well short of the sweep's own reach. */
    private static final float HUGE_DECAY = 100_000f;

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

    /**
     * <strong>Breaking a frog's aggro has to actually work.</strong> "It gives up easily" is a fact
     * about two numbers and nothing else - how far past its detection range it will keep hunting you
     * - and there is no system anywhere that would notice if somebody widened the gap to match the
     * rest of the table while tuning. The mechanic would simply stop existing.
     */
    @Test
    void letsGoOfHimSoonerThanAnythingElseOnTheFieldDoes() {
        SimConfig config = SimConfig.defaults();

        float frog = leeway(config, UnitKind.FROG);

        for (UnitKind kind : UnitKind.values()) {
            if (kind != UnitKind.FROG) {
                assertThat(frog)
                        .as("a frog must give up sooner than a %s", kind)
                        .isLessThan(leeway(config, kind));
            }
        }
    }

    /** How much further than the range it noticed you at a strain will keep hunting you. */
    private static float leeway(SimConfig config, UnitKind kind) {
        PerceptionDef perception = config.unit(kind).perception();
        return perception.loseRange() - perception.detectionRange();
    }

    @Test
    void variesOneStrainWithoutDisturbingTheRest() {
        SimConfig config = SimConfig.defaults();

        SimConfig changed = config.withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(1));

        assertThat(changed.unit(UnitKind.GRUNT).count()).isEqualTo(1);
        assertThat(changed.unit(UnitKind.BRUTE)).isEqualTo(config.unit(UnitKind.BRUTE));
    }

    /**
     * A carry shorter than the stride a sweep can leave means the clinch stops short of the commander
     * and the freeze plays across the gap it exists to close. Nothing downstream can notice: the
     * numbers are legal, the systems all work, and it only ever shows up as the beat looking wrong.
     */
    @Test
    void refusesAClinchCarryThatWouldStopShortOfTheCommander() {
        SimConfig config = SimConfig.defaults();

        assertThatThrownBy(() -> config.withMelee(config.melee().withClinchCarryDecay(HUGE_DECAY)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsACarryThatIsNeverStoppedAtAll() {
        SimConfig config = SimConfig.defaults();

        assertThat(config.withMelee(config.melee().withClinchCarryDecay(0f))
                        .melee()
                        .carryDistance())
                .isEqualTo(Float.MAX_VALUE);
    }
}
