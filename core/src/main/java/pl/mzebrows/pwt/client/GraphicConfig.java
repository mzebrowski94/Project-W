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
 */
public record GraphicConfig(
        float virtualWidth,
        float virtualHeight,
        int windowWidth,
        int windowHeight,
        boolean vsync,
        Color backgroundColor) {

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
                Color.valueOf(properties.getString("graphic.backgroundColor", "101018ff")));
    }
}
