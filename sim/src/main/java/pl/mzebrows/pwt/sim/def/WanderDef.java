package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How a unit that has noticed nobody passes the time.
 *
 * <p>Waypoint wandering: pick somewhere inside a patch, walk to it, stand a moment, pick again. It is
 * the cheapest idle that still reads as an animal rather than as a paused entity, and every number in
 * it is per strain, so a twitchy scout and a lumbering sentry are two blocks of properties and no
 * code.
 *
 * <p>The patch is anchored where the unit was spawned rather than to a region of the map, so a
 * scenario places idle behaviour by placing the unit, and nothing has to consult the terrain to know
 * where something belongs. Losing the commander therefore means walking home, which is what makes a
 * disengagement read as one.
 *
 * @param enabled whether this strain wanders at all; a unit configured off stands where it was put
 * @param radius how far from its spawn point it will roam, in world units
 * @param speedFactor fraction of its own speed it moves at while idle. Below one on purpose - a unit
 *     that ambles is visibly not yet coming for you, which is half of what makes noticing readable
 * @param arriveRadius how close counts as having got there, in world units. Without it a unit
 *     overshoots its waypoint and turns back and forth on the spot forever
 * @param pauseMinSeconds shortest stand-still after arriving
 * @param pauseMaxSeconds longest stand-still after arriving. Drawn from the simulation's seeded wander
 *     stream, so idling varies between units and not between runs
 * @param waypointTimeoutSeconds how long it will keep trying to reach one spot before choosing
 *     another. What stops a unit wedged behind an obstacle leaning into it for the rest of the level
 */
@With
public record WanderDef(
        boolean enabled,
        float radius,
        float speedFactor,
        float arriveRadius,
        float pauseMinSeconds,
        float pauseMaxSeconds,
        float waypointTimeoutSeconds) {

    public WanderDef {
        Require.notNegative("wander radius", radius);
        Require.notNegative("wander speedFactor", speedFactor);
        Require.positive("wander arriveRadius", arriveRadius);
        Require.notNegative("wander pauseMinSeconds", pauseMinSeconds);
        Require.notNegative("wander pauseMaxSeconds", pauseMaxSeconds);
        Require.positive("wander waypointTimeoutSeconds", waypointTimeoutSeconds);
        if (pauseMaxSeconds < pauseMinSeconds) {
            throw new IllegalArgumentException("wander pauseMaxSeconds must not be below pauseMinSeconds");
        }
    }

    /** A strain that stands where it was put. */
    public static WanderDef off() {
        return new WanderDef(false, 0f, 0f, 1f, 0f, 0f, 1f);
    }
}
