package pl.mzebrows.pwt.ui.hud;

import pl.mzebrows.pwt.sim.def.Objective;

/**
 * The top bar's second line - the one that is always current: the first objective the level still
 * wants, or that it has everything it wants. A level that names no objective but has control points leads through
 * them instead, and says nothing once they are all taken; so does one whose post is not stood yet, its waves still
 * held back - a mission's march, through its points to its finale's, and then the hold (G0064).
 *
 * <p>Objectives are recomputed every tick rather than latched ({@code Level}), so before the level is won
 * the line may go back from completed to a goal when a new wave walks onto a cleared field. That is the
 * truth and it is shown - but a change of goal waits {@code minHoldSeconds}, so a one-tick flip never
 * flickers. The counts on a goal change at once: they only ever say more has happened. Once won or lost
 * the line says so for good (G0066).
 *
 * <p>Becoming complete, or failed, pulses the line twice, then it holds still: motion says something
 * changed and nothing moves when nothing did (plan 6.4). Frame time, not simulation time.
 */
public final class GoalLine {

    /** The order goals are named in: ground first, then holding it, then what is left standing. */
    private static final Objective[] ORDER = {Objective.CAPTURE_POINTS, Objective.HOLD_POINT, Objective.CLEAR_ENEMIES};

    private static final int PULSES = 2;

    private final float minHoldSeconds;
    private final float pulseHz;
    private final boolean flashes;

    private long worldSerial = Long.MIN_VALUE;
    private Goal shown = Goal.NONE;
    private int first;
    private int second;
    private float heldSeconds;
    private float outcomeSeconds = Float.MAX_VALUE;
    private int revision;

    /**
     * @param minHoldSeconds how long a goal stays before another may replace it
     * @param pulseHz how fast completing pulses
     * @param flashScale 0 turns the pulse into a steady change; anything else leaves it on
     */
    public GoalLine(float minHoldSeconds, float pulseHz, float flashScale) {
        this.minHoldSeconds = minHoldSeconds;
        this.pulseHz = pulseHz;
        this.flashes = flashScale > 0f && pulseHz > 0f;
    }

    /** The goal the level asks for right now, with no memory and no hold. */
    public static Goal resolve(Readout readout) {
        if (readout.failed) {
            return Goal.FAILED;
        }
        if (readout.won) {
            return Goal.COMPLETE;
        }
        if (readout.regroupPresent > 0 && readout.regroupPresent < readout.regroupNeeded) {
            return Goal.REGROUP;
        }
        if (readout.objectivesRequired == 0) {
            // Points that are not an objective are still the way on: the line leads through them while any is left.
            return readout.pointsTaken < readout.pointsTotal ? Goal.CAPTURE_POINTS : Goal.NONE;
        }
        if (readout.complete()) {
            return Goal.COMPLETE;
        }
        if (readout.requires(Objective.HOLD_POINT)
                && !readout.wavesArmed
                && readout.pointsTaken < readout.pointsTotal) {
            // No post until its ground is taken, and its waves wait for it: a mission's march leads through its
            // points - the finale's among them - to the ground it is then told to hold (G0064).
            return Goal.CAPTURE_POINTS;
        }
        for (Objective objective : ORDER) {
            if (readout.requires(objective) && !readout.met(objective)) {
                return goalOf(objective);
            }
        }
        return Goal.COMPLETE;
    }

    /** Follows the readout. Call once a frame. */
    public void update(Readout readout, float frameSeconds) {
        heldSeconds += frameSeconds;
        outcomeSeconds += frameSeconds;
        Goal wanted = resolve(readout);
        boolean newWorld = readout.worldSerial != worldSerial;
        worldSerial = readout.worldSerial;
        if (newWorld || (wanted != shown && heldSeconds >= minHoldSeconds)) {
            if (outcome(wanted) && wanted != shown && !newWorld) {
                outcomeSeconds = 0f;
            }
            shown = wanted;
            heldSeconds = 0f;
            revision++;
        }
        int wantedFirst =
                switch (shown) {
                    case CAPTURE_POINTS -> readout.pointsTaken;
                    case HOLD_POINT -> readout.wavesSent;
                    case REGROUP -> readout.regroupPresent;
                    default -> 0;
                };
        int wantedSecond =
                switch (shown) {
                    case CAPTURE_POINTS -> readout.pointsTotal;
                    case HOLD_POINT -> readout.wavesTotal;
                    case REGROUP -> readout.regroupNeeded;
                    default -> 0;
                };
        if (wantedFirst != first || wantedSecond != second) {
            first = wantedFirst;
            second = wantedSecond;
            revision++;
        }
    }

    public Goal shown() {
        return shown;
    }

    /** Points taken, waves sent, or players in place - whichever the goal counts. */
    public int first() {
        return first;
    }

    /** Points there are, or waves there will be. */
    public int second() {
        return second;
    }

    /** Moves on whenever the words would change, so a painter rebuilds its text only then. */
    public int revision() {
        return revision;
    }

    /** Whether the line is drawn bright this frame: false only in the off half of a completion pulse. */
    public boolean bright() {
        if (!flashes || !outcome(shown)) {
            return true;
        }
        float cycles = outcomeSeconds * pulseHz;
        if (cycles >= PULSES) {
            return true;
        }
        return cycles - (int) cycles >= 0.5f;
    }

    /** A goal that says how the level ended - or, before the win, that it could. */
    private static boolean outcome(Goal goal) {
        return goal == Goal.COMPLETE || goal == Goal.FAILED;
    }

    private static Goal goalOf(Objective objective) {
        return switch (objective) {
            case CAPTURE_POINTS -> Goal.CAPTURE_POINTS;
            case HOLD_POINT -> Goal.HOLD_POINT;
            case CLEAR_ENEMIES -> Goal.CLEAR_ENEMIES;
        };
    }
}
