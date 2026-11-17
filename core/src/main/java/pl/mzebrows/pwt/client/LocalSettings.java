package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import org.jspecify.annotations.Nullable;

/**
 * What the player has set on this computer, kept in libGDX preferences of their own beside the mission
 * records' ({@link LocalMissionRecords}), never in the shipped {@code assets/*.properties} ({@code
 * docs/ui/menu_layout_agent.md}, {@code UI:main/controls}). One for the machine, never one a seat. An
 * unreadable file is read as every setting's default and a failed write logged: a setting degrades, play
 * never stops.
 */
public final class LocalSettings {

    private static final String PREFERENCES = "pl.mzebrows.pwt.settings";

    private static final String PROMPTS = "prompts";

    private final @Nullable Preferences preferences;

    /** What this run set, when there are no preferences to keep it in: a test, or a store that failed to open. */
    private boolean unsavedPrompts = true;

    private LocalSettings(@Nullable Preferences preferences) {
        this.preferences = preferences;
    }

    /** This computer's settings, once the application - and so its preferences - exists. */
    static LocalSettings load() {
        return new LocalSettings(open());
    }

    /** Settings kept for this run only, every one at its default: a test's. */
    static LocalSettings unsaved() {
        return new LocalSettings(null);
    }

    private static @Nullable Preferences open() {
        Application app = Gdx.app;
        if (app == null) {
            return null;
        }
        try {
            return app.getPreferences(PREFERENCES);
        } catch (RuntimeException e) {
            Log.error("Settings unreadable, read as the defaults", e);
            return null;
        }
    }

    /**
     * Whether the in-world prompts show - the chips over the subject saying which button and what it does.
     * Only the prompts: never the brackets, never a status. On until the player switches them off.
     */
    public boolean prompts() {
        Preferences kept = preferences;
        return kept == null ? unsavedPrompts : kept.getBoolean(PROMPTS, true);
    }

    /** Switches the prompts on or off, kept at once - a failed write logged, the run's value kept anyway. */
    public void prompts(boolean on) {
        unsavedPrompts = on;
        Preferences kept = preferences;
        if (kept == null) {
            return;
        }
        try {
            kept.putBoolean(PROMPTS, on);
            kept.flush();
        } catch (RuntimeException e) {
            Log.error("Setting " + PROMPTS + " not saved", e);
        }
    }
}
