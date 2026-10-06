package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * Where on a strain gunfire glances off: an arc round one end of it - the ant's head (G0045), the sludger's back
 * (G0073).
 *
 * <p><strong>Which side a shot arrives from, not where on the body it lands.</strong> A shot coming at
 * the creature from inside the arc - its flight pointing back into the way the creature faces - does
 * nothing and is spent; one from the flank or behind lands as it would on anything. Only gunfire:
 * a blast, fire and a sweep land wherever they land, so a fist in the face is how its head is hurt.
 * The arc is centred on the {@code Facing} the renderer draws the segments along, turned by {@code centreDegrees},
 * so the end the player sees drawn hard is the end that is armoured (pillar 5).
 *
 * @param enabled whether any of it is armoured; one configured off takes a shot from anywhere, which
 *     is what every strain but the ant and the sludger declares
 * @param arcDegrees how wide the armoured arc is
 * @param centreDegrees where the arc is centred, turned from the way it faces: zero is its head, 180 its back
 */
@With
public record ArmourDef(boolean enabled, float arcDegrees, float centreDegrees) {

    public ArmourDef {
        if (enabled && (arcDegrees <= 0f || arcDegrees > PerceptionDef.ALL_ROUND)) {
            throw new IllegalArgumentException("armour arcDegrees must be in (0, 360] when enabled, was " + arcDegrees);
        }
        if (centreDegrees < 0f || centreDegrees >= PerceptionDef.ALL_ROUND) {
            throw new IllegalArgumentException("armour centreDegrees must be in [0, 360), was " + centreDegrees);
        }
    }

    /** Nothing armoured. What every strain but the ant and the sludger declares. */
    public static ArmourDef off() {
        return new ArmourDef(false, 0f, 0f);
    }

    /**
     * The smallest dot product between the way it faces and the way back along a shot for the shot to
     * glance off. Computed once at spawn.
     */
    public float arcCosine() {
        return (float) StrictMath.cos(StrictMath.toRadians(arcDegrees * 0.5));
    }

    /** The cosine of the turn from its facing to the middle of the arc. Computed once at spawn. */
    public float centreCosine() {
        return (float) StrictMath.cos(StrictMath.toRadians(centreDegrees));
    }

    /** The sine of the same turn, anticlockwise. Computed once at spawn. */
    public float centreSine() {
        return (float) StrictMath.sin(StrictMath.toRadians(centreDegrees));
    }
}
