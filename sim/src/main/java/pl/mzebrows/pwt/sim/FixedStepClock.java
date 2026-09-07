package pl.mzebrows.pwt.sim;

/**
 * Turns variable frame deltas into a whole number of fixed simulation steps.
 *
 * <p>The simulation only ever advances by {@code tickSeconds}, which is what keeps it reproducible;
 * the leftover fraction is exposed as {@link #alpha()} so rendering can interpolate.
 */
public final class FixedStepClock {

    private final float tickSeconds;
    private final int maxTicksPerFrame;

    private float accumulator;

    public FixedStepClock(SimConfig config) {
        this.tickSeconds = config.tickSeconds();
        this.maxTicksPerFrame = config.maxTicksPerFrame();
    }

    /**
     * Consumes a frame delta and returns how many fixed steps should run for it. Deltas beyond
     * {@link SimConfig#maxTicksPerFrame()} steps are dropped rather than queued, so a long stall
     * (a breakpoint, a window drag) does not produce a burst of catch-up frames.
     */
    public int stepsFor(float frameDeltaSeconds) {
        if (frameDeltaSeconds > 0f) {
            accumulator += frameDeltaSeconds;
        }
        int steps = (int) (accumulator / tickSeconds);
        if (steps > maxTicksPerFrame) {
            accumulator = 0f;
            return maxTicksPerFrame;
        }
        accumulator -= steps * tickSeconds;
        return steps;
    }

    /** Progress towards the next step, in {@code [0, 1)}; used to interpolate rendering. */
    public float alpha() {
        return accumulator / tickSeconds;
    }
}
