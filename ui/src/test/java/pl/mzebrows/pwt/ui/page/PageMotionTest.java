package pl.mzebrows.pwt.ui.page;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageMotionTest {

    @Test
    void slidesInFromTheSideItIsGoingAndSettlesWithinItsTime() {
        PageMotion motion = new PageMotion(0.12f, 6, 1f);

        motion.arrive(1);

        assertThat(motion.offsetX()).isEqualTo(6);
        assertThat(motion.alpha()).isZero();
        motion.advance(0.12f);
        assertThat(motion.offsetX()).isZero();
        assertThat(motion.alpha()).isEqualTo(1f);
    }

    @Test
    void slidesInFromTheOtherSideComingBack() {
        PageMotion motion = new PageMotion(0.12f, 6, 1f);

        motion.arrive(-1);

        assertThat(motion.offsetX()).isEqualTo(-6);
    }

    @Test
    void finishesAtOnceWhenThePlayerActsDuringIt() {
        PageMotion motion = new PageMotion(0.12f, 6, 1f);
        motion.arrive(1);
        motion.advance(0.02f);

        motion.finish();

        assertThat(motion.moving()).isFalse();
        assertThat(motion.offsetX()).isZero();
    }

    @Test
    void doesNotMoveAtAllWithMotionOff() {
        PageMotion motion = new PageMotion(0.12f, 6, 0f);

        motion.arrive(1);

        assertThat(motion.moving()).isFalse();
        assertThat(motion.alpha()).isEqualTo(1f);
    }
}
