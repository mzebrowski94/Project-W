package pl.mzebrows.pwt.client;

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

    private final Color[] byLook;

    /** Takes one colour per {@link Look}, indexed by ordinal. Refuses a palette with a hole in it. */
    public Palette(Color[] byLook) {
        if (byLook.length != Look.values().length) {
            throw new IllegalArgumentException(
                    "palette needs one colour per look, got " + byLook.length + " of " + Look.values().length);
        }
        this.byLook = byLook.clone();
    }

    /** The colour to draw a given look in, before anything it is doing is taken into account. */
    public Color colorFor(Look look) {
        return byLook[look.ordinal()];
    }
}
