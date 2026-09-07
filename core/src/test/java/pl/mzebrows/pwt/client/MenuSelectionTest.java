package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MenuSelectionTest {

    private static final int ENTRIES = 3;

    @Test
    void startsOnTheFirstEntry() {
        assertThat(new MenuSelection(ENTRIES).index()).isZero();
    }

    @Test
    void movesDownOneEntryAtATime() {
        MenuSelection selection = new MenuSelection(ENTRIES);

        selection.next();

        assertThat(selection.index()).isEqualTo(1);
    }

    @Test
    void wrapsToTheTopFromTheBottom() {
        MenuSelection selection = new MenuSelection(ENTRIES);

        selection.next();
        selection.next();
        selection.next();

        assertThat(selection.index()).isZero();
    }

    @Test
    void wrapsToTheBottomFromTheTop() {
        MenuSelection selection = new MenuSelection(ENTRIES);

        selection.previous();

        assertThat(selection.index()).isEqualTo(ENTRIES - 1);
    }

    @Test
    void jumpsStraightToAnEntry() {
        MenuSelection selection = new MenuSelection(ENTRIES);

        boolean selected = selection.select(2);

        assertThat(selected).isTrue();
        assertThat(selection.index()).isEqualTo(2);
    }

    @Test
    void ignoresAnEntryPastTheEndOfTheList() {
        MenuSelection selection = new MenuSelection(ENTRIES);

        boolean selected = selection.select(ENTRIES);

        assertThat(selected).isFalse();
        assertThat(selection.index()).isZero();
    }

    @Test
    void ignoresANegativeEntry() {
        MenuSelection selection = new MenuSelection(ENTRIES);

        boolean selected = selection.select(-1);

        assertThat(selected).isFalse();
        assertThat(selection.index()).isZero();
    }

    @Test
    void refusesToBuildAMenuWithNothingInIt() {
        assertThatThrownBy(() -> new MenuSelection(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
