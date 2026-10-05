package pl.mzebrows.pwt.client.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.client.LaunchOptions;
import pl.mzebrows.pwt.client.play.MissionCode;
import pl.mzebrows.pwt.sim.def.TierKind;

class LaunchOptionsTest {

    private static final MissionCode MISSION = new MissionCode(TierKind.HARD, 31_337L);

    @Test
    void startsAsCopyZeroReadingEveryPad() {
        LaunchOptions options = LaunchOptions.none();

        assertThat(options.instance()).isZero();
        assertThat(options.instanceGiven()).isFalse();
        assertThat(options.pads().takes(3)).isTrue();
        assertThat(options.mission()).isNull();
        assertThat(options.problems()).isEmpty();
    }

    @Test
    void readsTheMissionItWasStartedWithHoweverItIsTyped() {
        LaunchOptions options =
                LaunchOptions.parse("--mission=" + MISSION.toString().toLowerCase(Locale.ROOT));

        assertThat(options.mission()).isEqualTo(MISSION);
        assertThat(options.problems()).isEmpty();
    }

    /** A typo is refused rather than played as some other mission (G0065). */
    @Test
    void refusesAMissionCodeThatFailsItsCheck() {
        String text = MISSION.text();
        String typo = text.substring(0, 2) + (text.charAt(2) == 'A' ? 'C' : 'A') + text.substring(3);

        LaunchOptions options = LaunchOptions.parse("--mission=" + typo);

        assertThat(options.mission()).isNull();
        assertThat(options.problems()).singleElement().asString().startsWith("ignored --mission=");
    }

    @Test
    void givesASecondCopyNoPadsUnlessToldOtherwise() {
        assertThat(LaunchOptions.parse("--instance=1").pads().takes(0)).isFalse();
        assertThat(LaunchOptions.parse("--instance=1", "--pads=0,2").pads().takes(2))
                .isTrue();
        assertThat(LaunchOptions.parse("--instance=1", "--pads=0,2").pads().takes(1))
                .isFalse();
        assertThat(LaunchOptions.parse("--pads=none").pads().takes(0)).isFalse();
    }

    @Test
    void reportsWhatItCannotReadAndCarriesOn() {
        LaunchOptions options =
                LaunchOptions.parse("--instance=eleven", "--pads=some", "--join=25.1.2.3", "--instance=2");

        assertThat(options.instance()).isEqualTo(2);
        assertThat(options.problems()).hasSize(3).allMatch(problem -> problem.startsWith("ignored "));
    }

    @Test
    void readsOnlyThePadsItWasGiven() {
        FakeGamepads four = new FakeGamepads();
        four.pluggedIn(pad("pad0"), pad("pad1"), pad("pad2"), pad("pad3"));

        ChosenGamepads chosen = new ChosenGamepads(four, PadPolicy.parse("1,3"));

        assertThat(chosen.count()).isEqualTo(2);
        assertThat(List.of(chosen.get(0).id(), chosen.get(1).id())).containsExactly("pad1", "pad3");
        assertThat(new ChosenGamepads(four, PadPolicy.NONE).count()).isZero();
    }

    private static Gamepad pad(String id) {
        return new FakeGamepad(id, "Xbox Wireless Controller");
    }
}
