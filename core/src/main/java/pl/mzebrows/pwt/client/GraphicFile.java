package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.ConfigKey;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * {@code graphic.properties} as its keys are written, bound by {@code ConfigBinder} under {@code graphic}:
 * a tunable is a component here and a line there, and nothing else - no literal default, no loader line
 * to keep in step. {@link GraphicConfig} builds what the renderer reads from it. A colour is its hex text
 * until then, checked before binding ({@link GraphicConfig#parse}).
 */
record GraphicFile(
        float virtualHeight,
        int windowWidth,
        int windowHeight,
        boolean vsync,
        String backgroundColor,
        Ui ui,
        View view,
        Hud hud,
        Defs<Look, String> color,
        @ConfigKey("color") Defs<Look, Variants> variants,
        @ConfigKey("terrain") Defs<Terrain, String> ground,
        @ConfigKey("terrain.rockEdge") String rockEdge,
        @ConfigKey("terrain.rockEdgeWidth") float rockEdgeWidth,
        @ConfigKey("terrain.rockEdgeAlpha") float rockEdgeAlpha,
        Region region,
        Tile tile,
        Thicket thicket,
        State state,
        Segments segments,
        Bloat bloat,
        Charge charge,
        Nest nest,
        Facing facing,
        Charged charged,
        Order order,
        Shield shield,
        Crate crate,
        Wound wound,
        Heat heat,
        Camera camera) {

    record Ui(
            int padding,
            int fontSize,
            int leading,
            int canvasHeight,
            int scale,
            float motionScale,
            float flashScale,
            float scrimAlpha,
            float bannerScrimAlpha,
            Motion motion,
            Page page) {}

    record Motion(float scrimSeconds, float pageSeconds, int pageSlide) {}

    record Page(int columns) {}

    record View(float aspectMin, float aspectMax, float topShare) {}

    record Hud(
            Portrait portrait,
            HudColor color,
            Overlay overlay,
            Panel panel,
            Alert alert,
            Goal goal,
            Reticle reticle,
            boolean rollCall,
            Upgrade upgrade,
            Terminal terminal) {}

    record Portrait(int size) {}

    record HudColor(String text, String textDim, String alert, String overlayFrame, String bar, String wound) {}

    record Overlay(float widthShare) {}

    record Panel(float alarmSeconds, int alarmShake) {}

    record Alert(float pulseHz) {}

    record Goal(float minHoldSeconds) {}

    record Reticle(int size, boolean keyboard, boolean pads, float padDistance) {}

    record Upgrade(float glow, float lift, float pulseHz) {}

    record Terminal(
            int lines,
            float charsPerSecond,
            float highCharsPerSecond,
            float minHoldSeconds,
            float maxAgeSeconds,
            int queue,
            float coalesceSeconds,
            float blinkSeconds,
            float cursorLingerSeconds,
            int fewTroopers) {}

    /**
     * A look's colours for the second player onward, at {@code graphic.color.<look>.<n>} - the commander's,
     * one per player a game can hold (pillar 5). A look the file gives none is one colour: {@link #none()}.
     */
    record Variants(
            @ConfigKey("1") String second,
            @ConfigKey("2") String third,
            @ConfigKey("3") String fourth,
            @ConfigKey("4") String fifth,
            @ConfigKey("5") String sixth,
            @ConfigKey("6") String seventh,
            @ConfigKey("7") String eighth) {

        /** A look drawn in its one colour, whoever it belongs to. */
        static Variants none() {
            return new Variants("", "", "", "", "", "", "");
        }

        /** The colour for this player, from the second; empty for the look's own. */
        String of(int variant) {
            return switch (variant) {
                case 1 -> second;
                case 2 -> third;
                case 3 -> fourth;
                case 4 -> fifth;
                case 5 -> sixth;
                case 6 -> seventh;
                case 7 -> eighth;
                default -> throw new IllegalArgumentException("no colour variant " + variant);
            };
        }
    }

    record Region(float variance) {}

    record Tile(float variance, int shades, int patch) {}

    record Thicket(int cells, float dot, String tint, float alpha) {}

    record State(
            float unaware,
            float windingUp,
            float airborne,
            float grappling,
            float staggered,
            float stunned,
            float slowed,
            float dying,
            float casualty,
            float crewed,
            float gripped) {}

    record Segments(float bodyOffset, float bodyScale, float headOffset, float headScale, float headShade) {}

    record Bloat(float scale, float alpha) {}

    record Charge(float scale, float tint) {}

    record Nest(int shells, float darken) {}

    record Facing(float length, float width, float tint) {}

    record Charged(float scale, String color) {}

    record Order(float scale, float tint) {}

    record Shield(float scale, int segments, String color) {}

    record Crate(float dot, String dotColor) {}

    record Wound(float size, float growth, float maxSize, String color) {}

    record Heat(float scale, float width, float spreadDegrees, int segments) {}

    record Camera(
            float deadZone,
            float smoothing,
            float lookAhead,
            float leadSmoothing,
            float nearZoom,
            float farZoom,
            float zoomSmoothing,
            float fitMargin,
            float shakeScale,
            float shakeCycles) {}
}
