package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

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
     * Troopers granted by pickups and not yet given to anybody, because the squad does not exist yet.
     *
     * <p>Alpha-phase scaffolding, listed as such in {@code docs/design/alpha-phase.md}. It is here so
     * that a level-scope grant has somewhere real to land and the path can be tested end to end;
     * nothing in the game reads it, and what replaces it is the squad.
     */
    public int pendingReinforcements;

    @Override
    public void reset() {
        pendingReinforcements = 0;
    }
}
