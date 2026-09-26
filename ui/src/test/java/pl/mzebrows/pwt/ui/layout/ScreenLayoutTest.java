package pl.mzebrows.pwt.ui.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScreenLayoutTest {

    private static final int CANVAS_HEIGHT = 360;
    private static final float ASPECT_MIN = 1.6f;
    private static final float ASPECT_MAX = 2.39f;
    private static final float TOP_SHARE = 0.5f;
    private static final HudMetrics METRICS = new HudMetrics(4, 8, 2, 4, 3, 32);

    /** The worked examples of the plan's section 5.2, one screen per row. */
    @ParameterizedTest(name = "{0} x {1}, uiScale {2}")
    @CsvSource({
        // screen,  uiScale, scale, canvas,   view x, w, h,   top, bottom
        "1920, 1080, 0, 3, 640, 360,   0, 640, 274,  46, 40",
        "2560, 1440, 0, 4, 640, 360,   0, 640, 274,  46, 40",
        "1280,  720, 0, 2, 640, 360,   0, 640, 274,  46, 40",
        "3840, 2160, 0, 6, 640, 360,   0, 640, 274,  46, 40",
        "1366,  768, 0, 2, 683, 384,   0, 683, 298,  46, 40",
        "1280,  800, 0, 2, 640, 400,   0, 640, 314,  46, 40",
        "1920, 1200, 0, 3, 640, 400,   0, 640, 314,  46, 40",
        "2560, 1080, 0, 3, 853, 360,  99, 655, 274,  46, 40",
        "3440, 1440, 0, 4, 860, 360, 102, 655, 274,  46, 40",
        "1024,  768, 0, 2, 512, 384,   0, 512, 298,  46, 40",
        " 800,  600, 0, 1, 800, 600,   0, 800, 500,  53, 47",
        "3840, 2160, 4, 4, 960, 540,   0, 960, 454,  46, 40",
    })
    void solvesEveryScreenOfTheWorkedExamples(
            int screenWidth,
            int screenHeight,
            int uiScale,
            int scale,
            int canvasWidth,
            int canvasHeight,
            int viewX,
            int viewWidth,
            int viewHeight,
            int top,
            int bottom) {
        ScreenLayout layout = ScreenLayout.solve(screenWidth, screenHeight, spec(uiScale));

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(layout.scale()).as("scale").isEqualTo(scale);
        softly.assertThat(layout.canvas()).as("canvas").isEqualTo(new Rect(0, 0, canvasWidth, canvasHeight));
        softly.assertThat(layout.view()).as("view").isEqualTo(new Rect(viewX, bottom, viewWidth, viewHeight));
        softly.assertThat(layout.topBar()).as("top bar").isEqualTo(new Rect(0, bottom + viewHeight, canvasWidth, top));
        softly.assertThat(layout.bottomBar()).as("bottom bar").isEqualTo(new Rect(0, 0, canvasWidth, bottom));
        softly.assertAll();
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1920, 1080", "1366, 768", "1280, 800", "2560, 1080", "1024, 768", "800, 600", "1000, 3000", "3000, 400"
    })
    void tilesTheCanvasWithTheTwoBarsAndTheViewAndNothingElse(int screenWidth, int screenHeight) {
        ScreenLayout layout = ScreenLayout.solve(screenWidth, screenHeight, spec(0));

        assertThat(layout.bottomBar().top()).isEqualTo(layout.view().y());
        assertThat(layout.view().top()).isEqualTo(layout.topBar().y());
        assertThat(layout.topBar().top()).isEqualTo(layout.canvas().height());
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1920, 1080", "1921, 1081", "1366, 768", "1280, 800", "3440, 1440", "1023, 767", "641, 359"})
    void neverScalesTheCanvasByAFraction(int screenWidth, int screenHeight) {
        ScreenLayout layout = ScreenLayout.solve(screenWidth, screenHeight, spec(0));

        Rect onScreen = layout.toScreen(layout.canvas());
        assertThat(onScreen.width()).isEqualTo(layout.canvas().width() * layout.scale());
        assertThat(screenWidth - onScreen.width()).isBetween(0, layout.scale() - 1);
        assertThat(screenHeight - onScreen.height()).isBetween(0, layout.scale() - 1);
        assertThat(onScreen.x()).isEqualTo((screenWidth - onScreen.width()) / 2);
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1920, 1080", "2560, 1080", "5120, 1440", "1280, 800", "1024, 768", "800, 600", "720, 1280", "600, 900"
    })
    void keepsTheViewInsideItsAspectRangeOnEveryScreen(int screenWidth, int screenHeight) {
        Rect view = ScreenLayout.solve(screenWidth, screenHeight, spec(0)).view();

        double aspect = view.width() / (double) view.height();
        assertThat(aspect).isBetween(ASPECT_MIN - 0.01, ASPECT_MAX + 0.01);
    }

    @Test
    void givesHeightBeyondTheRangeToTheBars() {
        ScreenLayout layout = ScreenLayout.solve(720, 1280, spec(0));

        assertThat(layout.view().height()).isEqualTo((int) (layout.canvas().width() / ASPECT_MIN));
        assertThat(layout.topBar().height()).isGreaterThan(METRICS.topMin());
        assertThat(layout.bottomBar().height()).isGreaterThan(METRICS.bottomMin());
    }

    @Test
    void anchorsBarContentToTheViewsSpanOnAWideScreen() {
        ScreenLayout layout = ScreenLayout.solve(2560, 1080, spec(0));

        assertThat(layout.contentLeft()).isEqualTo(99);
        assertThat(layout.contentRight()).isEqualTo(99 + 655);
        assertThat(layout.topBar().width()).isEqualTo(853);
    }

    @Test
    void honoursAUiScaleSmallerThanTheLargestThatFits() {
        ScreenLayout auto = ScreenLayout.solve(3840, 2160, spec(0));
        ScreenLayout smaller = ScreenLayout.solve(3840, 2160, spec(4));

        assertThat(smaller.scale()).isEqualTo(4);
        assertThat(smaller.toScreen(smaller.view()).height())
                .isGreaterThan(auto.toScreen(auto.view()).height());
    }

    @Test
    void neverScalesBeyondTheLargestThatFitsWhateverIsAskedFor() {
        ScreenLayout layout = ScreenLayout.solve(1920, 1080, spec(5));

        assertThat(layout.scale()).isEqualTo(3);
    }

    @Test
    void dropsTheBarsBeforeTheViewGoesUnderHalfTheWindow() {
        ScreenLayout layout = ScreenLayout.solve(300, 150, spec(0));

        assertThat(layout.view()).isEqualTo(new Rect(0, 0, 300, 150));
        assertThat(layout.topBar().isEmpty()).isTrue();
        assertThat(layout.bottomBar().isEmpty()).isTrue();
    }

    @Test
    void keepsBothBarsInAWindowJustTallEnoughForThem() {
        ScreenLayout layout = ScreenLayout.solve(400, 172, spec(0));

        assertThat(layout.topBar().height()).isEqualTo(METRICS.topMin());
        assertThat(layout.bottomBar().height()).isEqualTo(METRICS.bottomMin());
        assertThat(layout.view().height()).isEqualTo(86);
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"0, 0", "0, 720", "1280, 0", "-1, -1"})
    void survivesAZeroSizedWindow(int screenWidth, int screenHeight) {
        ScreenLayout layout = ScreenLayout.solve(screenWidth, screenHeight, spec(0));

        assertThat(layout).isEqualTo(ScreenLayout.NONE);
        assertThat(layout.view().isEmpty()).isTrue();
    }

    @Test
    void refusesAnAspectRangeWhoseMinimumIsAboveItsMaximum() {
        assertThatThrownBy(() -> new LayoutSpec(CANVAS_HEIGHT, 0, 2.4f, 1.6f, TOP_SHARE, METRICS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pinsTheViewsShapeWhenTheRangeIsOneNumber() {
        LayoutSpec pinned = new LayoutSpec(CANVAS_HEIGHT, 0, ASPECT_MAX, ASPECT_MAX, TOP_SHARE, METRICS);

        Rect view = ScreenLayout.solve(1280, 800, pinned).view();

        assertThat(view.width() / (double) view.height()).isCloseTo(ASPECT_MAX, Offset.offset(0.01));
    }

    private static LayoutSpec spec(int uiScale) {
        return new LayoutSpec(CANVAS_HEIGHT, uiScale, ASPECT_MIN, ASPECT_MAX, TOP_SHARE, METRICS);
    }
}
