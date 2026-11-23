package pl.mzebrows.pwt.sim;

import java.util.List;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * One kind of biome as data (missions ticket 03, G0008): the ground {@code map} lays, the creatures it is dealt and
 * what else it holds. Counts, never places - where everything stands is placement's (G0068). Read from {@code
 * sim.biome.<name>} in {@code biomes.properties}.
 *
 * @param ground its width, regions and coats, laid by {@code Strip}
 * @param roster its creatures, loose and in holes, every count scaled by {@code Roster.count}
 * @param furniture its points and everything else lying about, never scaled
 */
public record BiomeDef(BiomeGround ground, List<Roster> roster, Furniture furniture) {

    public BiomeDef {
        roster = List.copyOf(roster);
        int clearings = ground.clearings().count();
        if (furniture.sitesInClearings() && (clearings == 0 || clearings + 1 > furniture.sitesMax())) {
            // A last biome lays a point in its approach columns besides its clearings' (G0115): room for it in its
            // sites.
            throw new IllegalArgumentException("a biome whose sites are in its " + clearings
                    + " clearings has at least one, and sites for each and one more, was up to "
                    + furniture.sitesMax());
        }
        if (furniture.clearingPackShare() > 0 && clearings == 0) {
            throw new IllegalArgumentException("a biome with no clearing puts no pack in one");
        }
    }

    /**
     * Refuses a biome that stocks a gun's cache - a gun lying in a biome is a finale's, or out of scope (missions
     * ticket 01). Asked of each pickup's own grant, so a new cache is refused with no line here.
     */
    static void checkStock(Defs<BiomeKind, BiomeDef> biomes, Defs<PickupKind, PickupDef> pickups) {
        for (BiomeKind kind : BiomeKind.values()) {
            for (Stock line : biomes.of(kind).furniture().pickups()) {
                if (line instanceof Stock.Pickup(PickupKind pickup, int _, int _)
                        && pickups.of(pickup).playerGrant() instanceof Grant.Arm) {
                    throw new IllegalArgumentException(
                            "biome " + kind + " stocks " + pickup + ", a gun's cache: a finale's, never a biome's");
                }
            }
        }
    }
}
