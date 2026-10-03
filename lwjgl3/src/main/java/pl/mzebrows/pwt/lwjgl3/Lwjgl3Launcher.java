package pl.mzebrows.pwt.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import pl.mzebrows.pwt.client.LaunchOptions;
import pl.mzebrows.pwt.client.PwtGame;
import pl.mzebrows.pwt.client.draw.GraphicConfig;

/** Launches the desktop (LWJGL3) application. */
public final class Lwjgl3Launcher {

    private Lwjgl3Launcher() {}

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) {
            return; // Handles macOS support and helps on Windows.
        }
        GraphicConfig graphicConfig = GraphicConfig.load();
        LaunchOptions options = LaunchOptions.parse(args);
        PwtGame game = new PwtGame(graphicConfig, options, new GlfwPointer());
        Lwjgl3ApplicationConfiguration configuration = configuration(graphicConfig, options);
        // Offline, a lost window opens the pause menu (plan Q7); the game decides, this only tells it.
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
                game.focusLost();
            }
        });
        new Lwjgl3Application(game, configuration);
    }

    private static Lwjgl3ApplicationConfiguration configuration(GraphicConfig graphicConfig, LaunchOptions options) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(options.instanceGiven() ? "Project-W  #" + options.instance() : "Project-W");
        configuration.useVsync(graphicConfig.vsync());
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(graphicConfig.windowWidth(), graphicConfig.windowHeight());
        // Every size, position and pointer in physical pixels: the letterbox is solved in them, so a UI
        // pixel stays a whole number of real pixels however the operating system scales the desktop.
        configuration.setHdpiMode(HdpiMode.Pixels);
        if (options.instanceGiven()) {
            // Copies on one PC side by side, left and right, a row lower for every pair, so each window
            // can be seen and clicked into.
            int screenWidth = Lwjgl3ApplicationConfiguration.getDisplayMode().width;
            int x = options.instance() % 2 == 0 ? 20 : Math.max(20, screenWidth - graphicConfig.windowWidth() - 20);
            configuration.setWindowPosition(x, 60 + options.instance() / 2 * 60);
        }
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
