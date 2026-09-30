package pl.mzebrows.pwt.ui.page;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScrollListTest {

    @Test
    void neverScrollsAListThatFits() {
        ScrollList list = new ScrollList();

        list.step(3, 4, 6);
        list.glide(1f, 10f, 1f, 4, 6);

        assertThat(list.first()).isZero();
        assertThat(ScrollList.scrolls(4, 6)).isFalse();
    }

    @Test
    void stopsAtTheLastRowAndAtTheFirst() {
        ScrollList list = new ScrollList();

        list.step(50, 20, 6);
        assertThat(list.first()).isEqualTo(14);

        list.step(-50, 20, 6);
        assertThat(list.first()).isZero();
    }

    @Test
    void glidesFurtherTheHarderTheStickIsPushed() {
        ScrollList gentle = new ScrollList();
        ScrollList hard = new ScrollList();

        for (int frame = 0; frame < 10; frame++) {
            gentle.glide(0.5f, 10f, 0.1f, 40, 6);
            hard.glide(1f, 10f, 0.1f, 40, 6);
        }

        assertThat(gentle.first()).isEqualTo(5);
        assertThat(hard.first()).isEqualTo(10);
    }

    @Test
    void pullsBackInsideAListThatHasShrunk() {
        ScrollList list = new ScrollList();
        list.step(10, 20, 6);

        list.keep(8, 6);

        assertThat(list.first()).isEqualTo(2);
    }

    @Test
    void putsTheThumbAtTheBottomOfTheTrackWhenTheLastRowShows() {
        int track = 100;

        assertThat(ScrollList.thumb(track, 20, 5)).isEqualTo(25);
        assertThat(ScrollList.thumbOffset(track, 0, 20, 5)).isZero();
        assertThat(ScrollList.thumbOffset(track, 15, 20, 5)).isEqualTo(75);
    }

    @Test
    void goesBackToTheTopWhenOpenedAfresh() {
        ScrollList list = new ScrollList();
        list.step(5, 20, 6);

        list.home();

        assertThat(list.first()).isZero();
    }
}
