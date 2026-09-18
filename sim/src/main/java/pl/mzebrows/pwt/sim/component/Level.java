package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Objective;

/**
 * State that belongs to the level rather than to anything standing in it.
 *
 * <p>Carried by one entity spawned with the world and never removed. That entity is the answer to a
 * gap G0004 names explicitly: a reaction that is not about an entity - a score, a squad count, a
 * level objective - looks like the one thing a message queue would be good for. Making it an
 * ordinary entity with an ordinary component keeps every rule intact instead: systems find it through
 * a family, the state is somewhere you can look at it, and a replay carries it like anything else.
 *
 * <p>It has no body, no position and nothing to draw. It is a place to put a number.
 */
public final class Level implements Component, Pool.Poolable {

    /**
     * Troopers a pickup has granted the level and that are not yet standing on it.
     *
     * <p>The handover between {@code PickupSystem}, which knows a crate was worth some men, and
     * {@code ReinforcementSystem}, which knows where a man goes and how many the squad may hold. It
     * is also the queue those men arrive in: one is put down per tick, so a crate worth three drains
     * this over three ticks and then it is zero again, which is what it reads for almost all of a
     * level.
     *
     * <p>It survives longer only while there is nobody to form up on - the commander down, and a
     * trooper having reached the crate. That is a debt owed for a couple of seconds, not a balance:
     * it pays out on the tick somebody is standing again.
     */
    public int pendingReinforcements;

    /**
     * Which objectives this level names, and which of them are currently met, as bit sets.
     *
     * <p>Two small ints rather than two collections because this is read every tick and a level
     * component is pooled; ordinal indexing is safe here for the reason {@code Defs} gives - nothing
     * is persisted against the numbers, and the bits are set and read in the same process from the
     * enum's own constants.
     *
     * <p><strong>Met is recomputed every tick, not latched.</strong> Clearing the map is only true
     * while it is true: killing the last enemy and then meeting a new wave should un-meet it, and a
     * latch would quietly turn "clear the map" into "have cleared the map once".
     */
    private int required;

    private int met;

    /** Adds an objective this level requires. */
    public void require(Objective objective) {
        required |= bit(objective);
    }

    /** Whether this level asks for that objective at all. */
    public boolean requires(Objective objective) {
        return (required & bit(objective)) != 0;
    }

    /** Records whether an objective is met right now. */
    public void setMet(Objective objective, boolean isMet) {
        if (isMet) {
            met |= bit(objective);
        } else {
            met &= ~bit(objective);
        }
    }

    /** Whether that objective is met right now. */
    public boolean isMet(Objective objective) {
        return (met & bit(objective)) != 0;
    }

    /**
     * Whether every objective this level named is met.
     *
     * <p>A level that names none is <strong>never</strong> complete. That is deliberate: an empty
     * requirement returning true would make every arena that never thought about objectives - which
     * is all of them - instantly finished, and a scenario would have to opt out of a thing it had
     * never opted into.
     */
    public boolean complete() {
        return required != 0 && (met & required) == required;
    }

    @SuppressWarnings("EnumOrdinal")
    private static int bit(Objective objective) {
        return 1 << objective.ordinal();
    }

    @Override
    public void reset() {
        pendingReinforcements = 0;
        required = 0;
        met = 0;
    }
}
