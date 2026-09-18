package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Ground the level says you must hold, and the other side is coming for.
 *
 * <p>The mirror of a control point. A point is somewhere the squad walks to and takes; a post is
 * somewhere it stands and keeps - and it is the first thing in the game that tells the <em>aliens</em>
 * where to be. A creature that has noticed nobody walks at it instead of ambling about its patch,
 * which is what turns a level from a map you clear into a place that is attacked.
 *
 * <p>It is a goal rather than a thing to fight: nothing shoots it, nothing takes it, and it has no
 * health. What it costs you to lose is that the fighting is happening where you were told to stand.
 *
 * <p>No collider, exactly as a control point carries none: it is somewhere you walk, not something
 * you walk into, so it never enters the pairwise path and its body can be as wide as it needs to be
 * without inflating the collision grid's cell size.
 */
public final class Post implements Component, Pool.Poolable {

    /** How wide the ground it marks is, in world units. What the renderer measures its circle from. */
    public float radius;

    /** Sets how big it is and returns this component, for fluent spawning. */
    public Post set(float newRadius) {
        this.radius = newRadius;
        return this;
    }

    @Override
    public void reset() {
        radius = 0f;
    }
}
