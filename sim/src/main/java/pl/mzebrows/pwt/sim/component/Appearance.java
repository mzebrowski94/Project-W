package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What an entity is, for the renderer to look at. The renderer reads this and never asks what
 * components an entity has or what class it is (G0003), so swapping placeholder shapes for sprites
 * touches one class in the client and nothing here.
 *
 * <p>{@link Look} names roles, not colours or shapes. The mapping to anything visible lives in the
 * client module - the simulation holds no visual data and does not know a renderer exists.
 */
public final class Appearance implements Component, Pool.Poolable {

    /** The role a renderer draws. */
    public enum Look {
        /** Nothing meaningful set - a pooled component that was reset and not yet reused. */
        NONE,
        COMMANDER,
        ENEMY,
        PROJECTILE,
        OBSTACLE
    }

    public Look look = Look.NONE;

    /** Sets the look and returns this component, for fluent spawning. */
    public Appearance set(Look newLook) {
        this.look = newLook;
        return this;
    }

    @Override
    public void reset() {
        look = Look.NONE;
    }
}
