package pl.mzebrows.pwt.ui.nav;

import java.util.Collections;
import java.util.List;

/**
 * A code entered with a pad alone: one slot per character, left and right move between slots, up and down
 * turn the letter in the slot through that slot's alphabet. Valve's Steam Deck review fails a game whose
 * text entry needs a keyboard, and a code is short and its alphabet small, so this is all the keyboard a pad
 * needs (plan Q6). A room code has one alphabet in every slot; a mission code's first slot holds only the
 * tiers' letters (G0065).
 */
public final class CodePicker {

    private final String[] alphabets;
    private final int[] letters;
    private int slot;

    /** Every slot turning through the same alphabet. */
    public CodePicker(String alphabet, int length) {
        this(Collections.nCopies(Math.max(0, length), alphabet));
    }

    /** One slot per alphabet, in order. */
    public CodePicker(List<String> alphabets) {
        if (alphabets.isEmpty() || alphabets.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("a code needs an alphabet in every slot and at least one slot");
        }
        this.alphabets = alphabets.toArray(new String[0]);
        this.letters = new int[alphabets.size()];
    }

    /**
     * Shows this code, from the first slot - where a picker opens on a code it was handed. A character its
     * slot's alphabet lacks shows as that alphabet's first.
     */
    public void show(String code) {
        if (code.length() != letters.length) {
            throw new IllegalArgumentException("a code of " + letters.length + " characters, not " + code);
        }
        for (int i = 0; i < letters.length; i++) {
            letters[i] = Math.max(0, alphabets[i].indexOf(code.charAt(i)));
        }
        slot = 0;
    }

    public void left() {
        slot = Math.floorMod(slot - 1, letters.length);
    }

    public void right() {
        slot = Math.floorMod(slot + 1, letters.length);
    }

    /** The next letter in the slot, wrapping. */
    public void up() {
        letters[slot] = Math.floorMod(letters[slot] + 1, alphabets[slot].length());
    }

    public void down() {
        letters[slot] = Math.floorMod(letters[slot] - 1, alphabets[slot].length());
    }

    public int slot() {
        return slot;
    }

    public String code() {
        StringBuilder code = new StringBuilder(letters.length);
        for (int i = 0; i < letters.length; i++) {
            code.append(alphabets[i].charAt(letters[i]));
        }
        return code.toString();
    }

    /**
     * The code as the picker draws it - {@code  A [C] D  E }, the slot being changed in brackets - with a dash
     * after the first {@code dashAfter} characters, or none for 0.
     */
    public String shown(int dashAfter) {
        String code = code();
        StringBuilder text = new StringBuilder(3 * code.length() + 1);
        for (int i = 0; i < code.length(); i++) {
            if (i == dashAfter && dashAfter > 0) {
                text.append('-');
            }
            if (i == slot) {
                text.append('[').append(code.charAt(i)).append(']');
            } else {
                text.append(' ').append(code.charAt(i)).append(' ');
            }
        }
        return text.toString();
    }
}
