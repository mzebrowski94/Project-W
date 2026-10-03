package pl.mzebrows.pwt.sim.system;

import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * How something in flight comes off what it runs into: the one copy of it, for a thrown grenade (G0019)
 * and for a shot that bounces. A bounce changes direction, never speed.
 */
final class Bounce {

    private Bounce() {}

    /**
     * Mirrors a course about a surface whose outward unit normal is given, and <strong>only while it is
     * still heading into it</strong> - the guard stops the jitter of something non-solid that still
     * overlaps next tick.
     */
    static void reflect(Velocity velocity, float normalX, float normalY) {
        float into = velocity.x * normalX + velocity.y * normalY;
        if (into >= 0f) {
            return;
        }
        velocity.x -= 2f * into * normalX;
        velocity.y -= 2f * into * normalY;
    }

    /**
     * Comes off rock, which is not a body, and says whether it did.
     *
     * <p>Which axis to mirror is decided by asking where it could have got to on each one alone: if only
     * the sideways half of the step was blocked it came off a vertical face, and if neither half was
     * blocked on its own it went diagonally into an inside corner and comes back the way it came.
     *
     * <p>It is put back where it started the tick, so it is not asked again next tick.
     */
    static boolean offRock(TileMap map, Position at, PreviousPosition from, Velocity velocity) {
        if (map.passableAt(at.x, at.y)) {
            return false;
        }
        boolean sideways = !map.passableAt(at.x, from.y);
        boolean along = !map.passableAt(from.x, at.y);
        if (sideways || !along) {
            velocity.x = -velocity.x;
        }
        if (along || !sideways) {
            velocity.y = -velocity.y;
        }
        at.set(from.x, from.y);
        return true;
    }

    /** Comes off the arena's edge, kept a radius inside it, and says whether it did. */
    static boolean offEdge(Position at, float radius, float width, float height, Velocity velocity) {
        boolean came = false;
        if (at.x - radius < 0f || at.x + radius > width) {
            velocity.x = -velocity.x;
            at.x = clamp(at.x, radius, width - radius);
            came = true;
        }
        if (at.y - radius < 0f || at.y + radius > height) {
            velocity.y = -velocity.y;
            at.y = clamp(at.y, radius, height - radius);
            came = true;
        }
        return came;
    }

    /** Guards against an arena narrower than the thing bouncing around inside it. */
    private static float clamp(float value, float min, float max) {
        if (min >= max) {
            return (min + max) * 0.5f;
        }
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
