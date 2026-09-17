package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;

/**
 * The shared roll every occasional behaviour goes through. The third test here is the load-bearing
 * one: a switched-off behaviour that still drew a number would mean turning one off in a scenario
 * silently reshuffled every other roll in the game - reproducible, so nothing else would catch it.
 */
class OddsTest {

    private static final float TICK = 1f / 60f;

    /** Far more than one per second, so a tick's worth of it is still a certainty. */
    private static final float CERTAIN = 1000f;

    /** Enough rolls that the two counts below are about the ratio rather than about the seed. */
    private static final int TICKS = 6000;

    @Test
    void takesAChanceThatIsCertainAndNeverTakesOneOfZero() {
        Rng rng = new Rng(1L);

        assertThat(Odds.rolls(rng, CERTAIN, TICK)).isTrue();
        assertThat(Odds.rolls(rng, 0f, TICK)).isFalse();
    }

    @Test
    void readsTheNumberAsAChancePerSecondRatherThanPerTick() {
        int inShortTicks = timesTakenIn(TICK);
        int inTicksTwiceAsLong = timesTakenIn(TICK * 2f);

        assertThat(inTicksTwiceAsLong).isCloseTo(inShortTicks * 2, Offset.offset(inShortTicks / 4));
    }

    @Test
    void drawsNothingAtAllForABehaviourThatIsSwitchedOff() {
        Rng rng = new Rng(1L);
        long before = rng.state();

        Odds.rolls(rng, 0f, TICK);

        assertThat(rng.state()).isEqualTo(before);
    }

    @Test
    void asksTheTableForTheNumberWhenItIsGivenOne() {
        Chances chances = Chances.standard().with(AiChance.SWING_AT_A_LEAP, CERTAIN);

        assertThat(Odds.rolls(new Rng(1L), chances, AiChance.SWING_AT_A_LEAP, TICK))
                .isTrue();
    }

    /** How often a once-a-second chance is taken over {@link #TICKS} ticks of the given length. */
    private static int timesTakenIn(float deltaTime) {
        Rng rng = new Rng(7L);
        int taken = 0;
        for (int i = 0; i < TICKS; i++) {
            if (Odds.rolls(rng, 1f, deltaTime)) {
                taken++;
            }
        }
        return taken;
    }
}
