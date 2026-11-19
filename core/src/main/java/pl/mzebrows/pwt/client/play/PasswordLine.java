package pl.mzebrows.pwt.client.play;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.lan.RoomCode;

/**
 * A room's password as a player enters it (G0103): four characters of the room code's alphabet, read the way a code
 * is - any case, spaces and dashes ignored - and picked the way a code is, on a keyboard or letter by letter on a pad.
 * What HOST GAME sets and the join page's PASSWORD line asks, so the two read it alike. Blank is none.
 */
final class PasswordLine {

    /** What a password that is not four characters is told. */
    static final String REQUIRED = "a password is four characters of the room code's alphabet";

    /** What a password that is some but not all of its four characters is told, where none would do. */
    static final String INCOMPLETE = REQUIRED + ", or none";

    private final StringBuilder typed = new StringBuilder(RoomCode.LENGTH);

    PasswordLine(String text) {
        replace(text);
    }

    /**
     * One typed character. Backspace deletes; a character of the alphabet in either case is kept; a space, a dash
     * and anything else, and anything past the fourth character, is ignored.
     */
    void type(char c) {
        if (c == '\b') {
            if (!typed.isEmpty()) {
                typed.setLength(typed.length() - 1);
            }
            return;
        }
        char upper = Character.toUpperCase(c);
        if (typed.length() < RoomCode.LENGTH && RoomCode.ALPHABET.indexOf(upper) >= 0) {
            typed.append(upper);
        }
    }

    /** Replaces what was typed - with a password picked on a pad, or a remembered one - filtered as typing is. */
    void replace(String text) {
        typed.setLength(0);
        String upper = text.toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            type(upper.charAt(i));
        }
    }

    /** What has been typed so far, as the line shows it. */
    String text() {
        return typed.toString();
    }

    boolean blank() {
        return typed.isEmpty();
    }

    /** The password as the relay is told it: four characters, or empty for none - null while it is only part of one. */
    @Nullable
    String read() {
        return typed.isEmpty() ? "" : RoomCode.letters(typed.toString(), RoomCode.LENGTH);
    }
}
