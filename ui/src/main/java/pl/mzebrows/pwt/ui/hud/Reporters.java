package pl.mzebrows.pwt.ui.hud;

import java.util.List;

/**
 * Runs every reporter once a frame against the last frame's readout, which it keeps. A new world is
 * bound silently: the terminal is cleared, each reporter takes the world as it is, and the only line is
 * that the mission started. A player dropping in mid-level is not told what happened before he came.
 */
public final class Reporters {

    private final List<Reporter> reporters;
    private final Readout previous = new Readout();
    private boolean bound;

    /** Runs these reporters: every one there is - this list is the registry (plan 8.5). */
    public Reporters(List<Reporter> reporters) {
        this.reporters = List.copyOf(reporters);
    }

    /** Reports this frame. */
    public void report(Readout now, DispatchSink out) {
        if (!bound || now.worldSerial != previous.worldSerial) {
            bound = true;
            previous.copyFrom(now);
            out.reset();
            for (Reporter reporter : reporters) {
                reporter.bind(now);
            }
            out.offer(DispatchKind.MISSION_STARTED, -1, 0, 0);
            return;
        }
        for (Reporter reporter : reporters) {
            reporter.report(previous, now, out);
        }
        previous.copyFrom(now);
    }
}
