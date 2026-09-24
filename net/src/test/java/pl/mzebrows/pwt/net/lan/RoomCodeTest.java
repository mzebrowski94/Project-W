package pl.mzebrows.pwt.net.lan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomCodeTest {

    @Test
    void makesTheSameCodeFromTheSameSessionAndDifferentOnesFromOthers() {
        Set<String> codes = new HashSet<>();
        for (long session = -500; session < 500; session++) {
            codes.add(RoomCode.of(session * 0x9E37_79B9_7F4A_7C15L).text());
        }

        assertThat(RoomCode.of(42L)).isEqualTo(RoomCode.of(42L));
        assertThat(codes).hasSizeGreaterThan(990);
    }

    @Test
    void readsWhatAPlayerTypesHoweverHeTypesIt() {
        RoomCode code = RoomCode.of(123_456_789L);
        String typed = code.text().toLowerCase(java.util.Locale.ROOT);

        assertThat(RoomCode.parse(typed)).isEqualTo(code);
        assertThat(RoomCode.parse(" " + typed.substring(0, 2) + "-" + typed.substring(2) + " "))
                .isEqualTo(code);
    }

    @Test
    void leavesOutEveryCharacterThatCouldBeMisread() {
        assertThat(RoomCode.ALPHABET).doesNotContain("0", "O", "1", "I", "L", "2", "Z", "5", "S", "8", "B");
        assertThat(RoomCode.parse("K0QX")).isNull();
        assertThat(RoomCode.parse("KQX")).isNull();
        assertThat(RoomCode.parse("192.168.1.5")).isNull();
        assertThatThrownBy(() -> new RoomCode("KOQX")).isInstanceOf(IllegalArgumentException.class);
    }
}
