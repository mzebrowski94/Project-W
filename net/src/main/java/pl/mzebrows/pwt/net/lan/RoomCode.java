package pl.mzebrows.pwt.net.lan;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * What a player says to name a session: four characters, read out over voice chat and typed on another
 * machine. Nothing in it can be misread as anything else - no 0 and O, no 1, I and L, no 2 and Z, no 5
 * and S, no 8 and B - so 25 characters and about 390,000 codes: plenty for one network, and for the relay
 * later (stage 7), which hands out the same kind of code over the internet.
 *
 * <p>A code is not a secret and not a key. It says which session, nothing more.
 */
public record RoomCode(String text) {

    static final String ALPHABET = "ACDEFGHJKMNPQRTUVWXY34679";
    static final int LENGTH = 4;

    public RoomCode {
        if (text.length() != LENGTH) {
            throw new IllegalArgumentException("a room code is " + LENGTH + " characters, not " + text);
        }
        for (int i = 0; i < LENGTH; i++) {
            if (ALPHABET.indexOf(text.charAt(i)) < 0) {
                throw new IllegalArgumentException("no room code has a " + text.charAt(i) + " in it");
            }
        }
    }

    /** The code a session with this id goes by. */
    public static RoomCode of(long session) {
        char[] code = new char[LENGTH];
        long rest = session;
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET.charAt((int) Long.remainderUnsigned(rest, ALPHABET.length()));
            rest = Long.divideUnsigned(rest, ALPHABET.length());
        }
        return new RoomCode(new String(code));
    }

    /**
     * What a player typed, as a code - any case, spaces and dashes ignored - or null if it cannot be one.
     */
    public static @Nullable RoomCode parse(String typed) {
        String cleaned = typed.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (cleaned.length() != LENGTH) {
            return null;
        }
        for (int i = 0; i < LENGTH; i++) {
            if (ALPHABET.indexOf(cleaned.charAt(i)) < 0) {
                return null;
            }
        }
        return new RoomCode(cleaned);
    }

    @Override
    public String toString() {
        return text;
    }
}
