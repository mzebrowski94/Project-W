package pl.mzebrows.pwt.sim;

import java.util.List;
import pl.mzebrows.pwt.map.TileRect;

/**
 * One hand-made arrangement of a finale's set piece (G0087, amending G0064): its rock and what stands round the post,
 * every position an offset from the anchor. A finale's block holds a few, each written symmetric or to a pattern its
 * comment names; the finale's stream deals one a mission, and then the flip applies. Never worked out by rules.
 *
 * <p>Laid over what every layout of the finale shares - the ground's {@code pieces} and the hold's positions - so a
 * finale keeps fixed what does not vary, its post, watch and mouths always among them.
 *
 * @param pieces rock, {@code dx dy width height}, each rect's south-west corner an offset
 * @param turrets a gun on a mount each
 * @param mines a mine each
 * @param barrels an explosive barrel each
 * @param pickups what lies about, {@code pickup <kind> dx dy}
 */
public record Layout(
        List<TileRect> pieces, List<Offset> turrets, List<Offset> mines, List<Offset> barrels, List<LairLine> pickups) {

    public Layout {
        pieces = List.copyOf(pieces);
        turrets = List.copyOf(turrets);
        mines = List.copyOf(mines);
        barrels = List.copyOf(barrels);
        pickups = List.copyOf(pickups);
        for (LairLine line : pickups) {
            if (!(line instanceof LairLine.Pickup)) {
                throw new IllegalArgumentException("a layout's pickups are pickups, was " + line);
            }
        }
    }

    /** The same layout flipped north-south, as {@link FinaleDef#flipped()} flips the rest. */
    public Layout flipped() {
        return new Layout(
                pieces.stream().map(Offset::flipped).toList(),
                turrets.stream().map(Offset::flipped).toList(),
                mines.stream().map(Offset::flipped).toList(),
                barrels.stream().map(Offset::flipped).toList(),
                pickups.stream().map(LairLine::flipped).toList());
    }
}
