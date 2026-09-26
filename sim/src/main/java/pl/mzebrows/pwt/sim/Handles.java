package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Handle;

/**
 * Finding an entity again from its {@link Handle}.
 *
 * <p>The one way a component refers to another entity across ticks. A handle is issued in spawn
 * order and never reused, and every family is kept in spawn order (ADR 0013), so the family of
 * handled entities is sorted by handle and a lookup is a binary search - no index to keep in step
 * and nothing more for a snapshot to carry.
 */
public final class Handles {

    /** Refers to nobody. What a handle field holds before it is aimed at anything. */
    public static final long NOBODY = Handle.NOBODY;

    private static final ComponentMapper<Handle> HANDLE = ComponentMapper.getFor(Handle.class);

    private Handles() {}

    /** This entity's handle. */
    public static long of(Entity entity) {
        return HANDLE.get(entity).id;
    }

    /**
     * The entity with this handle, or null if it is not on the field - never spawned, reaped, or
     * {@link #NOBODY}. {@code handled} is the family of every entity with a {@link Handle}, which a
     * caller caches once like any other view.
     */
    public static @Nullable Entity resolve(ImmutableArray<Entity> handled, long handle) {
        if (handle == NOBODY) {
            return null;
        }
        int low = 0;
        int high = handled.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Entity candidate = handled.get(middle);
            long id = HANDLE.get(candidate).id;
            if (id < handle) {
                low = middle + 1;
            } else if (id > handle) {
                high = middle - 1;
            } else {
                return candidate;
            }
        }
        return null;
    }
}
