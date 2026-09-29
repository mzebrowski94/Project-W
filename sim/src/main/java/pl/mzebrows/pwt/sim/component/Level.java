package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Objective;

/**
 * State that belongs to the level rather than to anything standing in it.
 *
 * <p>Carried by one entity spawned with the world and never removed, so level-scope state needs no
 * message queue (G0004) and a snapshot carries it like anything else.
 */
public final class Level implements Component, Pool.Poolable {

    /**
     * Troopers a pickup has granted the level and that are not yet standing on it.
     *
     * <p>From {@code PickupSystem} to {@code ReinforcementSystem}, which puts one down per tick while
     * there is somebody to form up on.
     */
    public int pendingReinforcements;

    /**
     * Which objectives this level names, and which of them are currently met, as bit sets.
     *
     * <p>By ordinal, never persisted. <strong>Met is recomputed every tick, not latched.</strong>
     */
    private int required;

    private int met;

    /**
     * How many ticks this level has run: the only clock the simulation has. Anything timed derives
     * from it - a clock kept in a system is state no snapshot sees (ADR 0014).
     */
    public long tick;

    /**
     * The {@link Handle} the next entity is given, never reused (ADR 0016). Zero is nobody.
     */
    public long nextHandle = 1L;

    /**
     * Whether the harness has made every commander invulnerable. Switched only by an input event
     * ({@code PlayerInputs.switchInvulnerability}); for testing by hand only (pillar 2).
     */
    public boolean commandersInvulnerable;

    /**
     * How many bosses have gone down this level, counted on the tick each one's throes begin. Written
     * by {@code BarrageSystem}, once per boss; read by the upgrade package as a source of drafts.
     */
    public int bossesDown;

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
     * <p>A level that names none is <strong>never</strong> complete.
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
        tick = 0L;
        nextHandle = 1L;
        commandersInvulnerable = false;
        bossesDown = 0;
    }
}
