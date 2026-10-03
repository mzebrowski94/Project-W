package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Disposable;
import pl.mzebrows.pwt.client.hud.UiFont;
import pl.mzebrows.pwt.client.hud.UiStrings;

/**
 * Every asset path the game knows, in one place. Nothing else may reference an asset by string
 * literal - that is how filenames drift out of sync with what is actually on disk.
 *
 * <p>Assets that are not present yet are skipped with a warning rather than crashing the game, so
 * gameplay can be built and tested long before the art exists.
 */
public final class Assets implements Disposable {

    /** Packed atlas produced by {@code ./gradlew :core:packTextures}. */
    public static final String GAME_ATLAS = "atlas/game.atlas";

    /** Every word the interface shows ({@link UiStrings}); a classpath resource, like the config files. */
    public static final String UI_STRINGS = "i18n/ui.properties";

    /** Monogram's glyph table, which {@link UiFont} builds the interface's font from. */
    public static final String UI_FONT = "ui/fonts/monogram/monogram-bitmap.json";

    private final AssetManager manager = new AssetManager();

    /** Queues everything the game needs; call {@link #finishLoading()} or poll {@link #update()}. */
    public void queueAll() {
        queueIfPresent(GAME_ATLAS, TextureAtlas.class);
    }

    /** Advances async loading; returns true when the queue is empty. */
    public boolean update() {
        return manager.update();
    }

    /** Blocks until everything queued has loaded. */
    public void finishLoading() {
        manager.finishLoading();
    }

    /** True when the packed atlas exists and has finished loading. */
    public boolean hasAtlas() {
        return manager.isLoaded(GAME_ATLAS, TextureAtlas.class);
    }

    /** The packed atlas; only valid when {@link #hasAtlas()} is true. */
    public TextureAtlas atlas() {
        return manager.get(GAME_ATLAS, TextureAtlas.class);
    }

    private <T> void queueIfPresent(String path, Class<T> type) {
        if (Gdx.files.internal(path).exists()) {
            manager.load(path, type);
        } else {
            Log.info("Asset " + path + " not present yet; rendering will fall back to placeholders");
        }
    }

    @Override
    public void dispose() {
        manager.dispose();
    }
}
