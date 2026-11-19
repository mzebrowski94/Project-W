package pl.mzebrows.pwt.ui.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the briefing (G0101) shows besides its entries, as data: the players, each a row in its commander's colour -
 * never an entry, so nothing on one can be selected - and what the host's START needs and a joiner's page lacks.
 * The page's entries are {@link MenuTree#briefing}'s; the screen writes the words and the colours.
 */
public final class Briefing {

    /** One slot's holder as the session knows it. */
    public record Player(int slot, String machine, boolean host, boolean online) {}

    /** One player's row: the slot it is in (its colour), the machine it plays from, and whether that link is up. */
    public record Row(int slot, String machine, boolean online) {}

    private Briefing() {}

    /** The players as rows: the host's machine first, then by slot. */
    public static List<Row> rows(List<Player> players) {
        List<Player> ordered = new ArrayList<>(players);
        ordered.sort(Comparator.comparing((Player player) -> !player.host()).thenComparingInt(Player::slot));
        List<Row> rows = new ArrayList<>(ordered.size());
        for (Player player : ordered) {
            rows.add(new Row(player.slot(), player.machine(), player.online()));
        }
        return rows;
    }

    /** Whether another machine is in the session, its link up: the host's Back asks before it ends the session. */
    public static boolean anybodyJoined(List<Player> players) {
        for (Player player : players) {
            if (!player.host() && player.online()) {
                return true;
            }
        }
        return false;
    }

    /** START MISSION is the host's, and enabled whenever the code line names a mission, alone or with seven others. */
    public static boolean startEnabled(boolean host, boolean lineNamesMission) {
        return host && lineNamesMission;
    }

    /** A joiner's page says it waits; the host's does not. */
    public static boolean waiting(boolean host) {
        return !host;
    }
}
