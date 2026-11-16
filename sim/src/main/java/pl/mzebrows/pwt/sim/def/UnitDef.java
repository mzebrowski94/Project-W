package pl.mzebrows.pwt.sim.def;

import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * One strain of unit, as numbers.
 *
 * <p>A chasing unit is built from this at spawn and rebuilt from another when it evolves (G0008).
 *
 * @param radius circular body, in world units
 * @param speed world units per second; slower than the commander, or there is no escaping them. Zero
 *     is a strain that never walks at all: it stands where it was put, and nothing shoves it (G0048)
 * @param count how many of this strain a scenario spawns
 * @param health damage one absorbs before dying
 * @param contactDamage what it does to whatever it touches on the other side
 * @param attackArcDegrees how wide an arc in front of it its claws reach into, centred on the way it
 *     is going. {@link PerceptionDef#ALL_ROUND} bites anything it touches
 * @param attackCooldownSeconds how long before it can strike again
 * @param victimInvulnerableSeconds how long its victim is protected afterwards
 * @param lockSeconds how long a landed blow holds both it and its victim still
 * @param deathThroesSeconds how long its body stays on the field after a killing blow
 * @param feedToEvolve how much it must consume before it turns into {@link #evolvesTo}. Zero means
 *     eating alone never changes it, though an item that names a strain still can
 * @param evolvesTo what it becomes when it has fed enough, or null for a strain with nowhere to go
 * @param perception how far it can notice the commander, and how far he must get to be lost again
 * @param wander how it passes the time when it has noticed nobody
 * @param avoidance how far ahead it looks for things in its way, and how hard it leans off them
 * @param leap how it closes the last of the distance
 * @param hop how it gets about: at a walk, or in bursts. A gait paces a course, never chooses one
 * @param ranged how it shoots, if it shoots at all
 * @param approach how wide of whatever it is hunting it closes, if it spreads out at all
 * @param weave how hard it refuses to walk in a straight line at you, if it refuses at all
 * @param dodge how it answers a shot it can see coming, if at all; the definition a rabbit carries
 * @param grip what its leap does to a man it lands on, if it does anything but bite him (G0045)
 * @param armour where on it gunfire glances off, if anywhere (G0045)
 * @param barrage the cycle of patterns it shoots, if it is a boss (G0048)
 * @param sling the riders it carries and slings at whoever it hunts, if any (G0074)
 * @param fuse how long it lasts on the field before it goes off of itself, if it does (G0083)
 * @param slither whether it slithers - goes on in wide arcs, through bodies, in parts that its health is shared
 *     between (G0095) - and how
 * @param burrow whether it burrows - a shadow under the ground nothing can hurt, that surfaces under a man and snaps
 *     its maw shut (G0096) - and how
 * @param deathSlicks how many circles of venom it leaves where it dies, whatever kills it (G0076), shaped by the
 *     shared blood block; zero leaves none, and the swarm's toxic blood adds to it
 * @param shockable whether shock builds on it at all (G0059): false for the boss, whose barrage a stun
 *     would not pause (G0048)
 * @param chances how often each occasional behaviour happens for this strain: the shared table, with
 *     this strain's overrides resolved in
 * @param look what a renderer draws it as
 */
@With
public record UnitDef(
        float radius,
        float speed,
        int count,
        int health,
        int contactDamage,
        float attackArcDegrees,
        float attackCooldownSeconds,
        float victimInvulnerableSeconds,
        float lockSeconds,
        float deathThroesSeconds,
        int feedToEvolve,
        @Nullable UnitKind evolvesTo,
        PerceptionDef perception,
        WanderDef wander,
        AvoidanceDef avoidance,
        LeapDef leap,
        HopDef hop,
        RangedDef ranged,
        ApproachDef approach,
        WeaveDef weave,
        DodgeDef dodge,
        GripDef grip,
        ArmourDef armour,
        BarrageDef barrage,
        SlingDef sling,
        FuseDef fuse,
        SlitherDef slither,
        BurrowDef burrow,
        int deathSlicks,
        boolean shockable,
        Chances chances,
        Look look) {

    public UnitDef {
        Require.positive("unit radius", radius);
        Require.notNegative("unit speed", speed);
        Require.notNegative("unit count", count);
        Require.positive("unit health", health);
        Require.notNegative("unit deathSlicks", deathSlicks);
        Require.notNegative("unit contactDamage", contactDamage);
        if (attackArcDegrees <= 0f || attackArcDegrees > PerceptionDef.ALL_ROUND) {
            throw new IllegalArgumentException("unit attackArcDegrees must be in (0, 360], was " + attackArcDegrees);
        }
        Require.notNegative("unit attackCooldownSeconds", attackCooldownSeconds);
        Require.notNegative("unit victimInvulnerableSeconds", victimInvulnerableSeconds);
        Require.notNegative("unit lockSeconds", lockSeconds);
        Require.notNegative("unit deathThroesSeconds", deathThroesSeconds);
        Require.notNegative("unit feedToEvolve", feedToEvolve);
        if (ranged.enabled() && barrage.enabled()) {
            // A barrage puts each pattern's gun in the one pair of hands a unit has.
            throw new IllegalArgumentException(
                    "a strain with a barrage does not also shoot: the two would share one gun");
        }
        if (slither.enabled() && burrow.enabled()) {
            // Each decides the whole of its course; two would fight over one heading.
            throw new IllegalArgumentException("a strain that burrows does not also slither: both steer it whole");
        }
    }

    /**
     * The smallest dot product between the way it is going and the direction to something for its
     * claws to reach that thing.
     *
     * <p>Computed once at spawn, as {@code PerceptionDef.arcCosine()}.
     */
    public float attackArcCosine() {
        return (float) StrictMath.cos(StrictMath.toRadians(attackArcDegrees * 0.5));
    }

    /**
     * True for a strain that never walks: it stands where it was put, the solver pushes whatever it
     * meets out of its way rather than it out of theirs, and a blow does not throw it (G0048).
     */
    public boolean rooted() {
        return speed <= 0f;
    }

    /** True if feeding alone can turn this strain into another one. */
    public boolean canEvolveByFeeding() {
        return feedToEvolve > 0 && evolvesTo != null;
    }
}
