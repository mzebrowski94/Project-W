package pl.mzebrows.pwt.sim.system.query;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * The three tests a unit can declare about what it is able to notice (G0015), in one place.
 *
 * <p><strong>Cheapest first, and every caller runs them in that order:</strong> a squared distance,
 * then a dot product against a facing, then a walk of the tile grid. The expensive one is reached
 * only by something already in range and already in front, which is why a line-of-sight test costs a
 * fraction of what one per unit per tick would - the objection G0011 raised, answered by arithmetic
 * rather than by argument.
 *
 * <p>Here rather than inside one system because two different minds now ask the same questions: an
 * alien deciding whether it has noticed the commander, and a trooper deciding whether it has anything
 * to shoot at. Copying the tests would be how one of them quietly grows a rule the other does not
 * have - and a strain's senses would stop being readable from its definition.
 *
 * <p>Angles are compared as cosines, never as angles. Converting one costs a transcendental, and
 * {@code ArchitectureTest} bans those here because they are specified only to within an ulp and may
 * differ between JVMs, which pillar 6 does not allow.
 */
public final class Sight {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private Sight() {}

    /** Whether the two points are within this distance of one another. */
    public static boolean withinRange(float fromX, float fromY, float toX, float toY, float range) {
        float towardsX = toX - fromX;
        float towardsY = toY - fromY;
        return towardsX * towardsX + towardsY * towardsY <= range * range;
    }

    /**
     * Whether a point lies inside the cone a unit looking this way can notice within.
     *
     * <p>An {@code arcCosine} of {@code -1} notices all round and is beaten by every direction, so a
     * unit that declares no arc pays one comparison and never fails it. A unit with no facing worth
     * speaking of - it has never moved, or does not steer itself - also notices all round, so an arc
     * cannot accidentally blind something before it has taken a step.
     */
    public static boolean withinArc(
            float lookX, float lookY, float fromX, float fromY, float toX, float toY, float arcCosine) {
        if (arcCosine <= -1f || (lookX == 0f && lookY == 0f)) {
            return true;
        }
        float towardsX = toX - fromX;
        float towardsY = toY - fromY;
        float distanceSquared = towardsX * towardsX + towardsY * towardsY;
        if (distanceSquared <= 0f) {
            return true;
        }
        float scale = 1f / (float) Math.sqrt(distanceSquared);
        return (towardsX * lookX + towardsY * lookY) * scale >= arcCosine;
    }

    /**
     * The arc test with its <strong>all-round range</strong> folded in: whether a point is near enough to be
     * noticed whichever way the unit is looking, or else inside its arc. The one place a strain's
     * {@code allRoundRange} is read, so an alien and a trooper cannot come to mean two things by it.
     * Zero adds nothing.
     */
    public static boolean withinNotice(
            float lookX,
            float lookY,
            float fromX,
            float fromY,
            float toX,
            float toY,
            float arcCosine,
            float allRoundRange) {
        return withinRange(fromX, fromY, toX, toY, allRoundRange)
                || withinArc(lookX, lookY, fromX, fromY, toX, toY, arcCosine);
    }

    /**
     * Whether nothing solid stands between the two points.
     *
     * <p>Only solid ground blocks a line - terrain and a shut door (ADR 0022). Obstacles, corpses and other
     * units do not - a limit accepted on purpose and listed with the others, and the same asymmetry routing
     * already lives with.
     */
    public static boolean clearLine(Solid solid, float fromX, float fromY, float toX, float toY, boolean required) {
        return !required || solid.clearLine(fromX, fromY, toX, toY);
    }

    /**
     * Whether a shot this wide gets from one point to the other without meeting a boulder.
     *
     * <p><strong>A clear shot is a different question from a clear line.</strong> Sight is terrain alone
     * (the note on {@link #clearLine}), but a boulder eats a shot as surely as rock does, so a unit that
     * <em>fires</em> asks this as well - after the others, because it is a pass over every obstacle.
     * {@code clearance} is how far either side of the line the shot reaches: its own radius, plus how
     * far off the line a twin mount's muzzles sit.
     */
    public static boolean clearShot(
            ImmutableArray<Entity> obstacles, float fromX, float fromY, float toX, float toY, float clearance) {
        for (int i = 0; i < obstacles.size(); i++) {
            Entity obstacle = obstacles.get(i);
            Position at = POSITION.get(obstacle);
            if (nearLine(fromX, fromY, toX, toY, at.x, at.y, BODY.get(obstacle).radius + clearance)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a point lies within this distance of any part of the line between two others. */
    public static boolean nearLine(float fromX, float fromY, float toX, float toY, float x, float y, float reach) {
        float spanX = toX - fromX;
        float spanY = toY - fromY;
        float lengthSquared = spanX * spanX + spanY * spanY;
        float along = lengthSquared <= 0f ? 0f : ((x - fromX) * spanX + (y - fromY) * spanY) / lengthSquared;
        along = Math.max(0f, Math.min(1f, along));
        return withinRange(fromX + spanX * along, fromY + spanY * along, x, y, reach);
    }
}
