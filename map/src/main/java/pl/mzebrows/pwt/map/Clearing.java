package pl.mzebrows.pwt.map;

/**
 * One clearing laid (G0115, provisional, for review): a disc of open ground, judged at each tile's centre, round the
 * tile at {@code col, row}. No crag or outcrop is laid in it, and it is reached from the biome's ways in.
 *
 * @param col its centre tile's column
 * @param row its centre tile's row
 * @param radius how far it reaches from that tile's centre, in tiles
 */
public record Clearing(int col, int row, int radius) {

    /** Whether a tile's centre lies within the disc. */
    public boolean contains(int c, int r) {
        int dc = c - col;
        int dr = r - row;
        return dc * dc + dr * dr <= radius * radius;
    }
}
