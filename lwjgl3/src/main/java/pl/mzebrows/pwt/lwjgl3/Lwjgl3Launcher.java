package pl.mzebrows.pwt.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import pl.mzebrows.pwt.client.GraphicConfig;
import pl.mzebrows.pwt.client.PwtGame;

/** Launches the desktop (LWJGL3) application. */
public final class Lwjgl3Launcher {

    private Lwjgl3Launcher() {}

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) {
            return; // Handles macOS support and helps on Windows.
        }
        GraphicConfig graphicConfig = GraphicConfig.load();
        new Lwjgl3Application(new PwtGame(graphicConfig), configuration(graphicConfig));
    }

    private static Lwjgl3ApplicationConfiguration configuration(GraphicConfig graphicConfig) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Project-W");
        configuration.useVsync(graphicConfig.vsync());
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(graphicConfig.windowWidth(), graphicConfig.windowHeight());
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
