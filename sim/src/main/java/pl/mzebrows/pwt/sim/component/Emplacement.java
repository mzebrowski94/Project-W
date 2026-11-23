package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Cycle;
import pl.mzebrows.pwt.sim.def.EmplacementKind;

/**
 * An **emplacement** (G0113): a standing shooter nobody crews, and where it is in its cycle. The cycle was dealt when it
 * was put down and is held here, so a snapshot carries it (ADR 0014); nothing ever deals it again, so it can be learnt
 * (pillar 5). While it is awake it fires the points of its next step every period and moves on; asleep, its clock and
 * its cycle both stand still.
 */
public final class Emplacement implements Component, Pool.Poolable {

    /** Which kind of emplacement it is. */
    public EmplacementKind kind = EmplacementKind.SPIRE;

    /** Its cycle, packed as {@link Cycle#steps}: each step the compass points it fires along. */
    public long steps;

    /** How many steps its cycle has. */
    public int length;

    /** The step it fires next. */
    public int next;

    /** Ticks between one step and the next. */
    public int periodTicks;

    /** Ticks left before it fires its next step, counted only while it is awake. */
    public int remainingTicks;

    /** How near a man on his feet wakes it; zero, and only something else does (ticket 09). */
    public float wakeRadius;

    /** Whether it is firing: set each tick from the men near it, or - with no wake radius - by what wakes it. */
    public boolean awake;

    /** Sets its kind, its cycle and its clock, asleep and a whole period from its first step, for fluent spawning. */
    public Emplacement set(EmplacementKind newKind, Cycle cycle, int newPeriodTicks, float newWakeRadius) {
        this.kind = newKind;
        this.steps = cycle.steps();
        this.length = cycle.length();
        this.next = 0;
        this.periodTicks = newPeriodTicks;
        this.remainingTicks = newPeriodTicks;
        this.wakeRadius = newWakeRadius;
        this.awake = false;
        return this;
    }

    /** The compass points its next step fires along, one bit a point. */
    public int nextPoints() {
        return length <= 0 ? 0 : Cycle.points(steps, next);
    }

    @Override
    public void reset() {
        kind = EmplacementKind.SPIRE;
        steps = 0L;
        length = 0;
        next = 0;
        periodTicks = 0;
        remainingTicks = 0;
        wakeRadius = 0f;
        awake = false;
    }
}
