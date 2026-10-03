package pl.mzebrows.pwt.client.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.client.LaunchOptions;

class LaunchOptionsTest {

    @Test
    void startsAsCopyZeroReadingEveryPad() {
        LaunchOptions options = LaunchOptions.none();

        assertThat(options.instance()).isZero();
        assertThat(options.instanceGiven()).isFalse();
        assertThat(options.pads().takes(3)).isTrue();
        assertThat(options.problems()).isEmpty();
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
