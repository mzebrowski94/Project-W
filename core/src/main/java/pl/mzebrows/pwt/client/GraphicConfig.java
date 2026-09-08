package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import java.util.Locale;
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
 * @param stateTint how far to shift that colour for what the entity is currently doing
 * @param chargeMark how to draw an entity that is filling up towards something
 */
public record GraphicConfig(
        float virtualWidth,
        float virtualHeight,
        int windowWidth,
        int windowHeight,
        boolean vsync,
        Color backgroundColor,
        Palette palette,
        StateTint stateTint,
        ChargeMark chargeMark) {

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
                stateTint(properties),
                chargeMark(properties));
    }

    /** One colour per look, read from {@code graphic.color.<look>} and keyed by the enum's own name. */
    @SuppressWarnings("EnumOrdinal")
    private static Palette palette(GameProperties properties) {
        Look[] looks = Look.values();
        Color[] colors = new Color[looks.length];
        for (Look look : looks) {
            colors[look.ordinal()] =
                    color(properties, "graphic.color." + look.name().toLowerCase(Locale.ROOT), fallbackFor(look));
        }
        return new Palette(colors);
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
            case GRUNT -> "ff5d5dff";
            case BRUTE -> "b53a5aff";
            case PROJECTILE -> "ffe066ff";
            case SLUG -> "fff3c4ff";
            case STRIKE -> "ffffffff";
            case OBSTACLE -> "4a4a5eff";
            case MUSHROOM -> "8fd97fff";
            case FLOWER -> "e07be0ff";
            case FROZEN_TROOPER -> "9fd8f0ff";
            case ARMOURY -> "d8a04aff";
            case NONE -> "ff00ffff";
        };
    }

    private static StateTint stateTint(GameProperties properties) {
        return new StateTint(
                properties.getFloat("graphic.state.windingUp", 0.85f),
                properties.getFloat("graphic.state.airborne", 0.5f),
                properties.getFloat("graphic.state.grappling", -0.3f),
                properties.getFloat("graphic.state.staggered", 0.35f),
                properties.getFloat("graphic.state.dying", -0.7f));
    }

    private static ChargeMark chargeMark(GameProperties properties) {
        return new ChargeMark(
                properties.getFloat("graphic.charge.scale", 0.55f), properties.getFloat("graphic.charge.tint", 0.8f));
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
