package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * The one place that turns what an entity <em>is</em> into something visible.
 *
 * <p>This is the client half of G0003: the simulation says {@code GRUNT}, and only this class knows
 * that means red. Swapping placeholder circles for sprites replaces the renderer and this mapping,
 * and touches nothing in {@code sim}.
 *
 * <p>What an entity is <em>doing</em> is a separate axis, and lives in {@link StateTint} rather than
 * in another dozen colours here - a brute winding up and a grunt winding up should read as the same
 * thing happening to two different units, which is only true if the state is a modifier.
 *
 * <p>Indexed by ordinal rather than held as a field per look. Strains are data now and there will be
 * more of them; a record component per colour would mean every new one edited this class, its
 * loader and every test that builds a palette. A look with no colour cannot exist - the constructor
 * refuses an incomplete palette, which is the same guarantee {@code UnitDefs} makes about strains.
 *
 * <p>The ordinal is safe to index on because nothing is stored against it: the array is built from
 * {@code Look.values()} in the same process that reads it, so reordering the enum reorders the array.
 */
@SuppressWarnings("EnumOrdinal")
public final class Palette {

    private final Color[][] byLook;

    /**
     * Takes the colours for each {@link Look} in ordinal order: the first is its ordinary one and any
     * after it are its variants, in variant order. Refuses a palette with a hole in it.
     */
    public Palette(Color[][] byLook) {
        if (byLook.length != Look.values().length) {
            throw new IllegalArgumentException(
                    "palette needs colours for every look, got " + byLook.length + " of " + Look.values().length);
        }
        this.byLook = new Color[byLook.length][];
        for (int look = 0; look < byLook.length; look++) {
            if (byLook[look].length == 0) {
                throw new IllegalArgumentException("look " + Look.values()[look] + " has no colour at all");
            }
            this.byLook[look] = byLook[look].clone();
        }
    }

    /** The colour to draw a given look in, before anything it is doing is taken into account. */
    public Color colorFor(Look look) {
        return colorFor(look, 0);
    }

    /**
     * The same, for a look that has several appearances - four commanders, one per player.
     *
     * <p>A variant this look does not declare falls back to its ordinary colour rather than throwing,
     * because the failure that matters here is a player drawn in somebody else's colour and not a
     * missing key in a config file.
     */
    public Color colorFor(Look look, int variant) {
        Color[] variants = byLook[look.ordinal()];
        return variants[variant >= 0 && variant < variants.length ? variant : 0];
    }
}
