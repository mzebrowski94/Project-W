package pl.mzebrows.pwt.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Every word the interface shows, by key, from {@code i18n/ui.properties}. A key the file lacks draws
 * as {@code #key} and is logged once, so a missing word is visible on screen and never a crash.
 *
 * <p>Arguments are {@code {0}}, {@code {1}}. Callers format when a value changes, not every frame.
 */
public final class UiStrings {

    private final GameProperties strings;
    private final Set<String> reported = new HashSet<>();

    private UiStrings(GameProperties strings) {
        this.strings = strings;
    }

    public static UiStrings load() {
        return new UiStrings(GameProperties.load(Assets.UI_STRINGS));
    }

    /** The word for a key, or {@code #key} if there is none. */
    public String get(String key) {
        String value = strings.getString(key, "");
        if (!value.isEmpty()) {
            return value;
        }
        if (reported.add(key)) {
            Log.error("ui.properties has no '" + key + "'; drawing it as #" + key, null);
        }
        return "#" + key;
    }

    /** The word for a key with its arguments filled in. */
    public String format(String key, Object... arguments) {
        String text = get(key);
        for (int i = 0; i < arguments.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(arguments[i]));
        }
        return text;
    }
}
