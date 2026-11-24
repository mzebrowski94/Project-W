package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.input.PlayerOne;

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

    private static final String PLAYER_ONE = "playerOne";

    private static final String HOST_PASSWORD = "hostPassword";

    private static final String RELAY_HOST = "relayHost";

    private static final String RELAY_PORT = "relayPort";

    private final @Nullable Preferences preferences;

    /** What this run set, when there are no preferences to keep it in: a test, or a store that failed to open. */
    private boolean unsavedPrompts = true;

    private @Nullable PlayerOne unsavedPlayerOne;

    private String unsavedHostPassword = "";

    private @Nullable String unsavedRelayHost;

    private @Nullable String unsavedRelayPort;

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

    /**
     * What player one plays with on this computer: what the player switched it to, else what ships ({@code
     * controls.playerOne}). A kept value that names nothing is read as never set.
     */
    public PlayerOne playerOne(PlayerOne shipped) {
        Preferences kept = preferences;
        if (kept == null) {
            PlayerOne unsaved = unsavedPlayerOne;
            return unsaved == null ? shipped : unsaved;
        }
        String name = kept.getString(PLAYER_ONE, "");
        for (PlayerOne device : PlayerOne.values()) {
            if (device.name().equals(name)) {
                return device;
            }
        }
        return shipped;
    }

    /** Keeps what player one plays with, at once - a failed write logged, the run's value kept anyway. */
    public void switchPlayerOne(PlayerOne device) {
        unsavedPlayerOne = device;
        Preferences kept = preferences;
        if (kept == null) {
            return;
        }
        try {
            kept.putString(PLAYER_ONE, device.name());
            kept.flush();
        } catch (RuntimeException e) {
            Log.error("Setting " + PLAYER_ONE + " not saved", e);
        }
    }

    /**
     * The password HOST GAME asked for last, as it was typed - four characters of the room code's alphabet, or
     * empty for none: what the line shows again the next time the page opens (G0101). A game password, not a
     * credential (G0103), so it is kept as plain text beside the other settings.
     */
    public String hostPassword() {
        Preferences kept = preferences;
        return kept == null ? unsavedHostPassword : kept.getString(HOST_PASSWORD, "");
    }

    /** Keeps the password HOST GAME asked for, at once - a failed write logged, the run's value kept anyway. */
    public void hostPassword(String password) {
        unsavedHostPassword = password;
        Preferences kept = preferences;
        if (kept == null) {
            return;
        }
        try {
            kept.putString(HOST_PASSWORD, password);
            kept.flush();
        } catch (RuntimeException e) {
            Log.error("Setting " + HOST_PASSWORD + " not saved", e);
        }
    }

    /**
     * The relay address the player typed on the online page, or null while they never did - then {@code
     * net.relay.host} stands. Empty is an answer too: a relay the player cleared.
     */
    public @Nullable String relayHost() {
        Preferences kept = preferences;
        return kept == null ? unsavedRelayHost : kept.contains(RELAY_HOST) ? kept.getString(RELAY_HOST) : null;
    }

    /** The relay port the player typed, as typed (digits), or null while they never did or left it empty. */
    public @Nullable String relayPort() {
        Preferences kept = preferences;
        String port = kept == null ? unsavedRelayPort : kept.contains(RELAY_PORT) ? kept.getString(RELAY_PORT) : null;
        return port == null || port.isEmpty() ? null : port;
    }

    /** Keeps the relay address the player typed, at once - a failed write logged, the run's value kept anyway. */
    public void relay(String host, String port) {
        unsavedRelayHost = host;
        unsavedRelayPort = port;
        Preferences kept = preferences;
        if (kept == null) {
            return;
        }
        try {
            kept.putString(RELAY_HOST, host);
            kept.putString(RELAY_PORT, port);
            kept.flush();
        } catch (RuntimeException e) {
            Log.error("Settings " + RELAY_HOST + " and " + RELAY_PORT + " not saved", e);
        }
    }
}
