package pl.mzebrows.pwt.client.draw;

/**
 * How to draw a devour (G0099): how a clinch ends on screen. The one killed shrinks over his own death throes to
 * {@code endScale} of his size and is then removed as every body is; while both are there and the one who lived is
 * still in his lock, the pair sway together from side to side, across the line joining them. A look only: both
 * bodies keep their size and place for the solver and for shots.
 *
 * <p>The sway's phase is counted from the lock's own ticks, never the client's clock, so the pair agree on every
 * machine and a replay looks the same. It comes to rest as the lock ends and fades as the body's throes run out, so a
 * body that goes before the lock does - a trooper's - leaves the one who lived standing still, never snapping back.
 *
 * @param endScale what the body has shrunk to when its throes end, as a multiple of its radius - never nothing,
 *     which is a pickup's pop (G0078)
 * @param swayShare how far to either side the pair are swayed at most, as a multiple of the loser's radius
 * @param swayHz how many swings a second, at most {@link GraphicConfig#MAX_PULSE_HZ}
 */
public record DevourMark(float endScale, float swayShare, float swayHz) {

    private static final float TURN = (float) (Math.PI * 2.0);

    public DevourMark {
        if (!(endScale > 0f && endScale <= 1f)) {
            throw new IllegalArgumentException("devour endScale must be in (0, 1], was " + endScale);
        }
        if (swayShare < 0f) {
            throw new IllegalArgumentException("devour swayShare must not be negative, was " + swayShare);
        }
        if (!(swayHz >= 0f && swayHz <= GraphicConfig.MAX_PULSE_HZ)) {
            throw new IllegalArgumentException(
                    "a devour may sway at most " + GraphicConfig.MAX_PULSE_HZ + " times a second, not " + swayHz);
        }
    }

    /** The size the devoured body is drawn at, as a multiple of its radius, with {@code throesLeft} in {@code [0, 1]}. */
    public float scaleFor(float throesLeft) {
        float left = Math.max(0f, Math.min(1f, throesLeft));
        return endScale + (1f - endScale) * left;
    }

    /**
     * How far the pair are drawn to one side of the line joining them, in world units, signed: the two sides in turn.
     *
     * @param loserRadius the devoured body's radius
     * @param throesLeft how much of the devoured body's throes is left, in {@code [0, 1]}
     * @param lockTicks how many ticks of the winner's lock are left
     * @param alpha how far the clock is towards the next tick, in {@code [0, 1)}
     * @param tickSeconds how long a tick is
     * @param motionScale how far anything drawn moves; zero holds the pair still
     */
    public float swayFor(
            float loserRadius, float throesLeft, int lockTicks, float alpha, float tickSeconds, float motionScale) {
        float seconds = Math.max(0f, lockTicks - alpha) * tickSeconds;
        float left = Math.max(0f, Math.min(1f, throesLeft));
        return swayShare * loserRadius * motionScale * left * (float) Math.sin(TURN * swayHz * seconds);
    }
}
