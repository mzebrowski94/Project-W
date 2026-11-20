package pl.mzebrows.pwt.ui.page;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.ui.page.Briefing.Player;
import pl.mzebrows.pwt.ui.page.Briefing.Row;

/** The briefing's page as data (G0101): who is in, what the host may do, and what a joiner may not. */
class BriefingPageTest {

    @Test
    void listsThePlayersAsRowsTheHostsMachineFirstThenBySlot() {
        List<Row> rows = Briefing.rows(List.of(
                new Player(2, "kd7p", false, true),
                new Player(0, "host", true, true),
                new Player(1, "m4xz", false, true),
                new Player(3, "kd7p", false, true)));

        assertThat(rows)
                .extracting(Row::slot, Row::machine)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, "host"),
                        org.assertj.core.groups.Tuple.tuple(1, "m4xz"),
                        org.assertj.core.groups.Tuple.tuple(2, "kd7p"),
                        org.assertj.core.groups.Tuple.tuple(3, "kd7p"));
    }

    @Test
    void marksAPlayerWhoseLinkIsGoneAndKeepsItsRow() {
        List<Row> rows = Briefing.rows(List.of(new Player(0, "host", true, true), new Player(1, "m4xz", false, false)));

        assertThat(rows).extracting(Row::online).containsExactly(true, false);
        assertThat(Briefing.anybodyJoined(
                        List.of(new Player(0, "host", true, true), new Player(1, "m4xz", false, false))))
                .as("a link that is gone has not joined")
                .isFalse();
    }

    @Test
    void countsAnotherMachineWithItsLinkUpAsSomebodyJoined() {
        assertThat(Briefing.anybodyJoined(List.of(new Player(0, "host", true, true))))
                .isFalse();
        assertThat(Briefing.anybodyJoined(
                        List.of(new Player(0, "host", true, true), new Player(1, "m4xz", false, true))))
                .isTrue();
    }

    @Test
    void enablesStartForTheHostWheneverTheLineNamesAMissionAndForNobodyElse() {
        assertThat(Briefing.startEnabled(true, true)).isTrue();
        assertThat(Briefing.startEnabled(true, false)).isFalse();
        assertThat(Briefing.startEnabled(false, true)).isFalse();
    }

    @Test
    void givesAJoinerNothingToChooseButTheWayOutAndSaysHeWaits() {
        MenuPage joiner = MenuTree.briefing(false);
        MenuPage host = MenuTree.briefing(true);

        assertThat(joiner.entries()).extracting(MenuEntry::id).containsExactly("back");
        assertThat(Briefing.waiting(false)).isTrue();
        assertThat(Briefing.waiting(true)).isFalse();
        assertThat(host.entries())
                .extracting(MenuEntry::id)
                .containsExactly(
                        "easy", "medium", "hard", "random", "length", "health", "count", "code", "start", "back");
    }
}
