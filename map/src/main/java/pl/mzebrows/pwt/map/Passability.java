package pl.mzebrows.pwt.map;

/**
 * Whether a tile can be stood on and seen through. The {@link TileMap} answers it from terrain alone; a world that
 * blocks a tile for a while - a shut door (ADR 0022) - answers it from more than the map, and hands itself to the
 * map's walks ({@link TileMap#clearLine(float, float, float, float, Passability)} and the rest), so a line is walked
 * one way whoever is asking.
 */
@FunctionalInterface
public interface Passability {

    /** Whether a unit may stand on this tile, and a line pass through it. False for anything outside the map. */
    boolean passableAtTile(int col, int row);
}
