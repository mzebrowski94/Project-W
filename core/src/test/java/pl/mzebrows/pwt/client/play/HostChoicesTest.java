package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What HOST GAME decides: where, and the password (G0101, G0103). */
class HostChoicesTest {

    @Test
    void opensOnThisNetworkUntilTheInternetIsChosen() {
        HostChoices choices = new HostChoices(true, "");

        assertThat(choices.where()).isEqualTo(HostChoices.Where.THIS_NETWORK);
        assertThat(choices.internet()).isFalse();
        assertThat(choices.passwordEnabled()).isFalse();

        choices.step();

        assertThat(choices.internet()).isTrue();
        assertThat(choices.passwordEnabled()).isTrue();

        choices.step();

        assertThat(choices.where()).isEqualTo(HostChoices.Where.THIS_NETWORK);
    }

    @Test
    void cannotPickTheInternetWhileNoRelayIsSet() {
        HostChoices choices = new HostChoices(false, "KJ34");

        choices.step();
        choices.step();

        assertThat(choices.relaySet()).isFalse();
        assertThat(choices.internet()).isFalse();
        choices.type('A');
        assertThat(choices.typedPassword())
                .as("a line that is not live takes nothing")
                .isEqualTo("KJ34");
    }

    @Test
    void readsAPasswordTheForgivingWayAndHoldsFourCharacters() {
        HostChoices choices = new HostChoices(true, "");
        choices.step();

        for (char c : "kj-3 4xo5".toCharArray()) {
            choices.type(c);
        }

        // Separators are dropped, the fourth character closes the line, and what follows it is ignored.
        assertThat(choices.typedPassword()).isEqualTo("KJ34");
        assertThat(choices.password()).isEqualTo("KJ34");
        choices.type('\b');
        assertThat(choices.typedPassword()).isEqualTo("KJ3");
    }

    @Test
    void takesBlankAsNoPasswordAndHalfAPasswordAsAnError() {
        HostChoices choices = new HostChoices(true, "");
        choices.step();

        assertThat(choices.password()).isEmpty();

        choices.type('K');

        assertThat(choices.password()).isNull();
        assertThat(choices.problem()).contains("four characters");
        choices.type('J');
        choices.type('3');
        choices.type('4');
        assertThat(choices.password()).isEqualTo("KJ34");
        assertThat(choices.problem()).isEmpty();
    }

    @Test
    void showsTheRememberedPasswordAndTakesAPickedOne() {
        HostChoices choices = new HostChoices(true, "ac de");
        choices.step();

        assertThat(choices.typedPassword()).isEqualTo("ACDE");
        choices.replacePassword("KJ34");
        assertThat(choices.password()).isEqualTo("KJ34");
        choices.replacePassword("");
        assertThat(choices.password()).isEmpty();
    }
}
