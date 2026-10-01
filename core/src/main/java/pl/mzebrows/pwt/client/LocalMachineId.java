package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import java.security.SecureRandom;
import pl.mzebrows.pwt.net.session.MachineId;

/**
 * This computer's {@link MachineId}: made up the first time it plays online and kept in its libGDX
 * preferences, so every copy on it and every later run says the same.
 */
final class LocalMachineId {

    private static final String PREFERENCES = "pl.mzebrows.pwt";
    private static final String KEY = "machineId";

    private LocalMachineId() {}

    static String load() {
        Application app = Gdx.app;
        if (app == null) {
            return MachineId.make(new SecureRandom());
        }
        Preferences preferences = app.getPreferences(PREFERENCES);
        String kept = preferences.getString(KEY, "");
        String id = MachineId.keptOrNew(kept, new SecureRandom());
        if (!id.equals(kept)) {
            preferences.putString(KEY, id);
            preferences.flush();
        }
        return id;
    }
}
