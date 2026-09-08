package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import pl.mzebrows.pwt.sim.SimConfig;

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
 */
public record GraphicConfig(
        float virtualWidth,
        float virtualHeight,
        int windowWidth,
        int windowHeight,
        boolean vsync,
        Color backgroundColor,
        Palette palette,
        StateTint stateTint) {

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
                stateTint(properties));
    }

    private static Palette palette(GameProperties properties) {
        return new Palette(
                color(properties, "graphic.color.commander", "6ee7ffff"),
                color(properties, "graphic.color.enemy", "ff5d5dff"),
                color(properties, "graphic.color.brute", "b53a5aff"),
                color(properties, "graphic.color.projectile", "ffe066ff"),
                color(properties, "graphic.color.strike", "ffffffff"),
                color(properties, "graphic.color.obstacle", "4a4a5eff"),
                color(properties, "graphic.color.unknown", "ff00ffff"));
    }

    private static StateTint stateTint(GameProperties properties) {
        return new StateTint(
                properties.getFloat("graphic.state.windingUp", 0.85f),
                properties.getFloat("graphic.state.airborne", 0.5f),
                properties.getFloat("graphic.state.grappling", -0.3f),
                properties.getFloat("graphic.state.staggered", 0.35f),
                properties.getFloat("graphic.state.dying", -0.7f));
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
