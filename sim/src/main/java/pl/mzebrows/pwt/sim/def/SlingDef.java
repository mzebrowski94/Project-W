package pl.mzebrows.pwt.sim.def;

import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * What a strain carries on its back and slings at whoever it hunts (G0074): its <strong>riders</strong>, a count and
 * a strain, never an entity until one is slung.
 *
 * <p>A <strong>sling</strong> is told before it leaves - the winding-up tint every leap and barrage shows, standing
 * still - and aimed once, as it leaves, at a place {@link #landBehind} past its target. The rider flies as a leap that
 * nothing can hurt and that passes through bodies; rock and scenery stop it, and it comes down where it stops. A
 * declared stun breaks the wind-up off and the rider stays aboard. Riders are never reloaded; what is still aboard
 * when the strain dies spills round its body, as a destroyed nest's last brood does.
 *
 * @param riders how many it carries when it arrives. Zero slings nothing, which is every strain but one
 * @param rider the strain each rider is, or none for a strain that carries nothing
 * @param minRange how far off its target must be, at least, before it winds up a sling, in world units
 * @param maxRange how far off at most
 * @param windUpSeconds how long it stands and shows the sling before the rider leaves
 * @param restSeconds how long after a sling before it may wind up the next
 * @param speed how fast a slung rider flies, in world units per second
 * @param landBehind how far past its target, along the line it was slung, a rider comes down
 * @param landingSeconds how long a rider that has come down has its claws sheathed: on the field, killable, and
 *     harmless
 * @param spillRadius how far round its body the riders still aboard spill when it dies, in world units
 */
@With
public record SlingDef(
        int riders,
        @Nullable UnitKind rider,
        float minRange,
        float maxRange,
        float windUpSeconds,
        float restSeconds,
        float speed,
        float landBehind,
        float landingSeconds,
        float spillRadius) {

    public SlingDef {
        Require.notNegative("sling riders", riders);
        Require.notNegative("sling minRange", minRange);
        Require.atLeast("sling maxRange", maxRange, minRange, "a sling cannot reach less far than it must");
        Require.notNegative("sling windUpSeconds", windUpSeconds);
        Require.notNegative("sling restSeconds", restSeconds);
        Require.notNegative("sling speed", speed);
        Require.notNegative("sling landBehind", landBehind);
        Require.notNegative("sling landingSeconds", landingSeconds);
        Require.notNegative("sling spillRadius", spillRadius);
        if (riders > 0 && (rider == null || speed <= 0f)) {
            throw new IllegalArgumentException("a strain that carries riders has to name their strain and sling them");
        }
    }

    /** Carries nothing. What every strain but the slinger declares. */
    public static SlingDef off() {
        return new SlingDef(0, null, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
    }

    /** Whether it carries anything to sling at all. */
    public boolean enabled() {
        return riders > 0;
    }
}
