package pl.mzebrows.pwt.ui.nav;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodePickerTest {

    private static final String ALPHABET = "ACDE";

    @Test
    void turnsTheLetterInTheSlotAndMovesBetweenSlots() {
        CodePicker picker = new CodePicker(ALPHABET, 4);

        picker.up();
        picker.right();
        picker.up();
        picker.up();

        assertThat(picker.code()).isEqualTo("CDAA");
        assertThat(picker.slot()).isEqualTo(1);
    }

    @Test
    void wrapsLettersAndSlotsBothWays() {
        CodePicker picker = new CodePicker(ALPHABET, 4);

        picker.down();
        picker.left();

        assertThat(picker.code()).isEqualTo("EAAA");
        assertThat(picker.slot()).isEqualTo(3);
    }

    /** A mission code's first slot holds only the tiers' letters; the rest the whole alphabet (G0065). */
    @Test
    void turnsEachSlotThroughItsOwnAlphabet() {
        CodePicker picker = new CodePicker(List.of("XY", ALPHABET));

        picker.up();
        picker.up();
        picker.right();
        picker.down();

        assertThat(picker.code()).isEqualTo("XE");
    }

    @Test
    void opensOnTheCodeItIsShownFromItsFirstSlot() {
        CodePicker picker = new CodePicker(List.of("XY", ALPHABET, ALPHABET));
        picker.right();

        picker.show("YDC");

        assertThat(picker.code()).isEqualTo("YDC");
        assertThat(picker.slot()).isZero();
        picker.up();
        assertThat(picker.code()).isEqualTo("XDC");
    }

    @Test
    void showsTheSlotBeingChangedInBracketsAndTheDashBetweenTheHalves() {
        CodePicker picker = new CodePicker(ALPHABET, 4);
        picker.right();

        assertThat(picker.shown(0)).isEqualTo(" A [A] A  A ");
        assertThat(picker.shown(2)).isEqualTo(" A [A]- A  A ");
    }
}
