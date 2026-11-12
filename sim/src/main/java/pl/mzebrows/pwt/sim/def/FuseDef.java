package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How long a strain lasts on the field before it goes off of itself, if it does (G0083): a <strong>fuse</strong>,
 * running from the moment it is on the field, at the end of which it dies - and goes off, as anything does that goes
 * off by dying (G0018). Drawn as a mine's fuse is, quickening as it runs out (pillar 5).
 *
 * <p>A fuse stands still while the strain has hold of a man: his clock is the one that runs then (its {@link GripDef}),
 * and one that lets go of him starts its fuse afresh.
 *
 * @param seconds how long it lasts. Zero never goes off, which is every strain but one
 */
@With
public record FuseDef(float seconds) {

    public FuseDef {
        Require.notNegative("fuse seconds", seconds);
    }

    /** Never goes off of itself. What every strain but the leech declares. */
    public static FuseDef off() {
        return new FuseDef(0f);
    }

    /** Whether it goes off of itself at all. */
    public boolean enabled() {
        return seconds > 0f;
    }
}
