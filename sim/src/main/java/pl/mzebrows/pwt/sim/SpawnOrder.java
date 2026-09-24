package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import java.lang.reflect.Field;

/**
 * Keeps the engine's entity list and every family in the order their entities were spawned (ADR 0013).
 *
 * <p>Ashley 1.7.4 holds both in unordered gdx {@code Array}s, so removing an entity moves the last one
 * into its place, and after the first death the order a system iterates in is a function of history.
 * That is reproducible on one machine and impossible to carry in a snapshot. Setting {@code ordered}
 * on those arrays makes a removal close the gap instead, so with composition fixed at spawn every
 * family is the engine's list, filtered.
 *
 * <p><strong>The one place in the codebase that reaches inside Ashley.</strong> It fails loudly when the
 * fields it needs are not where 1.7.4 keeps them, so an upgrade that moved them stops the build rather
 * than quietly un-ordering the world.
 */
final class SpawnOrder {

    private final ObjectMap<Family, Array<Entity>> families;

    /** How many families were ordered last time, so {@link #keep()} costs one comparison when none is new. */
    private int ordered;

    SpawnOrder(Engine engine) {
        Object entityManager = read(Engine.class, "entityManager", engine);
        Array<Entity> entities = read(entityManager.getClass(), "entities", entityManager);
        entities.ordered = true;
        Object familyManager = read(Engine.class, "familyManager", engine);
        this.families = read(familyManager.getClass(), "families", familyManager);
        keep();
    }

    /**
     * Orders every family registered since the last call.
     *
     * <p>Families are registered whenever somebody first asks for one - the systems as they are added,
     * the client whenever it likes - so this runs before each tick rather than once. A family is filled
     * in engine order when it is registered, so ordering it afterwards loses nothing.
     */
    void keep() {
        if (families.size == ordered) {
            return;
        }
        for (Array<Entity> members : families.values()) {
            members.ordered = true;
        }
        ordered = families.size;
    }

    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    private static <T> T read(Class<?> owner, String name, Object from) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(from);
            if (value == null) {
                throw new IllegalStateException("Ashley's " + owner.getSimpleName() + "." + name + " is null");
            }
            return (T) value;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException(
                    "Ashley's " + owner.getSimpleName() + "." + name + " is not where ADR 0013 expects it", e);
        }
    }
}
