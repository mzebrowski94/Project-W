package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.map.TileRect;

/**
 * Where a finale's waves come onto the field (G0064): a named rect of tiles, written {@code name dx dy width height},
 * its south-west corner an offset from the anchor. A wave names its mouth.
 *
 * @param name what a wave line calls it
 * @param dx columns east of the anchor, of its west edge
 * @param dy rows north of the anchor, of its south edge
 * @param width how many columns across, at least one
 * @param height how many rows up, at least one
 */
public record Mouth(String name, int dx, int dy, int width, int height) {

    public Mouth {
        if (name.isBlank() || width < 1 || height < 1) {
            throw new IllegalArgumentException(
                    "a mouth is named and at least one tile, was '" + name + "' " + width + "x" + height);
        }
    }

    /** The same mouth with the set piece flipped north-south. */
    public Mouth flipped() {
        TileRect flipped = Offset.flipped(new TileRect(dx, dy, width, height));
        return new Mouth(name, flipped.col(), flipped.row(), width, height);
    }

    /** Its tiles, on ground anchored here. */
    public TileRect from(TileRect anchor) {
        return new TileRect(anchor.col() + dx, anchor.row() + dy, width, height);
    }
}
