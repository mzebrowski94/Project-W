package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Which way an entity is looking, as a unit vector; {@code (0, 0)} means it has no facing yet.
 *
 * <p>Gameplay state, not presentation: {@code PerceptionSystem} notices only what is inside the arc
 * centred on it (G0015), and the renderer draws the same vector, so the arc the game uses is the arc
 * the player was shown (pillar 5). Written by {@code FacingSystem} alone, at the end of the tick.
 *
 * <p>A vector rather than an angle: an angle needs {@code atan2} to write and trigonometry to read,
 * both banned in {@code sim} (pillar 6).
 */
public final class Facing implements Component, Pool.Poolable {

    /** Below this squared length a direction is noise rather than a heading. */
    private static final float EPSILON = 1e-4f;

    public float x;

    public float y;

    /**
     * Points along the given direction, which need not be normalised, and returns this component.
     * A direction too short to mean anything leaves the facing alone, so a unit that stops keeps
     * looking where it was looking.
     */
    public Facing face(float dirX, float dirY) {
        float lengthSquared = dirX * dirX + dirY * dirY;
        if (lengthSquared < EPSILON) {
            return this;
        }
        float scale = 1f / (float) Math.sqrt(lengthSquared);
        this.x = dirX * scale;
        this.y = dirY * scale;
        return this;
    }

    @Override
    public void reset() {
        x = 0f;
        y = 0f;
    }
}
