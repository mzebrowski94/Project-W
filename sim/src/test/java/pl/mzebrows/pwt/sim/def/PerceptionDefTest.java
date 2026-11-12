package pl.mzebrows.pwt.sim.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The one rule in here is not a formality. A lose range shorter than the detection range means a unit
 * standing at either distance notices, steps, loses, stops and notices again, every tick - and the
 * distance it happens at is exactly where a player edging forward stands.
 */
class PerceptionDefTest {

    @Test
    void refusesALoseRangeShorterThanTheDetectionRange() {
        assertThatThrownBy(() -> PerceptionDef.allRound(false, true, 30f, 20f, 0f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsTheTwoRangesToBeEqual() {
        PerceptionDef.allRound(false, true, 30f, 30f, 0f);
    }

    @Test
    void refusesAnArcThatIsNotAnArc() {
        assertThatThrownBy(() -> new PerceptionDef(false, true, 30f, 30f, 0f, 0f, false, 0f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerceptionDef(false, true, 30f, 30f, 0f, 400f, false, 0f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** An all-round range past the detection range would leave the arc nothing to decide (pillar 5). */
    @Test
    void refusesAnAllRoundRangePastWhereItNoticesAnything() {
        assertThatThrownBy(() -> new PerceptionDef(false, true, 30f, 30f, 0f, 90f, false, 31f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** All round has to be the cosine every direction beats, or an arc would blind a strain by default. */
    @Test
    void noticesEveryDirectionWhenItLooksAllRound() {
        assertThat(PerceptionDef.allRound(false, true, 30f, 30f, 0f).arcCosine())
                .isEqualTo(-1f);
    }

    @Test
    void turnsAnArcIntoTheCosineOfItsHalfAngle() {
        assertThat(new PerceptionDef(false, true, 30f, 30f, 0f, 180f, false, 0f).arcCosine())
                .isCloseTo(0f, org.assertj.core.data.Offset.offset(1e-6f));
    }
}
