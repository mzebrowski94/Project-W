package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.lan.LanAnnouncement;
import pl.mzebrows.pwt.net.lan.LanListener;
import pl.mzebrows.pwt.net.lan.RoomCode;

class JoinChoicesTest {

    private static final int PORT = 47_100;

    private final JoinChoices choices = new JoinChoices("ours");
    private final LanListener.Heard skirmish = heard(1L, "ours", "192.168.1.5", 47_100);
    private final LanListener.Heard defend = heard(2L, "ours", "25.1.2.3", 47_101);

    @Test
    void joinsTheHighlightedSessionWhenNothingIsTyped() {
        choices.heard(List.of(skirmish, defend));
        choices.down();

        assertThat(choices.choose(PORT)).isEqualTo(target("25.1.2.3", 47_101, defend));
    }

    @Test
    void joinsTheSessionPickedOnTheList() {
        choices.heard(List.of(skirmish, defend));

        choices.select(1);

        assertThat(choices.choose(PORT)).isEqualTo(target("25.1.2.3", 47_101, defend));
    }

    @Test
    void joinsTheSessionWhoseRoomCodeWasPickedOnAPad() {
        choices.heard(List.of(skirmish, defend));
        typeIn("zz");

        choices.replaceTyped(defend.announcement().code().text());

        assertThat(choices.typed()).isEqualTo(defend.announcement().code().text());
        assertThat(choices.choose(PORT)).isEqualTo(target("25.1.2.3", 47_101, defend));
    }

    @Test
    void joinsTheSessionWhoseRoomCodeIsTypedHoweverItIsTyped() {
        choices.heard(List.of(skirmish, defend));
        typeIn(defend.announcement().code().text().toLowerCase(java.util.Locale.ROOT));

        assertThat(choices.choose(PORT)).isEqualTo(target("25.1.2.3", 47_101, defend));
    }

    @Test
    void takesAnythingElseTypedForAnAddress() {
        typeIn("25.9.8.7");
        assertThat(choices.choose(PORT)).isEqualTo(new JoinChoices.Target("25.9.8.7", PORT, "25.9.8.7:" + PORT));

        typeIn(":47105");
        assertThat(choices.choose(PORT)).isEqualTo(new JoinChoices.Target("25.9.8.7", 47_105, "25.9.8.7:47105"));
    }

    @Test
    void saysWhyItCannotJoin() {
        assertThat(choices.choose(PORT)).isNull();
        assertThat(choices.problem()).contains("nothing heard");

        typeIn(RoomCode.of(77L).text());
        assertThat(choices.choose(PORT)).isNull();
        assertThat(choices.problem()).contains("no session with room");

        choices.type('\b');
        typeIn(":x");
        assertThat(choices.choose(PORT)).isNull();
        assertThat(choices.problem()).contains("number");
    }

    @Test
    void refusesASessionOfAnotherBuildHere() {
        LanListener.Heard stranger = heard(3L, "theirs", "192.168.1.9", 47_100);
        choices.heard(List.of(stranger));

        assertThat(choices.joinable(stranger)).isFalse();
        assertThat(choices.choose(PORT)).isNull();
        assertThat(choices.problem()).contains("theirs").contains("ours");
    }

    @Test
    void keepsTheHighlightOnTheListAsItShrinks() {
        choices.heard(List.of(skirmish, defend));
        choices.down();
        choices.heard(List.of(skirmish));

        assertThat(choices.selected()).isZero();
        choices.up();
        assertThat(choices.selected()).isZero();
    }

    @Test
    void keepsOnlyWhatAnAddressCanHoldAndDeletesWithBackspace() {
        typeIn("a b!c");
        choices.type('\b');

        assertThat(choices.typed()).isEqualTo("ab");
        for (int i = 0; i < 100; i++) {
            choices.type('x');
        }
        assertThat(choices.typed()).hasSize(JoinChoices.MAX_TYPED);
    }

    private static final JoinChoices.Relay RELAY = new JoinChoices.Relay("relay.example.org", 24_741);
    private final JoinChoices viaRelay = new JoinChoices("ours", RELAY);

    @Test
    void aCodeHeardOnTheNetworkWinsOverTheRelay() {
        viaRelay.heard(List.of(skirmish));
        typeVia(skirmish.announcement().code().text());

        assertThat(viaRelay.choose(PORT)).isEqualTo(target("192.168.1.5", 47_100, skirmish));
    }

