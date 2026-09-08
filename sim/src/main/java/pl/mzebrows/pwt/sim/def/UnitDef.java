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
 * @param attackCooldownSeconds how long before it can strike again
 * @param victimInvulnerableSeconds how long its victim is protected afterwards, so a crowd cannot
 *     delete a unit in a single tick
 * @param lockSeconds how long a landed blow holds both it and its victim still - the window the kill
 *     animation will play in
 * @param deathThroesSeconds how long its body stays on the field after a killing blow
 * @param feedToEvolve how much it must consume before it turns into {@link #evolvesTo}. Zero means
 *     eating alone never changes it, though an item that names a strain still can
 * @param evolvesTo what it becomes when it has fed enough, or null for a strain with nowhere to go
 * @param leap how it closes the last of the distance
 * @param look what a renderer draws it as
 */
@With
public record UnitDef(
        float radius,
        float speed,
        int count,
        float health,
        float contactDamage,
        float attackCooldownSeconds,
        float victimInvulnerableSeconds,
        float lockSeconds,
        float deathThroesSeconds,
        int feedToEvolve,
        @Nullable UnitKind evolvesTo,
        LeapDef leap,
        Look look) {

    public UnitDef {
        Require.positive("unit radius", radius);
        Require.positive("unit speed", speed);
        Require.notNegative("unit count", count);
        Require.positive("unit health", health);
        Require.notNegative("unit contactDamage", contactDamage);
        Require.notNegative("unit attackCooldownSeconds", attackCooldownSeconds);
        Require.notNegative("unit victimInvulnerableSeconds", victimInvulnerableSeconds);
        Require.notNegative("unit lockSeconds", lockSeconds);
        Require.notNegative("unit deathThroesSeconds", deathThroesSeconds);
        Require.notNegative("unit feedToEvolve", feedToEvolve);
    }

    /** True if feeding alone can turn this strain into another one. */
    public boolean canEvolveByFeeding() {
        return feedToEvolve > 0 && evolvesTo != null;
    }
}
