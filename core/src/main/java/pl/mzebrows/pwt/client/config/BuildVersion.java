package pl.mzebrows.pwt.client.config;

import pl.mzebrows.pwt.sim.ConfigBinder;

/**
 * Which copy of the game this is, for a person to read: the build version, stamped by {@code
 * :lwjgl3:generateBuildVersion} from the commit it was built from - {@code 2026.11.13-b412b68}, with {@code
 * -dirty} when the tree held anything uncommitted. The packaged zip carries the same words in its name and
 * the main menu shows them, so a playtester's report says which build it is about.
 *
 * <p>Not the build id ({@code BuildId}), which hashes the sources that decide a tick and decides who may
 * play together: two builds of one version always share an id, and two versions often do.
 */
public final class BuildVersion {

    private static final String RESOURCE = "pwt-version.properties";
    private static final String KEY = "build.version";

    /** What a copy nobody stamped calls itself - one started without the launcher's build. */
    static final String UNSTAMPED = "dev";

    private BuildVersion() {}

    /** This copy's build version, or {@code dev} when it was never stamped. */
    public static String current() {
        return Holder.VERSION;
    }

    /** The version a stamp's text names, or {@code dev} if it names none. */
    static String parse(String text) {
        String version = ConfigBinder.parse(text).getOrDefault(KEY, "").trim();
        return version.isEmpty() ? UNSTAMPED : version;
    }

    private static final class Holder {
        static final String VERSION = parse(GameProperties.text(RESOURCE));
    }
}
