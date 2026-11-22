package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.config.ConfigLoader;
import pl.mzebrows.pwt.client.hud.Hud;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.ConfigBinder;
import pl.mzebrows.pwt.sim.ConfigFile;
import pl.mzebrows.pwt.sim.ConfigKey;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.ui.hud.Terminal;
import pl.mzebrows.pwt.ui.hud.Toast;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.LayoutSpec;

/**
 * Rendering tunables, read from {@code graphic.properties}. Kept apart from {@link SimConfig} on purpose:
 * changing anything here must never change what the simulation does.
 *
 * <p><strong>Shaped as the file is</strong>, and bound from it directly: a tunable is a component here and a
 * line there, and nothing else. A mark the renderer draws is its own block - {@code graphic.charge.*} is a
 * {@link ChargeMark} - and a colour is read by the binder ({@code #rrggbb}, or {@code #rrggbbaa}). What is
 * built from several blocks - the layout, the palettes, the ground's detail, the toast's pace - is a method.
 *
 * @param virtualHeight the view's height in world units - the camera's distance. Its width is not a setting:
 *     it follows the shape the letterbox gives the view on this screen
 * @param windowWidth initial window width in pixels
 * @param windowHeight initial window height in pixels
 * @param vsync whether to wait for vertical sync
 * @param backgroundColor colour the frame is cleared to
 * @param ui the UI canvas, its type and its motion
 * @param view how the screen is split into the bars and the view
 * @param hud how the bars look
 * @param color placeholder colour per entity look, until sprites exist
 * @param variants a look's colours for the second player onward
 * @param ground placeholder colour per kind of ground
 * @param region, tile how far apart regions and tiles of the same ground are shaded
 * @param thicket how a thicket is hatched over its ground
 * @param state how far to shift a colour for what the entity is currently doing
 * @param segments how to draw a creature in two segments, head and body
 * @param bloat how to draw a body being pumped up
 * @param charge how to draw an entity that is filling up towards something
 * @param nest how to draw a hole in the ground
 * @param facing how to draw which way an entity is pointing
 * @param charged how to draw a unit whose gun goes off where its shots land
 * @param order how to draw a unit that is acting on an order
 * @param shield how to draw a shield in front of a man
 * @param crate how to draw a crate (G0056)
 * @param wound how to draw the wounds a man on his feet is carrying
 * @param heat how to draw how hot the gun a unit is holding has become
 * @param door how to draw a door being opened (G0069)
 * @param post how to draw a post, round the point it was taken from
 * @param flag how to draw a control point once it is taken: its pole, its flag and its ring
 * @param tree how to draw a tree's foliage over its trunk (G0072)
 * @param boulder how to shade a boulder
 * @param sling how to draw a slinger's riders on its back (G0074)
 * @param shadow how to draw the shadow under whatever is off the ground - a slung rider, a thrown grenade (G0077)
 * @param pop how to draw a take by touch rising off the taker (G0078)
 * @param geyser how to draw a geyser filling: its fire mark, pulsing (G0089)
 * @param slither how to draw the parts trailing a slithering head (G0095)
 * @param burrow how to draw a worm: its shadow under the ground and its maw's teeth up (G0096)
 * @param devour how to draw a clinch ending: the one killed shrinking, the pair swayed (G0099)
 * @param dash how to draw a dash: its thrusters and the puff it leaves (G0107)
 * @param border how to draw the rim every commander has
 * @param downMark how to draw the down mark over a commander who is down (G0109)
 * @param action how to draw the action key's brackets and its prompt (G0098)
 * @param actionMark the colour of the brackets on what the action key would act on, and of its prompt's frame
 * @param pickupMark the colour of the ticks on a pickup worth something to a commander near it
 * @param camera how the view follows what it is watching, and how far out it may sit
 */
