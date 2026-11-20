package pl.mzebrows.pwt.net.door;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class DoorWireTest {

    @Test
    void readsBackWhatItWrote() {
        Door.Message[] all = {
            new Door.Join("ACDE", "TUV3", "b412b68", "2026.11.13-b412b68"),
            new Door.Opened("ACDE"),
            new Door.Accepted("ACDE"),
            Door.refusal("room ACDE is full"),
        };
        for (Door.Message message : all) {
            byte[] bytes = Door.encode(message);
            assertThat(Door.read(bytes, 0, bytes.length)).isEqualTo(message);
        }
        byte[] bytes = Door.encode(new Door.Open("id", "v", 9L, new byte[] {4, 5}, ""));
        Door.Open back = (Door.Open) Door.read(bytes, 0, bytes.length);
        assertThat(back.sessionConfig()).containsExactly(4, 5);
        assertThat(back.hostKey()).isEqualTo(9L);
    }

    @Test
    void keepsItsBytesAsGolden() {
        assertThat(HexFormat.of().formatHex(Door.encode(new Door.Opened("ACDE"))))
                .isEqualTo("2a" + "00000004" + "41434445");
        assertThat(HexFormat.of().formatHex(Door.encode(new Door.Join("AC", "", "i", "v"))))
                .isEqualTo("29" + "00000002" + "4143" + "00000000" + "00000001" + "69" + "00000001" + "76");
    }

    @Test
    void cutsARefusalToWhatTheDoorCarriesWithoutSplittingACharacter() {
        String reason = "no room " + "ż".repeat(Door.MAX_TEXT_BYTES);

        Door.Refused refused = Door.refusal(reason);
        byte[] bytes = Door.encode(refused);

        assertThat(refused.text().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSizeLessThanOrEqualTo(Door.MAX_TEXT_BYTES);
        assertThat(refused.text()).startsWith(Door.REFUSAL_PREFIX + "no room ż").doesNotContain("�");
        assertThat(Door.read(bytes, 0, bytes.length)).isEqualTo(refused);
    }

    @Test
    void refusesWhatIsNotExactlyOneMessage() {
        byte[] ok = Door.encode(new Door.Opened("ACDE"));
        byte[] longer = java.util.Arrays.copyOf(ok, ok.length + 1);
        assertThatThrownBy(() -> Door.read(ok, 0, ok.length - 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Door.read(longer, 0, longer.length)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Door.read(new byte[] {7}, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Door.read(new byte[] {42, 127, 0, 0, 0}, 0, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
