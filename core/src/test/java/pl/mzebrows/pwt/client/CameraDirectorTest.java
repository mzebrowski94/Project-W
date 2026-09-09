package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class CameraDirectorTest {

    private static final float VIEW_WIDTH = 160f;
    private static final float VIEW_HEIGHT = 90f;
    private static final float MAP_WIDTH = 480f;
    private static final float MAP_HEIGHT = 180f;
    private static final float NEAR = 1f;
    private static final float FAR = 2.2f;
    private static final float FIT_MARGIN = 1.2f;
    private static final float FRAME = 1f / 60f;
    private static final Offset<Float> TOLERANCE = Offset.offset(0.01f);

    /** Placing the view where it belongs on the first frame, rather than sliding in from the origin. */
    @Test
    void arrivesAtItsTargetOnTheFirstFrameInsteadOfSlidingInFromTheOrigin() {
        CameraDirector director = director(0f, 0f);

        step(director, 240f, 90f);

        assertThat(director.centreX()).isCloseTo(240f, TOLERANCE);
        assertThat(director.centreY()).isCloseTo(90f, TOLERANCE);
    }

    @Test
    void ignoresATargetFidgetingInsideTheDeadZone() {
        CameraDirector director = director(10f, 0f);
        step(director, 240f, 90f);

        step(director, 245f, 90f);

        assertThat(director.centreX()).isCloseTo(240f, TOLERANCE);
    }

    @Test
    void followsATargetOnceItLeavesTheDeadZone() {
        CameraDirector director = director(10f, 0f);
        step(director, 240f, 90f);

        for (int i = 0; i < 240; i++) {
            step(director, 300f, 90f);
        }

        assertThat(director.centreX()).isCloseTo(290f, Offset.offset(1f));
    }

    @Test
    void keepsTheViewInsideTheMap() {
        CameraDirector director = director(0f, 0f);

        step(director, 5f, 5f);

        assertThat(director.centreX()).isCloseTo(VIEW_WIDTH * 0.5f, TOLERANCE);
        assertThat(director.centreY()).isCloseTo(VIEW_HEIGHT * 0.5f, TOLERANCE);
    }

    /** A level smaller than the view sits in the middle of it rather than jammed into a corner. */
    @Test
    void centresOnAMapSmallerThanTheView() {
        CameraDirector director = director(0f, 0f);

        director.begin();
        director.watch(10f, 10f);
        director.update(VIEW_WIDTH, VIEW_HEIGHT, 100f, 50f, FRAME);

        assertThat(director.centreX()).isCloseTo(50f, TOLERANCE);
        assertThat(director.centreY()).isCloseTo(25f, TOLERANCE);
    }

    /** Local co-op in one method: two targets, one box, and the view frames both. */
    @Test
    void framesBothTargetsWhenThereAreTwo() {
        CameraDirector director = director(0f, 0f);

        director.begin();
        director.watch(200f, 90f);
        director.watch(280f, 90f);
        director.update(VIEW_WIDTH, VIEW_HEIGHT, MAP_WIDTH, MAP_HEIGHT, FRAME);

        assertThat(director.centreX()).isCloseTo(240f, TOLERANCE);
    }

    @Test
    void widensToFitTargetsThatSeparate() {
        CameraDirector director = director(0f, 0f);

        director.begin();
        director.watch(140f, 90f);
        director.watch(340f, 90f);
        director.update(VIEW_WIDTH, VIEW_HEIGHT, MAP_WIDTH, MAP_HEIGHT, FRAME);

        assertThat(director.zoom()).isCloseTo(200f / VIEW_WIDTH * FIT_MARGIN, TOLERANCE);
    }

    /** Past the far zoom the view has given up; keeping players together is the game's job, not its. */
    @Test
    void stopsWideningAtItsFarthestZoom() {
        CameraDirector director = director(0f, 0f);

        director.begin();
        director.watch(40f, 90f);
        director.watch(440f, 90f);
        director.update(VIEW_WIDTH, VIEW_HEIGHT, MAP_WIDTH, MAP_HEIGHT, FRAME);

        assertThat(director.zoom()).isCloseTo(FAR, TOLERANCE);
    }

    /**
     * A camera that closes on its goal by a flat fraction per frame travels further per second at
     * 144Hz than at 30Hz, and then feels different on someone else's monitor. Nobody reproduces that.
     */
    @Test
    void movesTheSameDistanceWhateverTheFrameRate() {
        CameraDirector coarse = director(0f, 0f);
        CameraDirector fine = director(0f, 0f);
        step(coarse, 240f, 90f);
        step(fine, 240f, 90f);

        coarse.begin();
        coarse.watch(400f, 90f);
        coarse.update(VIEW_WIDTH, VIEW_HEIGHT, MAP_WIDTH, MAP_HEIGHT, 1f / 30f);
        for (int i = 0; i < 2; i++) {
            step(fine, 400f, 90f);
        }

        assertThat(coarse.centreX()).isCloseTo(fine.centreX(), Offset.offset(0.001f));
    }

    @Test
    void leansAheadOfWhereTheTargetIsWalking() {
        CameraDirector leaning = director(0f, 20f);
        CameraDirector still = director(0f, 20f);
        step(leaning, 240f, 90f);
        step(still, 240f, 90f);

        for (int i = 0; i < 120; i++) {
            walk(leaning, 240f, 1f);
            step(still, 240f, 90f);
        }

        assertThat(leaning.centreX()).isGreaterThan(still.centreX() + 10f);
    }

    /**
     * Found by playing it. Look-ahead that follows the raw movement input snaps the whole way across
     * the moment the player reverses, and tapping left-right twice a second shakes the view badly -
     * which no test above notices, because each of them holds one direction. The lead has to ease in
     * on its own and far more slowly than the camera does, so a flick averages out to nearly nothing
     * and only sustained movement earns a full lead.
     */
    @Test
    void barelyMovesWhenTheTargetFlicksBackAndForthOnTheSpot() {
        CameraDirector director = director(6f, 14f);
        step(director, 240f, 90f);
        float lowest = director.centreX();
        float highest = director.centreX();

        for (int frame = 0; frame < 240; frame++) {
            walk(director, 240f, frame / 12 % 2 == 0 ? 1f : -1f);
            lowest = Math.min(lowest, director.centreX());
            highest = Math.max(highest, director.centreX());
        }

        assertThat(highest - lowest)
                .as("how far the view swung while the player stood still and tapped left and right")
                .isLessThan(1f);
    }

    @Test
    void swapsBetweenTwoFixedZoomLevels() {
        CameraDirector director = director(0f, 0f);
        step(director, 240f, 90f);

        director.toggleWide();
        settle(director);

        assertThat(director.wide()).isTrue();
        assertThat(director.zoom()).isCloseTo(FAR, Offset.offset(0.02f));

        director.toggleWide();
        settle(director);

        assertThat(director.wide()).isFalse();
        assertThat(director.zoom()).isCloseTo(NEAR, Offset.offset(0.02f));
    }

    @Test
    void refusesZoomLevelsThatAreNotAViewAtAll() {
        assertThatThrownBy(() -> new CameraDirector.Settings(0f, 6f, 0f, 2f, 0f, 2f, 6f, 1.2f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CameraDirector.Settings(0f, 6f, 0f, 2f, 2f, 1f, 6f, 1.2f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CameraDirector director(float deadZone, float lookAhead) {
        return new CameraDirector(new CameraDirector.Settings(deadZone, 6f, lookAhead, 2f, NEAR, FAR, 6f, FIT_MARGIN));
    }

    /** One frame with the target standing at {@code x} but walking in the given direction. */
    private static void walk(CameraDirector director, float x, float heading) {
        director.begin();
        director.watch(x, 90f);
        director.heading(heading, 0f);
        director.update(VIEW_WIDTH, VIEW_HEIGHT, MAP_WIDTH, MAP_HEIGHT, FRAME);
    }

    private static void step(CameraDirector director, float x, float y) {
        director.begin();
        director.watch(x, y);
        director.update(VIEW_WIDTH, VIEW_HEIGHT, MAP_WIDTH, MAP_HEIGHT, FRAME);
    }

    private static void settle(CameraDirector director) {
        for (int i = 0; i < 300; i++) {
            step(director, 240f, 90f);
        }
    }
}
