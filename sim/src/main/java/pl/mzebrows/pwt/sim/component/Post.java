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

    /**
     * How far around it has to be clear before it counts as held, in world units.
     *
     * <p><strong>Zero means the whole level</strong>, which is what a level the size of one arena
     * means by it and is why nothing had to name this until a level was bigger than a fight. On a
     * march seven screens long, "nothing hostile standing" cannot mean the march - there are nests
     * still working four biomes back - so a post on a level like that names the ground it is
     * responsible for, and the objective asks about that ground rather than about the level.
     *
     * <p>It is deliberately not the capture radius above: what a post is worth is measured by what is
     * standing anywhere near it, and what you stand on to hold it is a few strides across.
     */
    public float watchRadius;

    /** Sets how big it is and how far it watches, returning this component, for fluent spawning. */
    public Post set(float newRadius, float newWatchRadius) {
        this.radius = newRadius;
        this.watchRadius = newWatchRadius;
        return this;
    }

    @Override
    public void reset() {
        radius = 0f;
        watchRadius = 0f;
    }
}
