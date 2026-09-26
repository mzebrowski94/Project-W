package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Which entity this is, for anything that has to find it again on a later tick.
 *
 * <p>The engine pools its {@code Entity} objects, so one kept across a tick comes back aimed at
 * whatever was spawned next - which is why no component may hold one ({@code HandleTest}). A handle
 * is issued once, from {@code Level.nextHandle}, when the entity is added, and never reused: holding
 * one is always safe, and {@code Handles.resolve} finds the entity or finds nobody. Every entity
 * carries one, and handles rise in spawn order, which is what lets that lookup be a binary search.
 */
public final class Handle implements Component, Pool.Poolable {

    /** Refers to nobody: what a field that names another entity holds before it names one. */
    public static final long NOBODY = 0L;

    public long id;

    @Override
    public void reset() {
        id = 0L;
    }
}
