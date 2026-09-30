package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * What a strain does with a man its leap lands on, if it does anything but bite him: it takes hold of
 * him and carries him off (G0045).
 *
 * <p><strong>A leap that grips never kills on landing.</strong> What kills is the clock: a man carried
 * for {@link #holdSeconds} is finished, and everything the player can do about it has to happen inside
 * that window - hit the thing from behind, hit its head with a fist, or, if it is him in its jaws,
 * hit it himself. The creature is killed by being hit, never by being struggled against: struggling is
 * a man landing blows on it like anybody else.
 *
 * @param enabled whether its leap takes hold at all; one configured off bites as every other leap
 *     does, which is what every strain but one declares
 * @param carrySpeed how fast it goes with a man in its jaws, in world units per second. Slower than a
 *     man on his feet, or nobody could catch up with one
 * @param holdSeconds how long it carries him before it finishes him
 * @param escapeKnockbackSpeed how hard a man is thrown clear when it dies with him still in its jaws, in
 *     world units per second (G0053)
 * @param escapeHarm what getting out of its jaws that way costs him: a wound for every gripper there is
 *     (G0053, G0057)
 */
@With
public record GripDef(
        boolean enabled, float carrySpeed, float holdSeconds, float escapeKnockbackSpeed, Harm escapeHarm) {

    public GripDef {
        Require.notNegative("grip carrySpeed", carrySpeed);
        Require.notNegative("grip holdSeconds", holdSeconds);
        Require.notNegative("grip escapeKnockbackSpeed", escapeKnockbackSpeed);
        if (enabled && holdSeconds <= 0f) {
            throw new IllegalArgumentException("a strain that grips has to hold on for some time");
        }
    }

    /** A leap that bites. What every strain but one declares. */
    public static GripDef off() {
        return new GripDef(false, 0f, 0f, 0f, Harm.NONE);
    }
}
