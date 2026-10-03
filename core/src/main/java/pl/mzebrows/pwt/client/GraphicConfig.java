package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.ConfigBinder;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.ui.hud.Terminal;
import pl.mzebrows.pwt.ui.hud.Toast;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.LayoutSpec;

/**
 * Rendering tunables, read from {@code graphic.properties}. Kept apart from {@link SimConfig} on
 * purpose: changing anything here must never change what the simulation does.
 *
 * @param virtualHeight the view's height in world units - the camera's distance. Its width is not a
 *     setting: it follows the shape the letterbox gives the view on this screen
 * @param layout how the screen is split into the UI canvas, the bars and the view
 * @param hud how the bars look
 * @param windowWidth initial window width in pixels
 * @param windowHeight initial window height in pixels
 * @param vsync whether to wait for vertical sync
 * @param backgroundColor colour the frame is cleared to
 * @param palette placeholder colour per entity look, until sprites exist
 * @param terrainPalette placeholder colour per kind of ground, and how far apart regions are shaded
 * @param stateTint how far to shift that colour for what the entity is currently doing
 * @param chargeMark how to draw an entity that is filling up towards something
 * @param facingMark how to draw which way an entity is pointing
 * @param segmentMark how to draw a creature in two segments, head and body
 * @param chargedMark how to draw a unit whose gun goes off where its shots land
 * @param nestMark how to draw a hole in the ground
 * @param orderMark how to draw a unit that is acting on an order
 * @param heatMark how to draw how hot the gun a unit is holding has become
 * @param woundMark how to draw the wounds a man on his feet is carrying
 * @param shieldMark how to draw a shield in front of a man
 * @param crateMark how to draw a crate (G0056)
 * @param camera how the view follows what it is watching, and how far out it may sit
 * @param terminal how the top bar's terminal paces its lines
 * @param toast how an upgrade earned or taken, or an adaptation, is typed over the view, and how long it stays
 */
