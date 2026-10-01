package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class MachineIdTest {

    @Test
    void keepsWhatAMachineKeptAndMakesOneForAMachineThatKeptNone() {
        SplittableRandom random = new SplittableRandom(5L);

        assertThat(MachineId.keptOrNew("k7qd", random)).isEqualTo("k7qd");
        assertThat(MachineId.valid(MachineId.keptOrNew(null, random))).isTrue();
        assertThat(MachineId.keptOrNew("Not One", random))
                .isNotEqualTo("Not One")
                .matches(MachineId::valid);
    }

    @Test
    void makesNoTwoAlikeInAPlaytestsWorth() {
        SplittableRandom random = new SplittableRandom(7L);

        assertThat(MachineId.make(random)).isNotEqualTo(MachineId.make(random));
    }

    /** It goes into a log as it came, so nothing that could end a field or a line passes. */
    @Test
    void refusesWhatCouldNotStandInALogAsItCame() {
        assertThat(MachineId.valid("pc-2")).isTrue();
        assertThat(MachineId.valid("")).isFalse();
        assertThat(MachineId.valid("k7qd event=left")).isFalse();
        assertThat(MachineId.valid("k7qd\nt=")).isFalse();
        assertThat(MachineId.valid("K7QD")).isFalse();
        assertThat(MachineId.valid("a".repeat(MachineId.MAX_LENGTH + 1))).isFalse();
    }
}
