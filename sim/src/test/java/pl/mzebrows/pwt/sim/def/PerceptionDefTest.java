package pl.mzebrows.pwt.sim.def;

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
        assertThatThrownBy(() -> new PerceptionDef(false, true, 30f, 20f, 0f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsTheTwoRangesToBeEqual() {
        new PerceptionDef(false, true, 30f, 30f, 0f);
    }
}
