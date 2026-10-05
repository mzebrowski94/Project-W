package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.play.MissionRecords;

/**
 * This computer's {@link MissionRecords}, kept in libGDX preferences of their own beside the machine id's
 * ({@link LocalMachineId}), so they can be deleted apart from it (G0067). An unreadable file is read as empty
 * and a failed write logged: records degrade, play never stops.
 */
final class LocalMissionRecords implements MissionRecords.Store {

    private static final String PREFERENCES = "pl.mzebrows.pwt.missions";

    private final @Nullable Preferences preferences;

    /** What this run kept, when there are no preferences to keep it in: a test, or a store that failed to open. */
    private final Map<String, Long> unsaved = new HashMap<>();

    private LocalMissionRecords(@Nullable Preferences preferences) {
        this.preferences = preferences;
    }

    static MissionRecords load() {
        return MissionRecords.read(new LocalMissionRecords(open()));
    }

    private static @Nullable Preferences open() {
        Application app = Gdx.app;
        if (app == null) {
            return null;
        }
        try {
            return app.getPreferences(PREFERENCES);
        } catch (RuntimeException e) {
            Log.error("Mission records unreadable, read as none", e);
            return null;
        }
    }

    @Override
    public Map<String, ?> kept() {
        Preferences kept = preferences;
        return kept == null ? unsaved : kept.get();
    }

    @Override
    public void keep(String key, long ticks) {
        Preferences kept = preferences;
        if (kept == null) {
            unsaved.put(key, ticks);
            return;
        }
        try {
            kept.putLong(key, ticks);
            kept.flush();
        } catch (RuntimeException e) {
            Log.error("Mission record " + key + " not saved", e);
        }
    }
}
