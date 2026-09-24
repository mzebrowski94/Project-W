package pl.mzebrows.pwt.client;

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
