package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How a creature refuses to walk in a straight line.
 *
 * <p><strong>A function of the ground it has covered, and of nothing else.</strong> Not a clock, so
 * it does not change shape when something slows the creature down, and not a roll - the same
 * argument G0012 makes for the dodge applies here twice over, because this one is happening all the
 * time. A player who watches a runner for a few seconds can learn to lead it, and that is the whole
 * point of a weave: it is a harder shot rather than a luckier one.
 *
 * <p>The phase comes from where the unit was spawned, so a pack arrives as a scatter rather than as a
 * chorus line, without a number being drawn for it.
 *
 * @param enabled whether this strain weaves at all. Off for everything that simply comes at you
 * @param amplitude how hard it leans off its course, as a fraction of its forward direction. Around
 *     a half is a noticeable serpentine; above one it is travelling sideways more than forwards
 * @param wavelength how far it travels through one full left-right-left cycle, in world units.
 *     Short is a jitter that reads as a bug; long is a curve nobody notices
 */
@With
public record WeaveDef(boolean enabled, float amplitude, float wavelength) {

    public WeaveDef {
        Require.notNegative("weave amplitude", amplitude);
        if (enabled && wavelength <= 0f) {
            throw new IllegalArgumentException("weave wavelength must be positive, was " + wavelength);
        }
    }

    /** Something that comes straight at you. What every strain but one declares. */
    public static WeaveDef off() {
        return new WeaveDef(false, 0f, 0f);
    }
}
