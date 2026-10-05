package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.map.TileRect;

/**
 * A tile measured from a finale's anchor ({@code Strip.anchor}), written {@code dx dy}: where one thing of its set
 * piece stands (G0064).
 *
 * @param dx columns east of the anchor
 * @param dy rows north of the anchor
 */
public record Offset(int dx, int dy) {

    /** The same tile with the set piece flipped north-south: nothing turns east-west, so west stays the way in. */
    public Offset flipped() {
        return new Offset(dx, -dy);
    }

    /** The tile itself, on ground anchored here. */
    public TileRect from(TileRect anchor) {
        return TileRect.ofTile(anchor.col() + dx, anchor.row() + dy);
    }

    /**
     * A rect of offsets flipped north-south: its rows mirrored about the anchor's, so its bottom row becomes {@code
     * -(dy + height - 1)}. The one place a rect is flipped - a piece of rock and a mouth alike.
     */
    public static TileRect flipped(TileRect rect) {
        return new TileRect(rect.col(), -(rect.row() + rect.height() - 1), rect.width(), rect.height());
    }
}
