package pl.mzebrows.pwt.sim.system.query;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Limpet;

/**
 * Which limpet a man has out (G0100): the one his next press sets off, and the one his panel says DETONATE for. One
 * question, asked by the system that decides the press and by the one that draws it.
 */
public final class Limpets {

    private static final ComponentMapper<Limpet> LIMPET = ComponentMapper.getFor(Limpet.class);
    private static final ComponentMapper<Handle> HANDLE = ComponentMapper.getFor(Handle.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);

    private Limpets() {}

    /**
     * The limpet {@code owner} threw that has not gone off, or null. {@code limpets} is the family of every entity with
     * a {@link Limpet}, which a caller caches once; a handful at most, one a man.
     */
    public static @Nullable Entity outOf(ImmutableArray<Entity> limpets, Entity owner) {
        if (!HANDLE.has(owner)) {
            return null;
        }
        long id = HANDLE.get(owner).id;
        for (int i = 0; i < limpets.size(); i++) {
            Entity limpet = limpets.get(i);
            if (limpet.isScheduledForRemoval() || DEAD.has(limpet)) {
                continue;
            }
            if (LIMPET.get(limpet).owner == id) {
                return limpet;
            }
        }
        return null;
    }
}
