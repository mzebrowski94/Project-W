package pl.mzebrows.pwt.net.session;

import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * Which computer a peer plays from, as a short word it made up once and keeps: the start of every
 * connection log's name and {@code inst=}, and said in the hello, so the host's log names the computer
 * behind each peer. The 2026-10-01 playtest's three computers each wrote logs called {@code instance0},
 * lined up only by their addresses. Random rather than the computer's name, so a log passed around
 * names nobody.
 */
public final class MachineId {

    /** The longest one a hello may carry - and so the longest nickname (the player's own name for his machine). */
    public static final int MAX_LENGTH = 16;

    /** Letters and digits nobody misreads for another: no 0 and o, no 1, l and i. */
    private static final String LETTERS = "abcdefghjkmnpqrstuvwxyz23456789";

    /** Four of them: some 900 000 machines, plenty to tell a playtest's apart. */
    private static final int LENGTH = 4;

    private MachineId() {}

    /** The one this machine kept, or a new one if what it kept is none - nothing yet, or edited by hand. */
    public static String keptOrNew(@Nullable String kept, RandomGenerator random) {
        return kept != null && valid(kept) ? kept : make(random);
    }

    /** A new one, for a machine that has none yet. */
    public static String make(RandomGenerator random) {
        StringBuilder id = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            id.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return id.toString();
    }

    /**
     * Whether {@code id} can stand in a log as it came: one to {@value #MAX_LENGTH} lower-case letters,
     * digits and dashes, so nothing a peer says can forge a line or a field in the host's log.
     */
    public static boolean valid(String id) {
        if (id.isEmpty() || id.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) {
                return false;
            }
        }
        return true;
    }
}
