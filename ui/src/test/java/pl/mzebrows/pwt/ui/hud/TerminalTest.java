package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerminalTest {

    /** Two lines, ten characters a second, highs at once, a second's hold, a queue of four. */
    private static final Terminal.Settings SLOW = new Terminal.Settings(2, 10f, 0f, 1f, 4f, 4, 2f, 0.5f, 1f, 5);

    private final List<String> said = new ArrayList<>();

    private Terminal terminal(Terminal.Settings settings) {
        return new Terminal(settings, dispatch -> {
            String words = dispatch.kind() + " " + dispatch.a();
            said.add(words);
            return words;
        });
    }

    private static Terminal.Settings fast(float minHold, float maxAge, int queue) {
        return new Terminal.Settings(2, 1000f, 0f, minHold, maxAge, queue, 2f, 0.5f, 1f, 5);
    }

    private static void run(Terminal terminal, float seconds) {
        for (float t = 0f; t < seconds; t += 0.05f) {
            terminal.advance(0.05f);
        }
    }

    @Test
    void showsTheTwoLatestDispatches() {
        Terminal terminal = terminal(fast(0.2f, 4f, 4));
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 1, 3);
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 2, 3);
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 3, 3);

        run(terminal, 2f);

        assertThat(terminal.showing()).isEqualTo(2);
        assertThat(terminal.line(0)).isEqualTo("COMMANDER_JOINED 3");
        assertThat(terminal.line(1)).isEqualTo("COMMANDER_JOINED 2");
    }

    @Test
    void typesTheNewestAtItsRate() {
        Terminal terminal = terminal(SLOW);
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 1, 3);
        terminal.advance(0f);

        assertThat(terminal.revealed()).isZero();
        terminal.advance(0.5f);

        assertThat(terminal.revealed()).isEqualTo(5);
    }

    @Test
    void showsAHighDispatchAtOnce() {
        Terminal terminal = terminal(SLOW);
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 1, 3);
        terminal.advance(0.1f);

        terminal.offer(DispatchKind.COMMANDER_DOWN, -1, 0, 0);
        terminal.advance(0.01f);

        assertThat(terminal.line(0)).isEqualTo("COMMANDER_DOWN 0");
        assertThat(terminal.revealed()).isEqualTo(terminal.line(0).length());
        assertThat(terminal.line(1)).isEqualTo("COMMANDER_JOINED 1");
    }

    @Test
    void stopsBlinkingOnceTheLineIsTyped() {
        Terminal terminal = terminal(SLOW);
        terminal.offer(DispatchKind.MISSION_STARTED, -1, 0, 0);
        terminal.advance(0.01f);

        assertThat(terminal.cursor()).isTrue();
        run(terminal, 1.2f);
        for (int frame = 0; frame < 40; frame++) {
            terminal.advance(0.05f);

            assertThat(terminal.cursor()).isFalse();
        }
    }

    @Test
    void holdsANewLineBeforeTheNextPushesItUp() {
        Terminal terminal = terminal(fast(1f, 4f, 4));
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 1, 3);
        terminal.advance(0.01f);
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 2, 3);

        run(terminal, 0.5f);

        assertThat(terminal.line(0)).isEqualTo("COMMANDER_JOINED 1");
        run(terminal, 1f);

        assertThat(terminal.line(0)).isEqualTo("COMMANDER_JOINED 2");
    }

    @Test
    void mergesRepeatsInsideTheWindow() {
        Terminal terminal = terminal(SLOW);
        terminal.offer(DispatchKind.REINFORCED, -1, 2, 0);
        terminal.offer(DispatchKind.REINFORCED, -1, 1, 0);
        terminal.offer(DispatchKind.REINFORCED, -1, 3, 0);
        terminal.advance(0.01f);

        assertThat(terminal.line(0)).isEqualTo("REINFORCED 6");
        assertThat(terminal.waiting()).isZero();
    }

    @Test
    void turnsACommanderDownIntoLostWithoutANewLine() {
        Terminal terminal = terminal(fast(0.1f, 4f, 4));
        terminal.offer(DispatchKind.COMMANDER_DOWN, 1, 0, 0);
        run(terminal, 1f);
        terminal.offer(DispatchKind.COMMANDER_JOINED, 3, 0, 0);
        run(terminal, 10f);

        terminal.offer(DispatchKind.COMMANDER_LOST, 1, 0, 0);
        terminal.advance(0.01f);

        assertThat(terminal.showing()).isEqualTo(2);
        assertThat(terminal.line(1)).isEqualTo("COMMANDER_LOST 0");
        assertThat(terminal.line(0)).isEqualTo("COMMANDER_JOINED 0");
    }

    @Test
    void saysLostAsANewLineWhenHeWentWithoutBeingDown() {
        Terminal terminal = terminal(fast(0.1f, 4f, 4));
        terminal.offer(DispatchKind.COMMANDER_DOWN, 1, 0, 0);
        terminal.advance(0.01f);

        terminal.offer(DispatchKind.COMMANDER_LOST, 2, 0, 0);
        terminal.advance(0.01f);

        assertThat(terminal.line(0)).isEqualTo("COMMANDER_LOST 0");
        assertThat(terminal.line(1)).isEqualTo("COMMANDER_DOWN 0");
    }

    @Test
    void mergesARepeatIntoTheLineOnShow() {
        Terminal terminal = terminal(fast(1f, 4f, 4));
        terminal.offer(DispatchKind.WEAPON_ACQUIRED, -1, 1, 0);
        terminal.advance(0.5f);
        terminal.offer(DispatchKind.WEAPON_ACQUIRED, -1, 2, 0);
        terminal.advance(0.01f);

        assertThat(terminal.line(0)).isEqualTo("WEAPON_ACQUIRED 2");
        assertThat(terminal.showing()).isEqualTo(1);
        assertThat(terminal.waiting()).isZero();
    }

    @Test
    void keepsSeparateLinesForSeparatePlayers() {
        Terminal terminal = terminal(fast(0.1f, 4f, 4));
        terminal.offer(DispatchKind.COMMANDER_DOWN, 0, 0, 0);
        terminal.offer(DispatchKind.COMMANDER_DOWN, 1, 0, 0);
        terminal.advance(0.01f);

        assertThat(terminal.showing()).isEqualTo(2);
    }

    @Test
    void dropsANormalDispatchThatWaitedTooLong() {
        Terminal terminal = terminal(fast(10f, 4f, 4));
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 1, 3);
        terminal.advance(0.01f);
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 2, 3);

        run(terminal, 5f);

        assertThat(terminal.waiting()).isZero();
        assertThat(terminal.line(0)).isEqualTo("COMMANDER_JOINED 1");
    }

    @Test
    void evictsTheOldestOfTheLowestPriorityWhenFull() {
        Terminal terminal = terminal(fast(0.1f, 60f, 4));
        terminal.offer(DispatchKind.REINFORCED, -1, 1, 0);
        terminal.offer(DispatchKind.WEAPON_ACQUIRED, 0, 1, 0);
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 1, 3);
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 2, 3);

        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 3, 3);
        run(terminal, 3f);

        assertThat(said)
                .containsExactly("COMMANDER_JOINED 1", "COMMANDER_JOINED 2", "COMMANDER_JOINED 3", "WEAPON_ACQUIRED 1");
    }

    @Test
    void neverEvictsAHighDispatchForALowerOne() {
        Terminal terminal = terminal(fast(0.1f, 60f, 2));
        terminal.offer(DispatchKind.MISSION_STARTED, -1, 0, 0);
        terminal.offer(DispatchKind.COMMANDER_DOWN, 1, 0, 0);

        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 1, 3);
        run(terminal, 2f);

        assertThat(said).containsExactly("MISSION_STARTED 0", "COMMANDER_DOWN 0");
    }

    @Test
    void startsEmptyOnANewWorld() {
        Terminal terminal = terminal(SLOW);
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 1, 3);
        terminal.offer(DispatchKind.COMMANDER_JOINED, -1, 2, 3);
        terminal.advance(0.1f);

        terminal.reset();

        assertThat(terminal.showing()).isZero();
        assertThat(terminal.waiting()).isZero();
        assertThat(terminal.cursor()).isFalse();
    }
}
