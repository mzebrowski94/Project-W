package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.sim.def.Require;

/**
 * Where what a biome counts is put down, the same in every biome (G0068): each a distance kept, crow-flies, from
 * what went down before it. In world units; a tile is five.
 *
 * @param startKeepOff how far from the start no nest and no creature is put down - above a nest's wake radius, so
 *     nothing wakes on the first tick
 * @param pointApart how far apart two control points stand, across the edge of their bands
 * @param pointKeepOff how far a nest keeps off every control point - above a point's rouse, so starting a capture
 *     wakes no hole
 * @param nestKeepOff how far a nest, and a pack's anchor, keeps off the passes its biome is entered by
 * @param nestApart how far apart two nests stand
 * @param packMin the fewest creatures put down round one anchor
 * @param packMax the most
 * @param packApart how far apart two packs' anchors stand; a pack spreads over half of it
 * @param houseStock how many crates and medkits one house holds at most
 * @param pointReach how far from a control point a crate left over from the houses stands
 * @param pickupKeepOff how far every pickup keeps off every nest
 * @param barrelReach how far from a nest or a pack's anchor a barrel stands
 * @param treeReach how far from a nest's middle the trees a biome stands by it go (G0072)
 * @param rockMargin how far anything that never moves - a nest, a boulder, a tree - stands off rock, and a boulder or
 *     a tree off everything else: wider than the widest thing that must get past it, so it leaves no slot to be stuck in (G0042)
 * @param geyserKeepOff how far a geyser's middle keeps off every control point, every nest and every way into its biome,
 *     the start among them (G0089): its fire is nobody's, so it never erupts where the squad must stand or comes in
 */
public record PlacementRules(
        float startKeepOff,
        float pointApart,
        float pointKeepOff,
        float nestKeepOff,
        float nestApart,
        int packMin,
        int packMax,
        float packApart,
        int houseStock,
        float pointReach,
        float pickupKeepOff,
        float barrelReach,
        float treeReach,
        float rockMargin,
        float geyserKeepOff) {

    public PlacementRules {
        Require.notNegative("placement startKeepOff", startKeepOff);
        Require.notNegative("placement pointApart", pointApart);
        Require.notNegative("placement pointKeepOff", pointKeepOff);
        Require.notNegative("placement nestKeepOff", nestKeepOff);
        Require.notNegative("placement nestApart", nestApart);
        Require.atLeastOne("placement packMin", packMin);
        Require.atLeast("placement packMax", packMax, packMin, "a pack cannot have a smaller most than fewest");
        Require.notNegative("placement packApart", packApart);
        Require.notNegative("placement houseStock", houseStock);
        Require.positive("placement pointReach", pointReach);
        Require.notNegative("placement pickupKeepOff", pickupKeepOff);
        Require.positive("placement barrelReach", barrelReach);
        Require.positive("placement treeReach", treeReach);
        Require.notNegative("placement rockMargin", rockMargin);
        Require.notNegative("placement geyserKeepOff", geyserKeepOff);
    }
}
