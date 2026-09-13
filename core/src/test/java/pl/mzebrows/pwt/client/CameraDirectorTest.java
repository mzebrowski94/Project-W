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

    /** Where the tests below park the view before anything goes off near it. */
    private static final float TARGET_X = 240f;

    private static final float TARGET_Y = 90f;

    /** A jolt worth several world units, felt out to most of a screen away. */
    private static final float SHAKE_STRENGTH = 4f;

    private static final float SHAKE_RADIUS = 60f;

    /** Hard enough to throw the view clean off the map if nothing held it in. */
    private static final float HEAVY_SHAKE = 30f;

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
    void shakesTheViewWhenSomethingViolentGoesOffNearby() {
        assertThat(shakenBy(TARGET_X, TARGET_Y, 1f)).isGreaterThan(1f);
    }

    @Test
    void feelsLessOfAShakeFromFurtherAway() {
        assertThat(shakenBy(TARGET_X + SHAKE_RADIUS * 0.75f, TARGET_Y, 1f))
                .isLessThan(shakenBy(TARGET_X, TARGET_Y, 1f));
    }

    @Test
    void feelsNothingFromAShakeRightAcrossTheLevel() {
        assertThat(shakenBy(TARGET_X + SHAKE_RADIUS + 10f, TARGET_Y, 1f)).isZero();
    }

    @Test
    void settlesTheViewAsTheShakeBurnsOut() {
        assertThat(shakenBy(TARGET_X, TARGET_Y, 0.05f)).isLessThan(shakenBy(TARGET_X, TARGET_Y, 1f));
    }

    /**
     * The bug this pair was written for. The clamp pins the centre to the middle of any level no
     * bigger than the view, so clamping the *shaken* position threw the jolt away entirely - and the
     * shipped map is exactly the size of the viewport, which meant no explosion anywhere shook
     * anything. Every other test here runs on a map far larger than the view and so never saw it.
     */
    @Test
    void shakesEvenOnALevelNoBiggerThanTheView() {
        CameraDirector director = director(0f, 0f);
        director.begin();
        director.watch(VIEW_WIDTH * 0.5f, VIEW_HEIGHT * 0.5f);
        director.update(VIEW_WIDTH, VIEW_HEIGHT, VIEW_WIDTH, VIEW_HEIGHT, FRAME);
        float restX = director.centreX();

        director.begin();
        director.watch(VIEW_WIDTH * 0.5f, VIEW_HEIGHT * 0.5f);
        director.shake(VIEW_WIDTH * 0.5f, VIEW_HEIGHT * 0.5f, SHAKE_STRENGTH, SHAKE_RADIUS, 1f);
        director.update(VIEW_WIDTH, VIEW_HEIGHT, VIEW_WIDTH, VIEW_HEIGHT, FRAME);

        assertThat(Math.abs(director.centreX() - restX)).isGreaterThan(1f);
    }

    /**
     * The view never comes to <em>rest</em> outside the level; only a shake may take it out, and never
     * further than the shake itself displaces it. That bound is what keeps the exception narrow.
     */
    @Test
    void letsAShakeLeaveTheMapButNeverByMoreThanTheShake() {
        CameraDirector director = director(0f, 0f);
        step(director, MAP_WIDTH - 5f, MAP_HEIGHT - 5f);
        float restX = director.centreX();

        director.begin();
        director.watch(MAP_WIDTH - 5f, MAP_HEIGHT - 5f);
        director.shake(MAP_WIDTH - 5f, MAP_HEIGHT - 5f, HEAVY_SHAKE, SHAKE_RADIUS, 1f);
        director.update(VIEW_WIDTH, VIEW_HEIGHT, MAP_WIDTH, MAP_HEIGHT, FRAME);

        assertThat(director.centreX()).isGreaterThan(MAP_WIDTH - VIEW_WIDTH * 0.5f);
        assertThat(director.centreX() - restX).isLessThanOrEqualTo(HEAVY_SHAKE + 0.001f);
    }

    @Test
    void refusesZoomLevelsThatAreNotAViewAtAll() {
        assertThatThrownBy(() -> new CameraDirector.Settings(0f, 6f, 0f, 2f, 0f, 2f, 6f, 1.2f, 1f, 3f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CameraDirector.Settings(0f, 6f, 0f, 2f, 2f, 1f, 6f, 1.2f, 1f, 3f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** How far one frame of shake moves the view from where it would otherwise have been sitting. */
    private static float shakenBy(float shakeX, float shakeY, float fade) {
        CameraDirector director = director(0f, 0f);
        step(director, TARGET_X, TARGET_Y);
        float restX = director.centreX();
        float restY = director.centreY();

        director.begin();
        director.watch(TARGET_X, TARGET_Y);
        director.shake(shakeX, shakeY, SHAKE_STRENGTH, SHAKE_RADIUS, fade);
        director.update(VIEW_WIDTH, VIEW_HEIGHT, MAP_WIDTH, MAP_HEIGHT, FRAME);

        return (float) Math.hypot(director.centreX() - restX, director.centreY() - restY);
    }

    private static CameraDirector director(float deadZone, float lookAhead) {
        return new CameraDirector(
                new CameraDirector.Settings(deadZone, 6f, lookAhead, 2f, NEAR, FAR, 6f, FIT_MARGIN, 1f, 3f));
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
