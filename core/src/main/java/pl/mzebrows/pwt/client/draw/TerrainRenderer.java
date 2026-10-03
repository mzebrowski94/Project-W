package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.Log;
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
    private final GroundDetail detail;
    private final ShapeRenderer shapes = new ShapeRenderer();

    private @Nullable TileMap map;

    /** One colour per terrain per region, resolved when the map is bound. Indexed terrain-major. */
    private Color[] resolved = new Color[0];

    /**
     * The hatch drawn over a thicket, and the rim drawn round rock, in the same index space.
     *
     * <p>Both are the tile's own colour mixed part of the way towards a mark colour, worked out here
     * rather than blended at draw time - see {@link GroundDetail}. A mark therefore sits on its own
     * shade of ground, so a thicket that crosses a region boundary still reads as one thicket.
     */
    private Color[] hatch = new Color[0];

    private Color[] rim = new Color[0];

    private int regionCount;
    private int shadeCount = 1;

    private boolean disposed;

    public TerrainRenderer(Viewport viewport, TerrainPalette palette, GroundDetail detail) {
        this.viewport = viewport;
        this.palette = palette;
        this.detail = detail;
    }

    /** Points the renderer at a map. Call again after a restart - a new world is a new map. */
    public void bind(TileMap tileMap) {
        this.map = tileMap;
        this.regionCount = Math.max(1, tileMap.regionCount());
        this.shadeCount = palette.tileShades();
        Terrain[] terrains = Terrain.values();
        this.resolved = new Color[terrains.length * regionCount * shadeCount];
        this.hatch = new Color[resolved.length];
        this.rim = new Color[resolved.length];
        for (Terrain terrain : terrains) {
            for (int region = 0; region < regionCount; region++) {
                for (int shade = 0; shade < shadeCount; shade++) {
                    int at = index(terrain, region, shade);
                    Color ground = new Color(palette.colorFor(terrain, region, shade));
                    resolved[at] = ground;
                    hatch[at] = mixed(ground, detail.thicketTint(), detail.thicketAlpha());
                    rim[at] = mixed(ground, detail.rockEdge(), detail.rockEdgeAlpha());
                }
            }
        }
    }

    /** A copy of one colour carried part of the way towards another. The half-transparency, baked. */
    private static Color mixed(Color ground, Color toward, float amount) {
        Color mix = new Color(ground);
        mix.r += (toward.r - mix.r) * amount;
        mix.g += (toward.g - mix.g) * amount;
        mix.b += (toward.b - mix.b) * amount;
        mix.a = 1f;
        return mix;
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
                int shade = palette.shadeStepFor(runStart, row);
                int runEnd = runStart;
                // The run now breaks on the tile shade as well, which is what keeps per-tile
                // variation and run-merging from being a choice between two. A handful of shades
                // means a run breaks a few times more often over a few hundred visible tiles.
                while (runEnd + 1 <= lastCol
                        && current.terrainAtTile(runEnd + 1, row) == terrain
                        && current.regionAtTile(runEnd + 1, row) == region
                        && palette.shadeStepFor(runEnd + 1, row) == shade) {
                    runEnd++;
                }
                shapes.setColor(colorOf(terrain, region, shade));
                shapes.rect(runStart * tile, row * tile, (runEnd - runStart + 1) * tile, tile);
                runStart = runEnd + 1;
            }
        }
        // The marks, after every flat tile rather than tile by tile, so a run is never broken by one.
        // Both passes walk only what the camera can see, which is what keeps a map three screens
        // wide costing what one screen costs.
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = firstCol; col <= lastCol; col++) {
                Terrain terrain = current.terrainAtTile(col, row);
                if (terrain == Terrain.THICKET) {
                    undergrowth(current, col, row, tile);
                } else if (terrain == Terrain.ROCK && detail.rimsRock()) {
                    wallEdges(current, col, row, tile);
                }
            }
        }
        shapes.end();
    }

    /**
     * The hatch over one tile of undergrowth: a small grid of marks, each well inside its own cell.
     *
     * <p>Regular rather than scattered, deliberately. A random-looking texture reads as noise at this
     * size and would have to be hashed off the tile to stay the same between frames; a grid reads as
     * something woven, and what the player has to take from it is "this is not open ground" rather
     * than any particular plant.
     */
    private void undergrowth(TileMap current, int col, int row, float tile) {
        int cells = detail.thicketCells();
        float cell = tile / cells;
        float mark = cell * detail.thicketDot();
        float inset = (cell - mark) * 0.5f;
        shapes.setColor(hatch[index(Terrain.THICKET, region(current, col, row), palette.shadeStepFor(col, row))]);
        float originX = col * tile;
        float originY = row * tile;
        for (int down = 0; down < cells; down++) {
            for (int across = 0; across < cells; across++) {
                shapes.rect(originX + across * cell + inset, originY + down * cell + inset, mark, mark);
            }
        }
    }

    /**
     * The rim on every face of a wall that touches something walkable.
     *
     * <p>Rock and floor were two dark colours a shade apart, and at the pulled-back view that is a
     * map you have to squint at to find the walls in. A lighter edge where the wall <em>stops</em>
     * is what the eye actually reads a wall by, and it costs nothing on the tiles in the middle of
     * one: an outcrop draws its outline and a solid block draws its perimeter.
     *
     * <p>Anything off the map counts as rock, so the border of a level is not outlined against
     * nothing.
     */
    private void wallEdges(TileMap current, int col, int row, float tile) {
        shapes.setColor(rim[index(Terrain.ROCK, region(current, col, row), palette.shadeStepFor(col, row))]);
        float width = tile * detail.rockEdgeWidth();
        float x = col * tile;
        float y = row * tile;
        if (open(current, col, row + 1)) {
            shapes.rect(x, y + tile - width, tile, width);
        }
        if (open(current, col, row - 1)) {
            shapes.rect(x, y, tile, width);
        }
        if (open(current, col - 1, row)) {
            shapes.rect(x, y, width, tile);
        }
        if (open(current, col + 1, row)) {
            shapes.rect(x + tile - width, y, width, tile);
        }
    }

    /** Whether the tile next door is something a unit could stand on. Off the map is not. */
    private static boolean open(TileMap current, int col, int row) {
        return current.inBounds(col, row) && current.terrainAtTile(col, row) != Terrain.ROCK;
    }

    private int region(TileMap current, int col, int row) {
        int region = current.regionAtTile(col, row);
        return region < 0 ? 0 : region % regionCount;
    }

    private Color colorOf(Terrain terrain, int region, int shade) {
        return resolved[index(terrain, region < 0 ? 0 : region % regionCount, shade)];
    }

    @SuppressWarnings("EnumOrdinal")
    private int index(Terrain terrain, int region, int shade) {
        return (terrain.ordinal() * regionCount + region) * shadeCount + shade;
    }

    @Override
    public void dispose() {
        disposed = true;
        map = null;
        shapes.dispose();
    }
}
