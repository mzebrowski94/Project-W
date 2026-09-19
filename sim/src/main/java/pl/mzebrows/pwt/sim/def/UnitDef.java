package pl.mzebrows.pwt.sim.def;

import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * One strain of unit, as numbers.
 *
 * <p>This is the definition a chasing unit is built from at spawn and rebuilt from when it evolves.
 * Two strains differ only in the values here, which is why a grunt and a brute need no code between
 * them: spawning reads a definition, evolving reads a different one, and every system in between is
 * looking at the same components either way.
 *
 * @param radius circular body, in world units
 * @param speed world units per second; slower than the commander, or there is no escaping them
 * @param count how many of this strain a scenario spawns. Scenario-facing rather than a property of
 *     the strain itself, but it belongs to the same block in {@code game.properties}
 * @param health damage one absorbs before dying
 * @param contactDamage what it does to whatever it touches on the other side
 * @param attackArcDegrees how wide an arc in front of it its claws reach into, centred on the way it
 *     is going. {@link PerceptionDef#ALL_ROUND} bites anything it touches, which is what every strain
 *     did before there was an arc; anything less leaves a blind spot behind it. Generous on purpose -
 *     a lunging creature is not precise, and a blind spot narrow enough to rely on would turn every
 *     fight into a dance round behind things
 * @param attackCooldownSeconds how long before it can strike again
 * @param victimInvulnerableSeconds how long its victim is protected afterwards, so a crowd cannot
 *     delete a unit in a single tick
 * @param lockSeconds how long a landed blow holds both it and its victim still - the window the kill
 *     animation will play in
 * @param deathThroesSeconds how long its body stays on the field after a killing blow
 * @param feedToEvolve how much it must consume before it turns into {@link #evolvesTo}. Zero means
 *     eating alone never changes it, though an item that names a strain still can
 * @param evolvesTo what it becomes when it has fed enough, or null for a strain with nowhere to go
 * @param perception how far it can notice the commander, and how far he must get to be lost again
 * @param wander how it passes the time when it has noticed nobody
 * @param avoidance how far ahead it looks for things in its way, and how hard it leans off them
 * @param leap how it closes the last of the distance
 * @param ranged how it shoots, if it shoots at all
 * @param approach how wide of whatever it is hunting it closes, if it spreads out at all. What stops
 *     a pack converging into a queue on one man
 * @param weave how hard it refuses to walk in a straight line at you, if it refuses at all
 * @param dodge how it answers a shot it can see coming, if it answers one at all. The same
 *     definition a rabbit carries: stepping aside from a bullet is one behaviour, not one per side
 * @param chances how often each occasional behaviour happens for this strain. Starts as the game's
 *     own table and is overridden per strain in {@code game.properties}, which is how a shooter can
 *     be far hungrier for a barrel than a grunt with no branch anywhere asking what it is
 * @param look what a renderer draws it as
 */
@With
public record UnitDef(
        float radius,
        float speed,
        int count,
        float health,
        float contactDamage,
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
        RangedDef ranged,
        ApproachDef approach,
        WeaveDef weave,
        DodgeDef dodge,
        Chances chances,
        Look look) {

    public UnitDef {
        Require.positive("unit radius", radius);
        Require.positive("unit speed", speed);
        Require.notNegative("unit count", count);
        Require.positive("unit health", health);
        Require.notNegative("unit contactDamage", contactDamage);
        if (attackArcDegrees <= 0f || attackArcDegrees > PerceptionDef.ALL_ROUND) {
            throw new IllegalArgumentException("unit attackArcDegrees must be in (0, 360], was " + attackArcDegrees);
        }
        Require.notNegative("unit attackCooldownSeconds", attackCooldownSeconds);
        Require.notNegative("unit victimInvulnerableSeconds", victimInvulnerableSeconds);
        Require.notNegative("unit lockSeconds", lockSeconds);
        Require.notNegative("unit deathThroesSeconds", deathThroesSeconds);
        Require.notNegative("unit feedToEvolve", feedToEvolve);
    }

    /**
     * The smallest dot product between the way it is going and the direction to something for its
     * claws to reach that thing.
     *
     * <p>The same trade {@code PerceptionDef.arcCosine()} makes, for the same reason: converted once
     * at spawn, so the per-contact test is a dot product and a comparison with no trigonometry in the
     * tick.
     */
    public float attackArcCosine() {
        return (float) StrictMath.cos(StrictMath.toRadians(attackArcDegrees * 0.5));
    }

    /** True if feeding alone can turn this strain into another one. */
    public boolean canEvolveByFeeding() {
        return feedToEvolve > 0 && evolvesTo != null;
    }
}
