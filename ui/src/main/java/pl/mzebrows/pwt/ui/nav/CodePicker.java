package pl.mzebrows.pwt.ui.nav;

/**
 * A room code entered with a pad alone: one slot per character, left and right move between slots,
 * up and down turn the letter in the slot through the code's alphabet. Valve's Steam Deck review fails
 * a game whose text entry needs a keyboard, and a room code is short and its alphabet small, so this is
 * all the keyboard a pad needs (plan Q6).
 */
public final class CodePicker {

    private final String alphabet;
    private final int[] letters;
    private int slot;

    public CodePicker(String alphabet, int length) {
        if (alphabet.isEmpty() || length < 1) {
            throw new IllegalArgumentException("a code needs an alphabet and at least one slot");
        }
        this.alphabet = alphabet;
        this.letters = new int[length];
    }

    public void left() {
        slot = Math.floorMod(slot - 1, letters.length);
    }

    public void right() {
        slot = Math.floorMod(slot + 1, letters.length);
    }

    /** The next letter in the slot, wrapping. */
    public void up() {
        letters[slot] = Math.floorMod(letters[slot] + 1, alphabet.length());
    }

    public void down() {
        letters[slot] = Math.floorMod(letters[slot] - 1, alphabet.length());
    }

    public int slot() {
        return slot;
    }

    public String code() {
        StringBuilder code = new StringBuilder(letters.length);
        for (int letter : letters) {
            code.append(alphabet.charAt(letter));
        }
        return code.toString();
    }
}
