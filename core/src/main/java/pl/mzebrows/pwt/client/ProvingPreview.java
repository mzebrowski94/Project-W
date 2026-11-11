package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.Disposable;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.draw.LetterboxViewport;
import pl.mzebrows.pwt.client.draw.TerrainRenderer;
import pl.mzebrows.pwt.client.draw.WorldRenderer;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.scenario.Scenarios;
import pl.mzebrows.pwt.ui.layout.Rect;

/**
 * The proving ground page's window (G0060): {@code Scenarios.preview} of the chosen strain, stepped on the
 * menu's own clock and drawn by the world's own renderers into a rect of the screen - so what the player
 * sees here is exactly what he will fight, gait and colour and size.
 *
 * <p>Owns GL resources from the first {@link #show} until {@link #dispose}; a new strain builds a new world,
 * the renderers stay.
 */
final class ProvingPreview implements Disposable {

    /** The most ticks one frame catches up: a frame that stalled must not run the preview for a second. */
    private static final int MOST_TICKS_PER_FRAME = 4;

    private final SimConfig config;
    private final LetterboxViewport viewport = new LetterboxViewport();
    private final WorldRenderer renderer;
    private final TerrainRenderer terrain;

    private @Nullable Simulation world;
    private @Nullable UnitKind showing;
    private float owed;

    ProvingPreview(GraphicConfig graphic, SimConfig config) {
        this.config = config;
        this.renderer = new WorldRenderer(
                viewport,
                graphic.palette(),
                graphic.state(),
                graphic.charge(),
                graphic.bloat(),
                graphic.nest(),
                graphic.facing(),
                graphic.segments(),
                graphic.charged(),
                graphic.order(),
                graphic.heat(),
                graphic.wound(),
                graphic.shield(),
                graphic.crate(),
                graphic.door(),
                graphic.post(),
                graphic.burst(),
                graphic.daze(),
                graphic.tree(),
                graphic.boulder(),
                graphic.sling(),
                graphic.shadow(),
                graphic.pop());
        this.terrain = new TerrainRenderer(viewport, graphic.terrainPalette(), graphic.groundDetail());
    }

    /** Shows this strain, building its world only when it changed - the same strain keeps ambling. */
    void show(UnitKind strain) {
        if (strain == showing) {
            return;
        }
        Simulation built = Scenarios.world(Scenarios.preview(strain), config);
        renderer.bind(built);
        terrain.bind(built.map());
        world = built;
        showing = strain;
        owed = 0f;
    }

    /** Steps the world by {@code delta} and draws all of it into {@code screenPixels}. */
    void render(float delta, Rect screenPixels) {
        Simulation shown = world;
        if (shown == null || screenPixels.isEmpty()) {
            return;
        }
        float tick = config.tickSeconds();
        owed = Math.min(owed + delta, tick * MOST_TICKS_PER_FRAME);
        while (owed >= tick) {
            shown.step();
            owed -= tick;
        }
        TileMap map = shown.map();
        // The whole patch, whatever the window's shape: fit the taller of its two proportions.
        float height = Math.max(map.worldHeight(), map.worldWidth() * screenPixels.height() / screenPixels.width());
        viewport.fit(screenPixels, height);
        if (viewport.getCamera() instanceof OrthographicCamera lens) {
            lens.position.set(map.worldWidth() * 0.5f, map.worldHeight() * 0.5f, 0f);
            lens.update();
        }
        terrain.render();
        renderer.render(owed / tick, null);
    }

    @Override
    public void dispose() {
        renderer.dispose();
        terrain.dispose();
    }
}
