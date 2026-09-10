package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A fired entity that despawns when it hits something or leaves the arena.
 *
 * <p>It carries <strong>where it was fired from</strong>, and deliberately not <em>who</em> fired it.
 * Something shot at wants to go and deal with the shooter, and a place answers that without a
 * reference: components are pooled, so an owner kept here could outlive the shooter and come back
 * pointing at whatever was built from the recycled instance (G0010). A muzzle flash is a place, and
 * walking at it is what the player would expect to see (G0016).
 *
 * <p>The origin is where the shot started, not where it landed. The impact point is a couple of
 * units from whoever was hit and is no use as somewhere to go.
 */
public final class Projectile implements Component, Pool.Poolable {

    /** Where this shot was fired from, in world units. */
    public float originX;

    public float originY;

    /** Records the muzzle, and returns this component, for fluent spawning. */
    public Projectile from(float x, float y) {
        this.originX = x;
        this.originY = y;
        return this;
    }

    @Override
    public void reset() {
        originX = 0f;
        originY = 0f;
    }
}
