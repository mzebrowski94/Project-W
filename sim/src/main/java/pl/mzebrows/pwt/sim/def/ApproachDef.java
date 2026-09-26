package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How wide of its target a creature closes, so a pack arrives as a line abreast rather than as a
 * queue.
 *
 * <p><strong>It moves the goal, not the course.</strong> Everything hunting the same man was steered
 * at the same point, and a flow field's gradient in the open is grid-shaped - so a pack crossing open
 * ground converged onto his row and came down it in single file, each one shoving the one in front.
 * Avoidance could not fix that: it is an opinion about what is in the way, and what was in the way
 * was another creature heading for the identical spot. Give each of them a <em>different</em> spot
 * and the problem stops existing, which is why this is two numbers and no new system.
 *
 * <p>{@link #offset} is a distance to either side of the line to the target, at full swing. Which
 * side, and how much of it, is {@code Approach.bearing} - seeded from where the creature was spawned,
 * exactly as a {@link WeaveDef weave}'s phase is (G0012), so a pack fans out without a number being
 * drawn from the {@code Rng} and the same level plays the same way on every run.
 *
 * <p>The swing falls to nothing as the creature closes, which is the half that keeps it a
 * <em>spread</em> and not a circle: past twice {@link #closeRange} it is at its widest, and by
 * {@link #closeRange} it is walking straight at him again. Something that held its offset all the
 * way in would orbit rather than arrive.
 *
 * <p>Terrain still has the last word. The offset point is only steered at while the line to it is
 * clear; in a corridor it is not, the flow field answers instead, and a pack files through a crack
 * the way it has to.
 *
 * @param enabled whether this strain spreads out at all. Off walks at the man himself
 * @param offset how far to either side of the line it closes, in world units, at full swing. Sized
 *     against the strain's own body: below a body width it reads as a wobble rather than as a
 *     creature taking its own line in
 * @param closeRange the distance at which it is walking straight at him again, in world units. Wide
 *     enough that the fan is open while the ground is still being crossed, and comfortably inside
 *     the strain's detection range, or it spends the whole approach at full swing and never closes
 */
@With
public record ApproachDef(boolean enabled, float offset, float closeRange) {

    public ApproachDef {
        Require.notNegative("approach offset", offset);
        Require.notNegative("approach closeRange", closeRange);
        if (enabled && closeRange <= 0f) {
            throw new IllegalArgumentException("a strain that spreads out needs a range to close in by");
        }
    }

    /** A strain that walks straight at whatever it is hunting. */
    public static ApproachDef off() {
        return new ApproachDef(false, 0f, 0f);
    }

    /** True if this strain takes a line of its own at all. */
    public boolean active() {
        return enabled && offset > 0f && closeRange > 0f;
    }
}
