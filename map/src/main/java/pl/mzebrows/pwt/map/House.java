package pl.mzebrows.pwt.map;

import java.util.List;

/**
 * Ground enclosed by a {@link Terrain#WALL} one tile thick and entered by its doorways (missions ticket 12): one
 * laid inside a biome, or a gatehouse built into a ridge. What stands in one is placement's.
 *
 * @param walls the house with its walls, which run round this rect's edge
 * @param doorways each a run of tiles in one wall, never at a corner, kept open
 */
public record House(TileRect walls, List<TileRect> doorways) {

    public House {
        if (walls.width() < 3 || walls.height() < 3) {
            throw new IllegalArgumentException("a house has an inside, was " + walls.width() + "x" + walls.height());
        }
        doorways = List.copyOf(doorways);
    }

    /** The ground inside the walls. */
    public TileRect inside() {
        return new TileRect(walls.col() + 1, walls.row() + 1, walls.width() - 2, walls.height() - 2);
    }
}
