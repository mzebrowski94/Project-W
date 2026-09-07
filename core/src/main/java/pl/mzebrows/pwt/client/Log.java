package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import org.jspecify.annotations.Nullable;

/**
 * Logging facade over {@code Gdx.app}, usable before the application exists (the launcher reads
 * config before libGDX starts) and in plain unit tests.
 *
 * <p>Never call this from a per-frame or per-entity loop.
 */
public final class Log {

    private static final String TAG = "PWT";

    private Log() {}

    /** Lifecycle events: startup, shutdown, screen changes. */
    public static void info(String message) {
        Application app = Gdx.app;
        if (app == null) {
            System.out.println("[INFO ] " + TAG + ": " + message);
        } else {
            app.log(TAG, message);
        }
    }

    /** Initialisation detail and resolved configuration. */
    public static void debug(String message) {
        Application app = Gdx.app;
        if (app == null) {
            System.out.println("[DEBUG] " + TAG + ": " + message);
        } else {
            app.debug(TAG, message);
        }
    }

    /** Failures, always with the cause when there is one. */
    public static void error(String message, @Nullable Throwable cause) {
        Application app = Gdx.app;
        if (app == null) {
            System.err.println("[ERROR] " + TAG + ": " + message);
            if (cause != null) {
                cause.printStackTrace(System.err);
            }
        } else if (cause == null) {
            app.error(TAG, message);
        } else {
            app.error(TAG, message, cause);
        }
    }
}
