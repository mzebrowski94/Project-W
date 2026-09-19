package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import java.util.Locale;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * Rendering tunables, read from {@code graphic.properties}. Kept apart from {@link SimConfig} on
 * purpose: changing anything here must never change what the simulation does.
 *
 * @param virtualWidth viewport width in world units
 * @param virtualHeight viewport height in world units
 * @param windowWidth initial window width in pixels
 * @param windowHeight initial window height in pixels
 * @param vsync whether to wait for vertical sync
 * @param backgroundColor colour the frame is cleared to
 * @param palette placeholder colour per entity look, until sprites exist
 * @param terrainPalette placeholder colour per kind of ground, and how far apart regions are shaded
 * @param stateTint how far to shift that colour for what the entity is currently doing
 * @param chargeMark how to draw an entity that is filling up towards something
 * @param facingMark how to draw which way an entity is pointing
 * @param chargedMark how to draw a unit whose gun goes off where its shots land
 * @param nestMark how to draw a hole in the ground
 * @param orderMark how to draw a unit that is acting on an order
 * @param heatMark how to draw how hot the gun a unit is holding has become
 * @param camera how the view follows what it is watching, and how far out it may sit
 */
public record GraphicConfig(
        float virtualWidth,
        float virtualHeight,
        int windowWidth,
        int windowHeight,
        boolean vsync,
        Color backgroundColor,
        Palette palette,
        TerrainPalette terrainPalette,
        StateTint stateTint,
        ChargeMark chargeMark,
        NestMark nestMark,
        FacingMark facingMark,
        ChargedMark chargedMark,
        OrderMark orderMark,
        HeatMark heatMark,
        CameraDirector.Settings camera) {

    private static final String FILE = "graphic.properties";

    public GraphicConfig {
        if (virtualWidth <= 0f || virtualHeight <= 0f) {
            throw new IllegalArgumentException("virtual size must be positive");
        }
        if (windowWidth < 1 || windowHeight < 1) {
            throw new IllegalArgumentException("window size must be positive");
        }
    }

    /** Loads rendering config, falling back to defaults for anything missing or malformed. */
    public static GraphicConfig load() {
        GameProperties properties = GameProperties.load(FILE);
        return new GraphicConfig(
                properties.getFloat("graphic.virtualWidth", 160f),
                properties.getFloat("graphic.virtualHeight", 90f),
                properties.getInt("graphic.windowWidth", 1280),
                properties.getInt("graphic.windowHeight", 720),
                properties.getBoolean("graphic.vsync", true),
                color(properties, "graphic.backgroundColor", "101018ff"),
                palette(properties),
                terrainPalette(properties),
                stateTint(properties),
                chargeMark(properties),
                nestMark(properties),
                facingMark(properties),
                chargedMark(properties),
                orderMark(properties),
                heatMark(properties),
                camera(properties));
    }

    /**
     * One colour per look, read from {@code graphic.color.<look>} and keyed by the enum's own name,
     * plus any variants at {@code graphic.color.<look>.<n>}.
     *
     * <p>A variant is a second appearance for the same role - the commander has one per player, so
     * four of them on a field are four colours rather than four identical circles (pillar 5). A look
     * with no variant keys is one colour, which is all but one of them.
     */
    @SuppressWarnings("EnumOrdinal")
    private static Palette palette(GameProperties properties) {
        Look[] looks = Look.values();
        Color[][] colors = new Color[looks.length][];
        for (Look look : looks) {
            String key = "graphic.color." + look.name().toLowerCase(Locale.ROOT);
            Color ordinary = color(properties, key, fallbackFor(look));
            Color[] variants = new Color[Players.MAX];
            variants[0] = ordinary;
            for (int variant = 1; variant < Players.MAX; variant++) {
                variants[variant] = color(properties, key + "." + variant, hex(ordinary));
            }
            colors[look.ordinal()] = variants;
        }
        return new Palette(colors);
    }

    /** A colour back as the eight hex digits {@link #color} expects, so a fallback can be a colour. */
    private static String hex(Color color) {
        return color.toString();
    }

    /**
     * The colour a look falls back to with no properties file at all.
     *
     * <p>Deliberately an exhaustive switch rather than a lookup with a default: adding a look fails
     * this compile until somebody decides what it should look like, which is the cheapest possible
     * reminder that a new kind of thing needs to be visible (pillar 5).
     */
    private static String fallbackFor(Look look) {
        return switch (look) {
            case COMMANDER -> "6ee7ffff";
            case TROOPER -> "4fb8d8ff";
            case GRUNT -> "ff5d5dff";
            case BRUTE -> "b53a5aff";
            case SPITTER -> "a05cf0ff";
            case RUNNER -> "ff9a4aff";
            case CONTROL_POINT -> "2b3a52ff";
            case POST -> "3a5230ff";
            case NEST -> "6b4a2aff";
            case TURRET -> "3f7fa8ff";
            case PROJECTILE -> "ffe066ff";
            case SLUG -> "fff3c4ff";
            case FLAME -> "ff7a2eff";
            case PELLET -> "ffd24dff";
            case TURRET_SHOT -> "7fd8ffff";
            case SPIT -> "d08cffff";
            case CHARGED_SPIT -> "ff8ce0ff";
            case STRIKE -> "ffffffff";
            case GRENADE -> "c07820ff";
            case BLAST -> "ffb03aff";
            case OBSTACLE -> "4a4a5eff";
            case MUSHROOM -> "8fd97fff";
            case FLOWER -> "e07be0ff";
            case SUPPLY_DROP -> "9fd8f0ff";
            case ARMOURY_RAILGUN -> "d8a04aff";
            case ARMOURY_FLAMER -> "ff7a2eff";
            case ARMOURY_SHOTGUN -> "4ad8a0ff";
            case BARREL -> "ff3a10ff";
            case RABBIT -> "f0f0f0ff";
            case MENDING -> "4ce88aff";
            case HEAT -> "ffc24aff";
            case OVERHEAT -> "ff4a2aff";
            case GLEAM -> "ffffffff";
            case NONE -> "ff00ffff";
        };
    }

    /**
     * One colour per kind of ground, read from {@code graphic.terrain.<name>}, plus how far apart two
     * regions of the same ground may be shaded.
     */
    @SuppressWarnings("EnumOrdinal")
    private static TerrainPalette terrainPalette(GameProperties properties) {
        Terrain[] terrains = Terrain.values();
        Color[] colors = new Color[terrains.length];
        for (Terrain terrain : terrains) {
            colors[terrain.ordinal()] = color(
                    properties, "graphic.terrain." + terrain.name().toLowerCase(Locale.ROOT), fallbackFor(terrain));
        }
        return new TerrainPalette(
                colors,
                properties.getFloat("graphic.region.variance", 0.06f),
                properties.getFloat("graphic.tile.variance", 0.022f),
                properties.getInt("graphic.tile.shades", 4),
                properties.getInt("graphic.tile.patch", 3));
    }

    /**
     * The colour a kind of ground falls back to with no properties file at all. Exhaustive for the
     * same reason {@link #fallbackFor(Look)} is: a new terrain fails this compile until somebody has
     * decided what it looks like.
     */
    private static String fallbackFor(Terrain terrain) {
        return switch (terrain) {
            case PLAIN -> "1c2130ff";
            case DUST -> "2a2620ff";
            case ORGANIC -> "16241cff";
            case ROCK -> "343442ff";
        };
    }

    private static StateTint stateTint(GameProperties properties) {
        return new StateTint(
                properties.getFloat("graphic.state.unaware", -0.4f),
                properties.getFloat("graphic.state.windingUp", 0.85f),
                properties.getFloat("graphic.state.airborne", 0.5f),
                properties.getFloat("graphic.state.grappling", -0.3f),
                properties.getFloat("graphic.state.staggered", 0.35f),
                properties.getFloat("graphic.state.dying", -0.7f),
                properties.getFloat("graphic.state.casualty", 0.6f),
                properties.getFloat("graphic.state.crewed", 0.45f));
    }

    private static ChargeMark chargeMark(GameProperties properties) {
        return new ChargeMark(
                properties.getFloat("graphic.charge.scale", 0.55f), properties.getFloat("graphic.charge.tint", 0.8f));
    }

    /**
     * How to draw a hole in the ground, under {@code graphic.nest}. Rings rather than one circle,
     * because dug ground drawn as a body reads as an enormous slow creature.
     */
    private static NestMark nestMark(GameProperties properties) {
        return new NestMark(
                properties.getInt("graphic.nest.shells", 4), properties.getFloat("graphic.nest.darken", 0.55f));
    }

    private static FacingMark facingMark(GameProperties properties) {
        return new FacingMark(
                properties.getFloat("graphic.facing.length", 1.35f),
                properties.getFloat("graphic.facing.width", 0.34f),
                properties.getFloat("graphic.facing.tint", 0.55f));
    }

    /**
     * How to draw a creature carrying an explosive, under {@code graphic.charged}. Its own colour
     * rather than a shift of the body's: it has to be findable at a glance in a line of otherwise
     * identical creatures.
     */
    private static ChargedMark chargedMark(GameProperties properties) {
        return new ChargedMark(
                properties.getFloat("graphic.charged.scale", 1.35f),
                color(properties, "graphic.charged.color", "ff3a10ff"));
    }

    private static OrderMark orderMark(GameProperties properties) {
        return new OrderMark(
                properties.getFloat("graphic.order.scale", 1.55f), properties.getFloat("graphic.order.tint", 0.75f));
    }

    private static HeatMark heatMark(GameProperties properties) {
        return new HeatMark(
                properties.getFloat("graphic.heat.scale", 1.7f),
                properties.getFloat("graphic.heat.width", 0.28f),
                properties.getFloat("graphic.heat.spreadDegrees", 150f),
                properties.getInt("graphic.heat.segments", 14));
    }

    /**
     * How the view behaves. Rendering only - none of it can reach the simulation, and a camera setting
     * that changed what the game does would be a bug rather than a feature.
     */
    private static CameraDirector.Settings camera(GameProperties properties) {
        return new CameraDirector.Settings(
                properties.getFloat("graphic.camera.deadZone", 6f),
                properties.getFloat("graphic.camera.smoothing", 8f),
                properties.getFloat("graphic.camera.lookAhead", 14f),
                properties.getFloat("graphic.camera.leadSmoothing", 2f),
                properties.getFloat("graphic.camera.nearZoom", 1f),
                properties.getFloat("graphic.camera.farZoom", 2.2f),
                properties.getFloat("graphic.camera.zoomSmoothing", 7f),
                properties.getFloat("graphic.camera.fitMargin", 1.25f),
                properties.getFloat("graphic.camera.shakeScale", 1f),
                properties.getFloat("graphic.camera.shakeCycles", 3f));
    }

    /** Falls back to the given hex when the configured value is missing or not a colour. */
    private static Color color(GameProperties properties, String key, String fallback) {
        String raw = properties.getString(key, fallback);
        try {
            return Color.valueOf(raw);
        } catch (RuntimeException e) {
            Log.error("graphic.properties: " + key + "='" + raw + "' is not a colour; using " + fallback, e);
            return Color.valueOf(fallback);
        }
    }
}
