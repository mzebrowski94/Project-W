package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * Where on a strain gunfire glances off: an arc in front of it, the head (G0045).
 *
 * <p><strong>Which side a shot arrives from, not where on the body it lands.</strong> A shot coming at
 * the creature from inside the arc - its flight pointing back into the way the creature faces - does
 * nothing and is spent; one from the flank or behind lands as it would on anything. Only gunfire:
 * a blast, fire and a sweep land wherever they land, so a fist in the face is how its head is hurt.
 * The arc is centred on the {@code Facing} the renderer draws the head along, so the head the player
 * sees is the head that is armoured (pillar 5).
 *
 * @param enabled whether any of it is armoured; one configured off takes a shot from anywhere, which
 *     is what every strain but one declares
 * @param headArcDegrees how wide the armoured arc is, centred on the way it faces
 */
@With
public record ArmourDef(boolean enabled, float headArcDegrees) {

    public ArmourDef {
        if (enabled && (headArcDegrees <= 0f || headArcDegrees > PerceptionDef.ALL_ROUND)) {
            throw new IllegalArgumentException(
                    "armour headArcDegrees must be in (0, 360] when enabled, was " + headArcDegrees);
        }
    }

    /** Nothing armoured. What every strain but one declares. */
    public static ArmourDef off() {
        return new ArmourDef(false, 0f);
    }

    /**
     * The smallest dot product between the way it faces and the way back along a shot for the shot to
     * glance off. Computed once at spawn.
     */
    public float arcCosine() {
        return (float) StrictMath.cos(StrictMath.toRadians(headArcDegrees * 0.5));
    }
}
