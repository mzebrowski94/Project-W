package pl.mzebrows.pwt.ui.nav;

import static org.assertj.core.api.Assertions.assertThat;

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
}
