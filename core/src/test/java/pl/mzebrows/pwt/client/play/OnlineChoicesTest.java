package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.client.play.OnlineChoices.Row;

final class OnlineChoicesTest {

    private static void typeAll(OnlineChoices choices, Row row, String text) {
        for (int i = 0; i < text.length(); i++) {
            choices.type(row, text.charAt(i));
        }
    }

    @Test
    void startsFromWhatItWasGiven() {
        OnlineChoices choices = new OnlineChoices("k3mx", "relay.example.org", "24741");

        assertThat(choices.nickname()).isEqualTo("k3mx");
        assertThat(choices.host()).isEqualTo("relay.example.org");
        assertThat(choices.port()).isEqualTo("24741");
    }

    @Test
    void anEmptyRelayStaysEmpty() {
        OnlineChoices choices = new OnlineChoices("k3mx", "", "");

        assertThat(choices.host()).isEmpty();
        assertThat(choices.port()).isNull();
    }

    @Test
    void aNicknameIsTypedInTheMachineIdsAlphabet() {
        OnlineChoices choices = new OnlineChoices("k3mx", "", "");
        for (int i = 0; i < 4; i++) {
            choices.type(Row.NICKNAME, '\b');
        }

        typeAll(choices, Row.NICKNAME, "Mat_eusz 7!");

        assertThat(choices.nickname()).isEqualTo("mateusz7");
    }

    @Test
    void aNicknameStopsAtSixteenCharacters() {
        OnlineChoices choices = new OnlineChoices("", "", "");

        typeAll(choices, Row.NICKNAME, "abcdefghijklmnopqrstuvwxyz");

        assertThat(choices.text(Row.NICKNAME)).isEqualTo("abcdefghijklmnop");
    }

    @Test
    void anEmptyNicknameIsTheMachinesOwn() {
        OnlineChoices choices = new OnlineChoices("k3mx", "", "");
        for (int i = 0; i < 4; i++) {
            choices.type(Row.NICKNAME, '\b');
        }

        assertThat(choices.text(Row.NICKNAME)).isEmpty();
        assertThat(choices.nickname()).isEqualTo("k3mx");
    }

    @Test
    void thePortTakesDigitsOnlyAndOnlyAPortCounts() {
        OnlineChoices choices = new OnlineChoices("k3mx", "", "");

        typeAll(choices, Row.SERVER_PORT, "9x9999");
        assertThat(choices.text(Row.SERVER_PORT)).isEqualTo("99999");
        assertThat(choices.port()).isNull();

        choices.replace(Row.SERVER_PORT, "0");
        assertThat(choices.port()).isNull();
        choices.replace(Row.SERVER_PORT, "65535");
        assertThat(choices.port()).isEqualTo("65535");
    }

    @Test
    void theServerLineTakesAHostNameOrAnAddress() {
        OnlineChoices choices = new OnlineChoices("k3mx", "", "");

        typeAll(choices, Row.SERVER_IP, "Relay-1.example.org:9 /");

        assertThat(choices.host()).isEqualTo("Relay-1.example.org9");
    }

    @Test
    void typingSaysWhetherALineChanged() {
        OnlineChoices choices = new OnlineChoices("k3mx", "", "");

        assertThat(choices.type(Row.SERVER_PORT, 'x')).isFalse();
        assertThat(choices.type(Row.SERVER_PORT, '7')).isTrue();
    }
}
