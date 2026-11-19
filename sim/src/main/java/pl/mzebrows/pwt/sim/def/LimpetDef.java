package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * What a gun that throws a <strong>limpet</strong> throws (G0100): a thing that sticks to the first rock, door,
 * scenery or alien it touches - or to the ground where its reach runs out - {@code calibrates} there, and goes off by
 * dying, small before it has calibrated and big after. Its flight is the gun's own: speed, size and reach.
 *
 * @param calibrateSeconds how long it must be stuck before its big blast is ready
 * @param blastRadius the circle it leaves set off before then - in flight, or still calibrating. Zero is a gun that
 *     throws no limpet, which is every gun but the detonator
 * @param blastHarm what that circle does to a man (G0053)
 * @param calibratedBlastRadius the circle it leaves set off once calibrated
 * @param calibratedBlastHarm what that one does to a man
 */
@With
public record LimpetDef(
        float calibrateSeconds,
        float blastRadius,
        Harm blastHarm,
        float calibratedBlastRadius,
        Harm calibratedBlastHarm) {

    public LimpetDef {
        Require.notNegative("limpet calibrateSeconds", calibrateSeconds);
        Require.notNegative("limpet blastRadius", blastRadius);
        Require.notNegative("limpet calibratedBlastRadius", calibratedBlastRadius);
        if (blastRadius > 0f && calibratedBlastRadius < blastRadius) {
            throw new IllegalArgumentException(
                    "a calibrated limpet goes off bigger, never smaller: calibratedBlastRadius " + calibratedBlastRadius
                            + " under blastRadius " + blastRadius);
        }
    }

    /** A gun that throws no limpet. What every gun but the detonator declares. */
    public static LimpetDef off() {
        return new LimpetDef(0f, 0f, Harm.DOWN, 0f, Harm.DOWN);
    }

    /** Whether this gun throws a limpet at all. */
    public boolean enabled() {
        return blastRadius > 0f;
    }
}
