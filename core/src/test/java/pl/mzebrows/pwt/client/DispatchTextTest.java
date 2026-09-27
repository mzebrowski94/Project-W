package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.ui.hud.DispatchKind;
import pl.mzebrows.pwt.ui.hud.Terminal;

class DispatchTextTest {

    private static final UiStrings STRINGS = shipped();

    private static final Terminal.Settings AT_ONCE = new Terminal.Settings(2, 1000f, 0f, 0f, 4f, 8, 2f, 0.5f, 1f, 5);

    private static String said(DispatchKind kind, int slot, int a, int b) {
        Terminal terminal = new Terminal(AT_ONCE, new DispatchText(STRINGS));
        terminal.offer(kind, slot, a, b);
        terminal.advance(0.01f);
        return terminal.line(0);
    }

    /** The shipped words: the test classpath has no {@code assets/}. */
    private static UiStrings shipped() {
        try {
            return UiStrings.parse(Files.readString(Path.of("../assets/i18n/ui.properties"), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void namesThePlayerOnlyWhenTheLineIsAboutOne() {
        assertThat(said(DispatchKind.COMMANDER_DOWN, 1, 0, 0)).isEqualTo("P2 COMMANDER DOWN");
        assertThat(said(DispatchKind.COMMANDER_DOWN, -1, 0, 0)).isEqualTo("COMMANDER DOWN");
    }

    @Test
    @SuppressWarnings("EnumOrdinal") // a dispatch carries an enum as its number
    void namesTheGunHePickedUp() {
        assertThat(said(DispatchKind.WEAPON_ACQUIRED, -1, WeaponKind.FLAMER.ordinal(), 0))
                .isEqualTo("FLAMER ACQUIRED");
    }

    @Test
    void fillsInTheNumbers() {
        assertThat(said(DispatchKind.REINFORCED, -1, 3, 0)).isEqualTo("REINFORCEMENTS HAVE ARRIVED: +3");
    }

    @Test
    void turnsADownedCommandersLineToLost() {
        Terminal terminal = new Terminal(AT_ONCE, new DispatchText(STRINGS));
        terminal.offer(DispatchKind.COMMANDER_DOWN, 1, 0, 0);
        terminal.advance(0.01f);
        terminal.offer(DispatchKind.COMMANDER_LOST, 1, 0, 0);
        terminal.advance(0.01f);

        assertThat(terminal.showing()).isEqualTo(1);
        assertThat(terminal.line(0)).isEqualTo("P2 COMMANDER LOST");
    }
}
