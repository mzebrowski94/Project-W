package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;

/**
 * A **worked site** (G0111): a site taken by one man's work held at it - a mainframe downloaded. The work is the
 * site's, on a door's rules (G0069): whoever is at it this tick - a commander with the key held, the one trooper it
 * went to - marks it worked, and a tick nobody did loses all of it; one tick of work a tick, however many hands.
 * Whether it is taken is its {@link Site}'s.
 */
public final class WorkedSite implements Component, Pool.Poolable {

    /** Which kind of worked site it is. */
    public WorkedSiteKind kind = WorkedSiteKind.MAINFRAME;

    /** What the action key offers at it (G0098): its kind's, kept here so reach can ask without the table. */
    public ActionKind action = ActionKind.NONE;

    /** How many ticks of work take it. */
    public int workTicks;

    /** How much work has been done on it, in ticks. Lost on any tick nobody works on it. */
    public int workedTicks;

    /** Whether somebody worked on it this tick: set by whoever is at it, spent when the tick's work is counted. */
    public boolean worked;

    /** The one trooper working it, by handle, or nobody. */
    public long trooper = Handle.NOBODY;

    /** Sets its kind and how long it takes, untouched, and returns this component, for fluent spawning. */
    public WorkedSite set(WorkedSiteKind newKind, ActionKind newAction, int newWorkTicks) {
        this.kind = newKind;
        this.action = newAction;
        this.workTicks = newWorkTicks;
        this.workedTicks = 0;
        this.worked = false;
        this.trooper = Handle.NOBODY;
        return this;
    }

    /** How far through the work it is, in {@code [0, 1]}: its own bar, the charge everyone sees on it. */
    public float progress() {
        return workTicks <= 0 ? 0f : Math.min(1f, workedTicks / (float) workTicks);
    }

    @Override
    public void reset() {
        kind = WorkedSiteKind.MAINFRAME;
        action = ActionKind.NONE;
        workTicks = 0;
        workedTicks = 0;
        worked = false;
        trooper = Handle.NOBODY;
    }
}