public record GraphicConfig(
        float virtualHeight,
        int windowWidth,
        int windowHeight,
        boolean vsync,
        Color backgroundColor,
        Ui ui,
        View view,
        Hud hud,
        Defs<Look, Color> color,
        @ConfigKey("color") Defs<Look, Variants> variants,
        @ConfigKey("terrain") Defs<Terrain, Color> ground,
        @ConfigKey("terrain.rockEdge") Color rockEdge,
        @ConfigKey("terrain.rockEdgeWidth") float rockEdgeWidth,
        @ConfigKey("terrain.rockEdgeAlpha") float rockEdgeAlpha,
        Region region,
        Tile tile,
        Thicket thicket,
        StateTint state,
        SegmentMark segments,
        BloatMark bloat,
        ChargeMark charge,
        NestMark nest,
        FacingMark facing,
        ChargedMark charged,
        OrderMark order,
        ShieldMark shield,
        CrateMark crate,
        WoundMark wound,
        HeatMark heat,
        DoorMark door,
        PostMark post,
        FlagMark flag,
        BurstMark burst,
        DazeMark daze,
        TreeMark tree,
        BoulderMark boulder,
        SlingMark sling,
        ShadowMark shadow,
        PopMark pop,
        GeyserMark geyser,
        SlitherMark slither,
        BurrowMark burrow,
        DevourMark devour,
        LimpetMark limpet,
        DashMark dash,
        BorderMark border,
        DownMark downMark,
        ActionMark action,
        @ConfigKey("color.actionMark") Color actionMark,
        @ConfigKey("color.pickupMark") Color pickupMark,
        CameraDirector.Settings camera) {

    /** How a colour is written: red, green and blue in hex, and alpha after them if it is not opaque. */
    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    /** How a colour is read - what the binder cannot see from {@code sim}. */
    private static final ConfigBinder.Conversions COLOURS =
            new ConfigBinder.Conversions(Map.of(Color.class, GraphicConfig::colour));

    /** {@code assets/graphic.properties}: what ships, compiled in, and how a player's copy is read. */
    public static final ConfigFile<GraphicConfig> FILE =
            new ConfigFile<>("graphic.properties", GraphicConfig.class, "graphic", ShippedGraphicConfig.TEXT, COLOURS);

    /** The fastest an alert or the upgrade tell may pulse, a second. */
    public static final float MAX_PULSE_HZ = 3f;

    /**
     * Rows of text beside the portrait in a player panel: label and status, gun and heat, tally. The panel's
     * shape rather than a setting - a fourth row is a design change, not a number to turn.
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
     * The UI canvas, under {@code graphic.ui}: its type, how it is scaled, and how it moves.
     *
     * @param motionScale how far anything on the canvas moves; zero holds it all still
     * @param flashScale how bright a flash is; zero turns them off
     * @param scrimAlpha how dark the scrim behind a page is
     * @param bannerScrimAlpha how dark the scrim behind the paused banner is
     * @param resultHoldSeconds how long the world runs on after a level's outcome before the result
     *     screen opens (G0066)
     */
    public record Ui(
            int padding,
            int fontSize,
            int leading,
            int canvasHeight,
            int scale,
            float motionScale,
            float flashScale,
            float scrimAlpha,
            float bannerScrimAlpha,
            float resultHoldSeconds,
            Motion motion,
            Page page) {

        public Ui {
            if (motionScale < 0f || flashScale < 0f) {
                throw new IllegalArgumentException("HUD motion out of range");
            }
            if (resultHoldSeconds < 0f) {
                throw new IllegalArgumentException("result hold must not be negative");
            }
        }
    }

    /** How long a scrim and a page take to come in, and how far a page slides. */
    public record Motion(float scrimSeconds, float pageSeconds, int pageSlide) {}

    /**
     * The menu's pages, under {@code graphic.ui.page}.
     *
     * @param columns how many characters wide a page's panel is
     * @param scenarios whether the Adventures are listed - under CAMPAIGN, and under HOST GAME after its
     *     missions. Off, CAMPAIGN says it is not open yet and points at the daily missions
     */
    public record Page(int columns, boolean scenarios) {}

    /** How wide the view may be against its height, and how much of the bars the top one takes. */
    public record View(float aspectMin, float aspectMax, float topShare) {}

    /** How the bars look, under {@code graphic.hud}. */
    public record Hud(
            Portrait portrait,
            HudColor color,
            Overlay overlay,
            Panel panel,
            Alert alert,
            Goal goal,
            Reticle reticle,
            boolean rollCall,
            Upgrade upgrade,
            Terminal.Settings terminal,
            ToastPace toast) {}

    public record Portrait(int size) {}

    /** The bars' colours. */
    public record HudColor(
            Color text,
            Color textDim,
            Color alert,
            Color overlayFrame,
            Color bar,
            Color wound,
            Color swarm,
            Color dash) {}

    /** How much of the view's width the dev overlay may take. */
    public record Overlay(float widthShare) {

        public Overlay {
            if (!(widthShare >= 0f && widthShare < 1f)) {
                throw new IllegalArgumentException("overlay width share is a fraction below one: " + widthShare);
            }
        }
    }

    /** How long a player panel's alarm runs, and how far it shakes the panel. */
    public record Panel(float alarmSeconds, int alarmShake) {

        public Panel {
            if (alarmSeconds < 0f || alarmShake < 0) {
                throw new IllegalArgumentException("HUD motion out of range");
            }
        }
    }

    /** How fast an alert pulses. */
    public record Alert(float pulseHz) {

        public Alert {
            if (!(pulseHz >= 0f && pulseHz <= MAX_PULSE_HZ)) {
                throw new IllegalArgumentException(
                        "an alert may pulse at most " + MAX_PULSE_HZ + " times a second, not " + pulseHz);
            }
        }
    }

    /** How long a goal stays up before the next may replace it. */
    public record Goal(float minHoldSeconds) {}

    /**
     * The aim reticles: how big, whether the keyboard player has one and the pad players do, and how far
     * ahead a pad player's is drawn.
     */
    public record Reticle(int size, boolean keyboard, boolean pads, float padDistance) {

        public Reticle {
            if (size < 1 || padDistance < 0f) {
                throw new IllegalArgumentException("HUD motion out of range");
            }
        }
    }

    /**
     * The upgrade tell: how much it glows and lifts, both shares, and how fast it pulses; and how long the prompt over
     * a commander who has just earned a draft stays (G0098).
     */
    public record Upgrade(float glow, float lift, float pulseHz, float promptSeconds) {

        public Upgrade {
            if (!(pulseHz >= 0f && pulseHz <= MAX_PULSE_HZ)) {
                throw new IllegalArgumentException(
                        "the upgrade tell may pulse at most " + MAX_PULSE_HZ + " times a second, not " + pulseHz);
            }
            if (!(glow >= 0f && glow <= 1f && lift >= 0f && lift <= 1f)) {
                throw new IllegalArgumentException(
                        "the upgrade tell's glow and lift are shares: " + glow + ", " + lift);
            }
            if (promptSeconds < 0f) {
                throw new IllegalArgumentException("the upgrade prompt's seconds are not negative: " + promptSeconds);
            }
        }
    }

    /** How fast the toast types, how long it stays, and how many may wait; its cursor blinks as the terminal's. */
    public record ToastPace(float charsPerSecond, float holdSeconds, int queue) {}

    /**
     * A look's colours for the second player onward, at {@code graphic.color.<look>.<n>} - the commander's,
     * one per player a game can hold (pillar 5). A look the file gives none is one colour: {@link #none()}.
     */
    public record Variants(
            @ConfigKey("1") @Nullable Color second,
            @ConfigKey("2") @Nullable Color third,
            @ConfigKey("3") @Nullable Color fourth,
            @ConfigKey("4") @Nullable Color fifth,
            @ConfigKey("5") @Nullable Color sixth,
            @ConfigKey("6") @Nullable Color seventh,
            @ConfigKey("7") @Nullable Color eighth) {

        /** A look drawn in its one colour, whoever it belongs to. */
        static Variants none() {
            return new Variants(null, null, null, null, null, null, null);
        }

        /** The colour for this player, from the second; null for the look's own. */
        @Nullable
        Color of(int variant) {
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

    /** How far apart two regions of the same ground may be shaded. */
    public record Region(float variance) {}

    /** How far apart two tiles of the same ground may be shaded, in how many shades, by patches how big. */
    public record Tile(float variance, int shades, int patch) {}

    /** How a thicket is hatched: marks a tile, their size, their tint and how strong. */
    public record Thicket(int cells, float dot, Color tint, float alpha) {}

    /**
     * Rendering config from {@code graphic.properties}, with every value it lacks or garbles taken from what
     * ships and said out loud.
     */
    public static GraphicConfig load() {
        return ConfigLoader.load(FILE);
    }

    /** What ships: {@code assets/graphic.properties}, compiled in. What every test draws with. */
    public static GraphicConfig defaults() {
        return FILE.defaults();
    }

    /** A colour as the file writes it; anything else is not a colour. */
    static Color colour(String text) {
        if (!HEX.matcher(text).matches()) {
            throw new IllegalArgumentException("not a colour");
        }
        return Color.valueOf(text);
    }

    /**
     * The letterbox, from {@code graphic.view} and {@code graphic.ui}. The bars' heights are not settings:
     * they follow from the font, the lines and the portrait they hold ({@link HudMetrics}).
     */
    public LayoutSpec layout() {
        HudMetrics metrics = new HudMetrics(
                ui.padding(),
                ui.fontSize(),
                ui.leading(),
                TOP_LINES_BESIDES_TERMINAL + hud.terminal().lines(),
                PANEL_ROWS,
                hud.portrait().size());
        return new LayoutSpec(
                ui.canvasHeight(), ui.scale(), view.aspectMin(), view.aspectMax(), view.topShare(), metrics);
    }

    /** The toast ({@code UI:hud/view/toast}): its pace, and the terminal's cursor blink. */
    public Toast.Settings toast() {
        ToastPace pace = hud.toast();
        return new Toast.Settings(
                pace.charsPerSecond(),
                pace.holdSeconds(),
                pace.queue(),
                hud.terminal().blinkSeconds());
    }

    /**
     * One colour per look, plus a colour per player for a look that has them - the commander, so four of
     * them on a field are four colours rather than four identical circles (pillar 5). A look with none is
     * one colour for everybody. Built afresh on each call: ask once and keep it.
     */
    @SuppressWarnings("EnumOrdinal")
    public Palette palette() {
        Look[] looks = Look.values();
        Color[][] colors = new Color[looks.length][];
        for (Look look : looks) {
            Color ordinary = color.of(look);
            Variants byVariant = variants.of(look);
            Color[] byPlayer = new Color[Players.MAX];
            byPlayer[0] = new Color(ordinary);
            for (int variant = 1; variant < Players.MAX; variant++) {
                Color written = byVariant.of(variant);
                byPlayer[variant] = new Color(written == null ? ordinary : written);
            }
            colors[look.ordinal()] = byPlayer;
        }
        return new Palette(colors);
    }

    /** One colour per kind of ground, and how far apart regions and tiles of one ground are shaded. */
    @SuppressWarnings("EnumOrdinal")
    public TerrainPalette terrainPalette() {
        Terrain[] terrains = Terrain.values();
        Color[] colors = new Color[terrains.length];
        for (Terrain terrain : terrains) {
            colors[terrain.ordinal()] = new Color(ground.of(terrain));
        }
        return new TerrainPalette(colors, region.variance(), tile.variance(), tile.shades(), tile.patch());
    }

    /** What is drawn over the ground: a thicket's hatch, and rock's edge. */
    public GroundDetail groundDetail() {
        return new GroundDetail(
                thicket.cells(),
                thicket.dot(),
                thicket.tint(),
                thicket.alpha(),
                rockEdge,
                rockEdgeWidth,
                rockEdgeAlpha);
    }
}
