package pl.mzebrows.pwt.ui.nav;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InputModalityTest {

    @Test
    void switchesToThePointerWhenTheMouseMovesFarEnough() {
        InputModality modality = new InputModality();
        NavInput input = new NavInput();
        input.pointerMoved = true;

        modality.update(input);

        assertThat(modality.mode()).isEqualTo(InputModality.Mode.POINTER);
        assertThat(modality.showsPointer()).isTrue();
    }

    @Test
    void ignoresATwitchUnderTheWakeDistance() {
        InputModality modality = new InputModality();

        modality.update(new NavInput());

        assertThat(modality.mode()).isEqualTo(InputModality.Mode.DIGITAL);
    }

    @Test
    void takesTheSelectionBackWhenAKeyIsPressed() {
        InputModality modality = new InputModality();
        NavInput input = new NavInput();
        input.pointerMoved = true;
        modality.update(input);

        input.clear();
        input.down = true;
        input.device = Device.KEYBOARD;
        modality.update(input);

        assertThat(modality.mode()).isEqualTo(InputModality.Mode.DIGITAL);
        assertThat(modality.padHints()).isFalse();
    }

    @Test
    void namesPadButtonsInTheHintsOfTheLastPadThatActed() {
        InputModality modality = new InputModality();
        NavInput input = new NavInput();
        input.confirm = true;
        input.device = Device.PAD;
        input.playStation = true;

        modality.update(input);

        assertThat(modality.padHints()).isTrue();
        assertThat(modality.playStation()).isTrue();
    }
}
