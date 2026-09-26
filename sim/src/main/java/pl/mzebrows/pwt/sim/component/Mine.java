package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Something small lying in the open that goes off when the other side comes too close.
 *
 * <p>A barrel waits to be shot at; a mine waits to be walked into, which is the whole difference and
 * the reason it is a component rather than another kind of pickup. <strong>It explodes by dying</strong>
 * like everything else that explodes (G0018): what is here is the clock, and the circle is a
 * {@code BlastsOnDeath} exactly as a barrel's is.
 *
 * <p><strong>Only the other side arms it.</strong> A commander walking over one, and a trooper
 * standing on one, do nothing at all - so a minefield is ground the player can lay, cross and haul
 * about rather than ground he has to remember. What it costs him is that the blast, like every
 * blast, is on nobody's side (G0018): laying one badly and standing beside it is still his mistake.
 *
 * <p>Arming <strong>latches</strong>, for the reason a nest waking latches: a fuse a creature could
 * walk away from is a fuse the player watches rather than a threat the other side has to deal with,
 * and a mine that could be reset by backing off would have no moment in it.
 */
public final class Mine implements Component, Pool.Poolable {

    /** How close something on the other side has to come to set the clock going, in world units. */
    public float triggerRadius;

    /** How long it ticks before it goes off. The moment - long enough to see and too short to answer. */
    public int fuseTicks;

    public int remainingTicks;

    /** How long one flash lasts when it has only just started ticking, in ticks - fractional, being a rhythm. */
    public float flashFromTicks;

    /** How long one lasts at the end. Shorter, so the flashing quickens as the moment comes. */
    public float flashToTicks;

    /** True once something has come near enough. It never goes back. */
    public boolean armed;

    /** Sets what it takes to set this one off, and returns this component, for fluent spawning. */
    public Mine set(float newTriggerRadius, int newFuseTicks, float newFlashFromTicks, float newFlashToTicks) {
        this.triggerRadius = newTriggerRadius;
        this.fuseTicks = newFuseTicks;
        this.remainingTicks = newFuseTicks;
        this.flashFromTicks = newFlashFromTicks;
        this.flashToTicks = newFlashToTicks;
        this.armed = false;
        return this;
    }

    /** Starts the clock. Does nothing to one that is already ticking. */
    public void arm() {
        armed = true;
    }

    /** True once the clock has run out. */
    public boolean spent() {
        return armed && remainingTicks <= 0;
    }

    /**
     * How bright the fuse is right now, in {@code [0, 1]}, for the renderer to draw (pillar 5).
     *
     * <p>A triangle wave off its own clock, whose period shortens as the fuse runs down: what the
     * player has to be able to read is not how long is left as a number but that it is <em>going</em>,
     * and faster. Its own clock rather than the shared pulse everything that shines uses, because
     * two mines armed a second apart have to be a second apart on screen - a minefield flashing in
     * unison reads as one thing rather than as several.
     *
     * <p>A triangle rather than a sine because {@code sin} is banned in this module: it is specified
     * only to within an ulp and may differ between JVMs, which pillar 6 does not allow.
     */
    public float flash() {
        if (!armed || fuseTicks <= 0 || flashFromTicks <= 0f) {
            return 0f;
        }
        float left = remainingTicks / (float) fuseTicks;
        float period = flashToTicks + (flashFromTicks - flashToTicks) * left;
        if (period <= 0f) {
            return 0f;
        }
        float turns = remainingTicks / period;
        float phase = turns - (float) Math.floor(turns);
        return 1f - Math.abs(phase * 2f - 1f);
    }

    @Override
    public void reset() {
        triggerRadius = 0f;
        fuseTicks = 0;
        remainingTicks = 0;
        flashFromTicks = 0f;
        flashToTicks = 0f;
        armed = false;
    }
}
