package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;

/**
 * Draws the ground the level is played on, under everything else.
 *
 * <p>Two things keep this cheap enough not to think about, and both are needed once a map is larger
 * than the view. It <strong>culls to the visible rectangle</strong>, so a map three screens wide
 * costs the same as one that fits; and it <strong>merges runs</strong>, so a row of thirty tiles that
 * draw identically is one rectangle rather than thirty. Neither allocates.
 *
 * <p>Colours are resolved once per map rather than per tile per frame: a map has a handful of regions
 * and a handful of terrains, so every combination fits in one small array built when the map is bound.
 *
 * <p>Not unit-tested, by the same rule as every other class here that holds GL objects - the decision
 * it would be worth testing, what colour a tile is, lives in {@link TerrainPalette} and is tested
 * there. Keep this thin enough to review by eye.
 */
public final class TerrainRenderer implements Disposable {

    private final Viewport viewport;
    private final TerrainPalette palette;
    private final ShapeRenderer shapes = new ShapeRenderer();

    private @Nullable TileMap map;

    /** One colour per terrain per region, resolved when the map is bound. Indexed terrain-major. */
    private Color[] resolved = new Color[0];

    private int regionCount;
    private boolean disposed;

    public TerrainRenderer(Viewport viewport, TerrainPalette palette) {
        this.viewport = viewport;
        this.palette = palette;
    }

    /** Points the renderer at a map. Call again after a restart - a new world is a new map. */
    public void bind(TileMap tileMap) {
        this.map = tileMap;
        this.regionCount = Math.max(1, tileMap.regionCount());
        Terrain[] terrains = Terrain.values();
        this.resolved = new Color[terrains.length * regionCount];
        for (Terrain terrain : terrains) {
            for (int region = 0; region < regionCount; region++) {
                resolved[index(terrain, region)] = new Color(palette.colorFor(terrain, region));
            }
        }
    }

    /** Draws every tile the camera can currently see. */
    public void render() {
        TileMap current = map;
        if (disposed || current == null) {
            if (disposed) {
                Log.error("TerrainRenderer.render called after dispose; nothing drawn", null);
            }
            return;
        }
        viewport.apply();
        Camera camera = viewport.getCamera();
        float zoom = camera instanceof OrthographicCamera orthographic ? orthographic.zoom : 1f;
        float halfWidth = viewport.getWorldWidth() * 0.5f * zoom;
        float halfHeight = viewport.getWorldHeight() * 0.5f * zoom;

        int firstCol = Math.max(0, current.colAt(camera.position.x - halfWidth));
        int lastCol = Math.min(current.cols() - 1, current.colAt(camera.position.x + halfWidth));
        int firstRow = Math.max(0, current.rowAt(camera.position.y - halfHeight));
        int lastRow = Math.min(current.rows() - 1, current.rowAt(camera.position.y + halfHeight));
        if (firstCol > lastCol || firstRow > lastRow) {
            return;
        }

        float tile = current.tileSize();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int row = firstRow; row <= lastRow; row++) {
            int runStart = firstCol;
            while (runStart <= lastCol) {
                Terrain terrain = current.terrainAtTile(runStart, row);
                int region = current.regionAtTile(runStart, row);
                int runEnd = runStart;
                while (runEnd + 1 <= lastCol
                        && current.terrainAtTile(runEnd + 1, row) == terrain
                        && current.regionAtTile(runEnd + 1, row) == region) {
                    runEnd++;
                }
                shapes.setColor(colorOf(terrain, region));
                shapes.rect(runStart * tile, row * tile, (runEnd - runStart + 1) * tile, tile);
                runStart = runEnd + 1;
            }
        }
        shapes.end();
    }

    private Color colorOf(Terrain terrain, int region) {
        int at = index(terrain, region < 0 ? 0 : region % regionCount);
        return resolved[at];
    }

    @SuppressWarnings("EnumOrdinal")
    private int index(Terrain terrain, int region) {
        return terrain.ordinal() * regionCount + region;
    }

    @Override
    public void dispose() {
        disposed = true;
        map = null;
        shapes.dispose();
    }
}
