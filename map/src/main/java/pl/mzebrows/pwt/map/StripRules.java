package pl.mzebrows.pwt.map;

/**
 * What every mission's strip shares, whichever biomes are dealt into it: its height, the {@link #clearance},
 * the apron, and the ridge between two biomes with its passes (missions tickets 03 and 13).
 *
 * @param rows the strip's height in tiles, its rock border included
 * @param apron how far past where a pass comes out of a ridge's face its biome keeps base ground, in tiles
 * @param clearance the narrowest passage left between two rocks anywhere on the strip, in tiles
 * @param ridgeWidth the solid core of a ridge, in columns
 * @param ridgeRough how far a ridge's face may wander into the biome beside it, in columns; zero is a straight face
 * @param passWidth how wide a pass is, in rows
 * @param passesMin the fewest passes through one ridge, at least one
 * @param passesMax the most
 * @param passApart the least rock between two passes through one ridge, in rows
 * @param passJog how many rows a pass may bend on its way through
 * @param snake the least a pass is entered from the row the last ridge's pass came out on, in rows
 */
public record StripRules(
        int rows,
        int apron,
        int clearance,
        int ridgeWidth,
        int ridgeRough,
        int passWidth,
        int passesMin,
        int passesMax,
        int passApart,
        int passJog,
        int snake) {

    public StripRules {
        if (apron < 0 || clearance < 1 || ridgeWidth < 1 || ridgeRough < 0 || passApart < 1 || passJog < 0) {
            throw new IllegalArgumentException("strip rules out of range: " + apron + " apron, " + clearance
                    + " clearance, " + ridgeWidth + " ridge, " + ridgeRough + " rough, " + passApart + " apart, "
                    + passJog + " jog");
        }
        if (passWidth < clearance) {
            throw new IllegalArgumentException(
                    "a pass is at least the clearance wide, was " + passWidth + " under " + clearance);
        }
        if (passesMin < 1 || passesMax < passesMin) {
            throw new IllegalArgumentException(
                    "a ridge has passes from at least 1 up, were " + passesMin + ".." + passesMax);
        }
        if (snake < 0 || (entryRows(rows, passWidth) - 1) / 2 < snake) {
            throw new IllegalArgumentException("a pass cannot always be entered " + snake + " rows from the last: "
                    + rows + " rows hold " + entryRows(rows, passWidth) + " entry rows for a pass " + passWidth
                    + " wide");
        }
    }

    /** How many rows a pass may be entered on: every row it fits above, inside the border. */
    int entryRows() {
        return entryRows(rows, passWidth);
    }

    private static int entryRows(int rows, int passWidth) {
        return rows - 2 - passWidth + 1;
    }
}
