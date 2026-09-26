package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;

/**
 * The shared chance every occasional behaviour goes through. Two tests here are load-bearing.
 * {@code drawsNothingAtAllForABehaviourThatIsSwitchedOff}: a switched-off behaviour that still drew a
 * number would mean turning one off in a scenario silently reshuffled every other chance in the game -
 * reproducible, so nothing else would catch it. {@code neverLetsMoreThanTheLongestGapGoBy}: that bound
 * is the whole of what dealing a chance evenly buys over a coin tossed every tick.
 */
class OddsTest {

    private static final float TICK = 1f / 60f;

    /** Far more than one per second, so a tick's worth of it is still a certainty. */
    private static final float CERTAIN = 1000f;

    private static final float ONCE_A_SECOND = 1f;

    /** A thousand seconds: enough that a count is about the rate rather than about the seed. */
    private static final int TICKS = 60_000;

    private static final Chances EVEN = Chances.none();

    private static final Chances COIN = Chances.none().withLongestGap(0f);

    @ParameterizedTest(name = "{0}")
    @MethodSource("dealings")
    void takesAChanceThatIsCertainAndNeverTakesOneOfZero(Chances chances) {
        Rng rng = new Rng(1L);

        assertThat(Odds.spent(Odds.spend(rng, 0f, chances, CERTAIN, TICK))).isTrue();
        assertThat(Odds.spent(Odds.spend(rng, 0f, chances, 0f, TICK))).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dealings")
    void keepsToTheStatedRateHoweverItIsDealt(Chances chances) {
        int taken = timesTakenIn(chances, TICK);

        assertThat(taken).isCloseTo(1000, Offset.offset(50));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dealings")
    void readsTheNumberAsAChancePerSecondRatherThanPerTick(Chances chances) {
        int inShortTicks = timesTakenIn(chances, TICK);
        int inTicksTwiceAsLong = timesTakenIn(chances, TICK * 2f);

        assertThat(inTicksTwiceAsLong).isCloseTo(inShortTicks * 2, Offset.offset(inShortTicks / 10));
    }

    @Test
    void neverLetsMoreThanTheLongestGapGoBy() {
        float longest = longestGapIn(EVEN);

        assertThat(longest).isLessThanOrEqualTo(EVEN.longestGap() / ONCE_A_SECOND + TICK);
    }

    @Test
    void letsACoinTossedEveryTickGoFarLongerThanThat() {
        float longest = longestGapIn(COIN);

        assertThat(longest).isGreaterThan(EVEN.longestGap() / ONCE_A_SECOND * 2f);
    }

    @Test
    void banksNothingFromASpellAtAChanceFarAboveOnePerTick() {
        Rng rng = new Rng(7L);
        float due = 0f;
        for (int i = 0; i < 100; i++) {
            due = Odds.spend(rng, due, EVEN, CERTAIN, TICK);
        }
        int taken = 0;

        for (int i = 0; i < 10; i++) {
            due = Odds.spend(rng, due, EVEN, ONCE_A_SECOND, TICK);
            if (Odds.spent(due)) {
                taken++;
            }
        }

        assertThat(taken).isLessThanOrEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dealings")
    void drawsNothingAtAllForABehaviourThatIsSwitchedOff(Chances chances) {
        Rng rng = new Rng(1L);
        long before = rng.state();

        Odds.spend(rng, 0f, chances, 0f, TICK);

        assertThat(rng.state()).isEqualTo(before);
    }

    @Test
    void asksTheTableForTheNumberWhenItIsGivenOne() {
        Chances chances = Chances.none().with(AiChance.SWING_AT_A_LEAP, CERTAIN);

        assertThat(Odds.spent(Odds.spend(new Rng(1L), 0f, chances, AiChance.SWING_AT_A_LEAP, TICK)))
                .isTrue();
    }

    static Stream<Chances> dealings() {
        return Stream.of(EVEN, COIN);
    }

    /** How often a once-a-second chance is taken over {@link #TICKS} ticks of the given length. */
    private static int timesTakenIn(Chances chances, float deltaTime) {
        Rng rng = new Rng(7L);
        float due = 0f;
        int taken = 0;
        for (int i = 0; i < TICKS; i++) {
            due = Odds.spend(rng, due, chances, ONCE_A_SECOND, deltaTime);
            if (Odds.spent(due)) {
                taken++;
            }
        }
        return taken;
    }

    /** The longest stretch, in seconds, between two takings of a once-a-second chance. */
    private static float longestGapIn(Chances chances) {
        Rng rng = new Rng(7L);
        float due = 0f;
        int since = 0;
        int longest = 0;
        for (int i = 0; i < TICKS; i++) {
            since++;
            due = Odds.spend(rng, due, chances, ONCE_A_SECOND, TICK);
            if (Odds.spent(due)) {
                longest = Math.max(longest, since);
                since = 0;
            }
        }
        return longest * TICK;
    }
}