    @Test
    void anyOtherCodeGoesToTheRelayWithNoPasswordTheFirstTime() {
        viaRelay.heard(List.of(skirmish));
        typeVia("kj3x");

        JoinChoices.Target target = viaRelay.choose(PORT);

        assertThat(target)
                .isEqualTo(
                        new JoinChoices.Target("relay.example.org", 24_741, "room KJ3X through the relay", "KJ3X", ""));
        assertThat(target.relayed()).isTrue();
    }

    @Test
    void aCodeHeardNowhereGoesNowhereWhenNoRelayIsSet() {
        typeIn("kj3x");

        assertThat(choices.choose(PORT)).isNull();
        assertThat(choices.problem()).contains("no session with room KJ3X heard on this network");
    }

    @Test
    void anAddressStaysAnAddressWhateverTheRelay() {
        typeVia("25.9.8.7");

        assertThat(viaRelay.choose(PORT)).isEqualTo(new JoinChoices.Target("25.9.8.7", PORT, "25.9.8.7:" + PORT));
    }

    @Test
    void aRoomHeardOnTheNetworkJoinsThroughTheRelayWithThePasswordItAnnounced() {
        LanListener.Heard roomed = roomed(3L, "KJ3X", "4TYU");
        viaRelay.heard(List.of(roomed));

        JoinChoices.Target target = viaRelay.choose(PORT);

        assertThat(target)
                .isEqualTo(new JoinChoices.Target(
                        "relay.example.org", 24_741, "room KJ3X through the relay", "KJ3X", "4TYU"));
        typeVia("kj3x");
        assertThat(viaRelay.choose(PORT)).as("typed, it is the same room").isEqualTo(target);
    }

    @Test
    void aRoomHeardOnTheNetworkCannotBeJoinedWhereNoRelayIsSet() {
        choices.heard(List.of(roomed(3L, "KJ3X", "")));

        assertThat(choices.choose(PORT)).isNull();
        assertThat(choices.problem()).contains("room KJ3X is on the relay").contains("no relay is set");
    }

    @Test
    void asksForAPasswordOnceTheRelayRefusesForWantOfOne() {
        typeVia("kj3x");
        viaRelay.askPassword("KJ3X");

        assertThat(viaRelay.asking()).isEqualTo("KJ3X");
        assertThat(viaRelay.choose(PORT)).as("nothing typed yet").isNull();
        assertThat(viaRelay.problem()).contains("four characters");

        typeVia("4t-yu");

        assertThat(viaRelay.typedPassword()).isEqualTo("4TYU");
        assertThat(viaRelay.typed()).as("the code line is left as it was").isEqualTo("kj3x");
        assertThat(viaRelay.choose(PORT))
                .isEqualTo(new JoinChoices.Target(
                        "relay.example.org", 24_741, "room KJ3X through the relay", "KJ3X", "4TYU"));
    }

    @Test
    void takesAPasswordPickedOnAPadAndLetsItGoWithBack() {
        viaRelay.askPassword("KJ3X");

        viaRelay.replaceTyped("ACDE");

        assertThat(viaRelay.typedPassword()).isEqualTo("ACDE");
        viaRelay.cancelPassword();
        assertThat(viaRelay.asking()).isNull();
        assertThat(viaRelay.typedPassword()).isEmpty();
    }

    private void typeVia(String text) {
        for (int i = 0; i < text.length(); i++) {
            viaRelay.type(text.charAt(i));
        }
    }

    private static LanListener.Heard roomed(long session, String code, String password) {
        return new LanListener.Heard(
                new LanAnnouncement(
                        session, new RoomCode(code), "ours", 24_741, "Skirmish", "pc", 1, 8, true, password),
                "192.168.1.5");
    }

    private void typeIn(String text) {
        for (int i = 0; i < text.length(); i++) {
            choices.type(text.charAt(i));
        }
    }

    private static JoinChoices.Target target(String host, int port, LanListener.Heard session) {
        return new JoinChoices.Target(
                host, port, "room " + session.announcement().code() + " at " + host);
    }

    private static LanListener.Heard heard(long session, String build, String address, int port) {
        return new LanListener.Heard(
                new LanAnnouncement(session, RoomCode.of(session), build, port, "Skirmish", "pc", 1, 8), address);
    }
}