public record GraphicConfig(
        float virtualHeight,
        LayoutSpec layout,
        HudStyle hud,
        int windowWidth,
        int windowHeight,
        boolean vsync,
        Color backgroundColor,
        Palette palette,
        TerrainPalette terrainPalette,
        GroundDetail groundDetail,
        StateTint stateTint,
        ChargeMark chargeMark,
        BloatMark bloatMark,
        NestMark nestMark,
        FacingMark facingMark,
        SegmentMark segmentMark,
        ChargedMark chargedMark,
        OrderMark orderMark,
        HeatMark heatMark,
        WoundMark woundMark,
        ShieldMark shieldMark,
        CrateMark crateMark,
        CameraDirector.Settings camera,
        Terminal.Settings terminal,
        Toast.Settings toast) {

    private static final String FILE = "graphic.properties";

    private static final String ROOT = "graphic";

    /** How a colour is written: red, green and blue in hex, and alpha after them if it is not opaque. */
    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    /**
     * Rows of text beside the portrait in a player panel: label and status, gun and heat, tally. The
     * panel's shape rather than a setting - a fourth row is a design change, not a number to turn.
     */
    private static final int PANEL_ROWS = 3;

    /** Lines in the top bar besides the terminal's: the mission and the current goal. */
    private static final int TOP_LINES_BESIDES_TERMINAL = 2;

    public GraphicConfig {
        if (virtualHeight <= 0f) {
            throw new IllegalArgumentException("virtual height must be positive");
        }
        if (windowWidth < 1 || windowHeight < 1) {
            throw new IllegalArgumentException("window size must be positive");
        }
    }

    /**
     * Rendering config from {@code graphic.properties}, with every value it lacks or garbles taken from
     * what ships and said out loud ({@link #parse}).
     */
    public static GraphicConfig load() {
        return parse(GameProperties.text(FILE));
    }

    /** What ships: {@code assets/graphic.properties}, compiled in. What every test draws with. */
    public static GraphicConfig defaults() {
        return Shipped.CONFIG;
    }

    /**
     * Rendering config from the text of a {@code graphic.properties}. Empty text - no file at all - is what
     * ships. A key no record has, a value missing or unreadable, and a colour that is not one are each
     * logged, and the shipped value stands for it; {@code ShippedGraphicConfigTest} holds the shipped file
     * to stricter terms.
     */
    public static GraphicConfig parse(String text) {
        Parsed parsed = read(text);
        for (String problem : parsed.problems()) {
            Log.error(FILE + ": " + problem + "; the shipped value stands", null);
        }
        return parsed.config();
    }

    /** The same, with everything that was wrong with the text rather than logging it. */
    static Parsed read(String text) {
        if (text.isBlank()) {
            return new Parsed(defaults(), List.of());
        }
        List<String> problems = new ArrayList<>();
        SortedMap<String, String> values = colours(ConfigBinder.parse(text), problems);
        ConfigBinder.Bound<GraphicFile> bound = ConfigBinder.bind(GraphicFile.class, ROOT, values, Shipped.VALUES);
        problems.addAll(bound.problems());
        GraphicFile file = bound.value();
        return new Parsed(file == null ? defaults() : of(file), List.copyOf(problems));
    }

    /** A config read from text, and everything that had to be taken from what ships instead. */
    public record Parsed(GraphicConfig config, List<String> problems) {}

    /** The same text with every colour that does not read as one replaced by the shipped colour, and named. */
    static SortedMap<String, String> colours(SortedMap<String, String> values, List<String> problems) {
        SortedMap<String, String> checked = new TreeMap<>(values);
        for (var entry : values.entrySet()) {
            String shipped = Shipped.VALUES.get(entry.getKey());
            if (shipped != null && isColour(shipped) && !isColour(entry.getValue())) {
                problems.add(entry.getKey() + "=" + entry.getValue() + " is not a colour");
                checked.put(entry.getKey(), shipped);
            }
        }
        return checked;
    }

    private static boolean isColour(String text) {
        return HEX.matcher(text).matches();
    }

    /** What the renderer reads, built from the file as written. */
    static GraphicConfig of(GraphicFile file) {
        return new GraphicConfig(
                file.virtualHeight(),
                layout(file),
                hud(file.hud(), file.ui()),
                file.windowWidth(),
                file.windowHeight(),
                file.vsync(),
                Color.valueOf(file.backgroundColor()),
                palette(file),
                terrainPalette(file),
                new GroundDetail(
                        file.thicket().cells(),
                        file.thicket().dot(),
                        Color.valueOf(file.thicket().tint()),
                        file.thicket().alpha(),
                        Color.valueOf(file.rockEdge()),
                        file.rockEdgeWidth(),
                        file.rockEdgeAlpha()),
                stateTint(file.state()),
                new ChargeMark(file.charge().scale(), file.charge().tint()),
                new BloatMark(file.bloat().scale(), file.bloat().alpha()),
                new NestMark(file.nest().shells(), file.nest().darken()),
                new FacingMark(
                        file.facing().length(),
                        file.facing().width(),
                        file.facing().tint()),
                new SegmentMark(
                        file.segments().bodyOffset(),
                        file.segments().bodyScale(),
                        file.segments().headOffset(),
                        file.segments().headScale(),
                        file.segments().headShade()),
                new ChargedMark(
                        file.charged().scale(), Color.valueOf(file.charged().color())),
                new OrderMark(file.order().scale(), file.order().tint()),
                new HeatMark(
                        file.heat().scale(),
                        file.heat().width(),
                        file.heat().spreadDegrees(),
                        file.heat().segments()),
                new WoundMark(
                        file.wound().size(),
                        file.wound().growth(),
                        file.wound().maxSize(),
                        Color.valueOf(file.wound().color())),
                new ShieldMark(
                        file.shield().scale(),
                        file.shield().segments(),
                        Color.valueOf(file.shield().color())),
                new CrateMark(file.crate().dot(), Color.valueOf(file.crate().dotColor())),
                camera(file.camera()),
                terminal(file.hud().terminal()),
                toast(file.hud().toast(), file.hud().terminal()));
    }

    /**
     * The letterbox, under {@code graphic.view} and {@code graphic.ui}. The bars' heights are not here:
     * they are derived from the font, the lines and the portrait they hold ({@link HudMetrics}).
     */
    private static LayoutSpec layout(GraphicFile file) {
        GraphicFile.Ui ui = file.ui();
        HudMetrics metrics = new HudMetrics(
                ui.padding(),
                ui.fontSize(),
                ui.leading(),
                TOP_LINES_BESIDES_TERMINAL + file.hud().terminal().lines(),
                PANEL_ROWS,
                file.hud().portrait().size());
        return new LayoutSpec(
                ui.canvasHeight(),
                ui.scale(),
                file.view().aspectMin(),
                file.view().aspectMax(),
                file.view().topShare(),
                metrics);
    }

    /** The terminal (plan 8.4), under {@code graphic.hud.terminal}. Its line count also sizes the top bar. */
    private static Terminal.Settings terminal(GraphicFile.Terminal terminal) {
        return new Terminal.Settings(
                terminal.lines(),
                terminal.charsPerSecond(),
                terminal.highCharsPerSecond(),
                terminal.minHoldSeconds(),
                terminal.maxAgeSeconds(),
                terminal.queue(),
                terminal.coalesceSeconds(),
                terminal.blinkSeconds(),
                terminal.cursorLingerSeconds(),
                terminal.fewTroopers());
    }

    /** The toast ({@code UI:hud/view/toast}), under {@code graphic.hud.toast}; its cursor blinks as the terminal's does. */
    private static Toast.Settings toast(GraphicFile.Toast toast, GraphicFile.Terminal terminal) {
        return new Toast.Settings(toast.charsPerSecond(), toast.holdSeconds(), toast.queue(), terminal.blinkSeconds());
    }

    private static HudStyle hud(GraphicFile.Hud hud, GraphicFile.Ui ui) {
        GraphicFile.HudColor color = hud.color();
        return new HudStyle(
                Color.valueOf(color.text()),
                Color.valueOf(color.textDim()),
                Color.valueOf(color.alert()),
                Color.valueOf(color.overlayFrame()),
                Color.valueOf(color.bar()),
                hud.overlay().widthShare(),
                ui.motionScale(),
                ui.flashScale(),
                hud.panel().alarmSeconds(),
                hud.panel().alarmShake(),
                hud.alert().pulseHz(),
                hud.goal().minHoldSeconds(),
                hud.reticle().size(),
                hud.reticle().keyboard(),
                hud.reticle().pads(),
                hud.reticle().padDistance(),
                hud.rollCall(),
                ui.scrimAlpha(),
                ui.bannerScrimAlpha(),
                ui.motion().scrimSeconds(),
                ui.motion().pageSeconds(),
                ui.motion().pageSlide(),
                ui.page().columns(),
                Color.valueOf(color.wound()),
                Color.valueOf(color.swarm()),
                hud.upgrade().glow(),
                hud.upgrade().lift(),
                hud.upgrade().pulseHz());
    }

    /**
     * One colour per look, keyed by the enum's own name, plus a colour per player for a look that has
     * them - the commander, so four of them on a field are four colours rather than four identical
     * circles (pillar 5). A look with none is one colour for everybody.
     */
    @SuppressWarnings("EnumOrdinal")
    private static Palette palette(GraphicFile file) {
        Look[] looks = Look.values();
        Color[][] colors = new Color[looks.length][];
        for (Look look : looks) {
            Color ordinary = Color.valueOf(file.color().of(look));
            GraphicFile.Variants variants = file.variants().of(look);
            Color[] byPlayer = new Color[Players.MAX];
            byPlayer[0] = ordinary;
            for (int variant = 1; variant < Players.MAX; variant++) {
                String written = variants.of(variant);
                byPlayer[variant] = written.isEmpty() ? new Color(ordinary) : Color.valueOf(written);
            }
            colors[look.ordinal()] = byPlayer;
        }
        return new Palette(colors);
    }

    /** One colour per kind of ground, plus how far apart two regions of the same ground may be shaded. */
    @SuppressWarnings("EnumOrdinal")
    private static TerrainPalette terrainPalette(GraphicFile file) {
        Terrain[] terrains = Terrain.values();
        Color[] colors = new Color[terrains.length];
        for (Terrain terrain : terrains) {
            colors[terrain.ordinal()] = Color.valueOf(file.ground().of(terrain));
        }
        return new TerrainPalette(
                colors,
                file.region().variance(),
                file.tile().variance(),
                file.tile().shades(),
                file.tile().patch());
    }

    private static StateTint stateTint(GraphicFile.State state) {
        return new StateTint(
                state.unaware(),
                state.windingUp(),
                state.airborne(),
                state.grappling(),
                state.staggered(),
                state.stunned(),
                state.slowed(),
                state.dying(),
                state.casualty(),
                state.crewed(),
                state.gripped());
    }

    /**
     * How the view behaves. Rendering only - none of it can reach the simulation, and a camera setting
     * that changed what the game does would be a bug rather than a feature.
     */
    private static CameraDirector.Settings camera(GraphicFile.Camera camera) {
        return new CameraDirector.Settings(
                camera.deadZone(),
                camera.smoothing(),
                camera.lookAhead(),
                camera.leadSmoothing(),
                camera.nearZoom(),
                camera.farZoom(),
                camera.zoomSmoothing(),
                camera.fitMargin(),
                camera.shakeScale(),
                camera.shakeCycles());
    }

    /** Bound on first use, so a failure names the key rather than a class. */
    private static final class Shipped {

        static final SortedMap<String, String> VALUES = ConfigBinder.parse(ShippedGraphicConfig.TEXT);

        static final GraphicConfig CONFIG =
                of(ConfigBinder.bindStrictly(GraphicFile.class, ROOT, ShippedGraphicConfig.TEXT));

        private Shipped() {}
    }
}
