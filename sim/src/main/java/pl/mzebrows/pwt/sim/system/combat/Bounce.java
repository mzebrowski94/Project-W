package pl.mzebrows.pwt.sim.system.combat;

import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * How something in flight comes off what it runs into: the one copy of it, for a thrown grenade (G0019)
 * and for a shot that bounces. A bounce changes direction, never speed.
 */
final class Bounce {

    /** How far short of the face something is put, so the point it ends on is on the open side of it. */
    private static final float SHORT_OF_THE_FACE = 1e-3f;

    private Bounce() {}

    /**
     * Puts something that has flown into rock back where its path met the face: a shot that ends there, a limpet
     * that sticks there (G0100). It is walked from where it stood last tick - or, for one fired with its muzzle
     * already in the wall, from a tile further back along its line, which is inside whoever fired it. Left where it
     * is when no open ground lies behind, or when it is not moving.
     */
    static void toTheFace(Solid map, Position position, PreviousPosition previous, Velocity velocity) {
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        if (speed <= 0f) {
            return;
        }
        float directionX = velocity.x / speed;
        float directionY = velocity.y / speed;
        float fromX = previous.x;
        float fromY = previous.y;
        if (!map.passableAt(fromX, fromY)) {
            fromX -= directionX * map.map().tileSize();
            fromY -= directionY * map.map().tileSize();
        }
        float spanX = position.x - fromX;
        float spanY = position.y - fromY;
        float open = map.passableFraction(fromX, fromY, position.x, position.y);
        if (open <= 0f) {
            return;
        }
        position.set(
                fromX + spanX * open - directionX * SHORT_OF_THE_FACE,
                fromY + spanY * open - directionY * SHORT_OF_THE_FACE);
    }

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
    static boolean offRock(Solid map, Position at, PreviousPosition from, Velocity velocity) {
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
    static float clamp(float value, float min, float max) {
        if (min >= max) {
            return (min + max) * 0.5f;
        }
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
