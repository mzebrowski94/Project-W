package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Which side an entity is on. Damage only ever crosses sides, never stays within one.
 *
 * <p>This exists so combat asks a question about <em>data</em> rather than about what an entity is.
 * It replaced a hard-coded "is this the commander?" check that stopped a commander shooting itself;
 * with a side on the projectile, the same rule now covers troopers, friendly fire and anything else
 * with a team, without another branch.
 */
public final class Faction implements Component, Pool.Poolable {

    /** The sides that exist. Scenery is {@link #NEUTRAL}, which is on nobody's side and hurts nobody. */
    public enum Side {
        /** Not set, or scenery. Never friendly with anything, including other neutrals' attacks. */
        NEUTRAL,
        /** The commander and, when they exist, troopers. */
        PLAYER,
        /** Everything trying to kill them. */
        ALIEN
    }

    public Side side = Side.NEUTRAL;

    /** Sets the side and returns this component, for fluent spawning. */
    public Faction set(Side newSide) {
        this.side = newSide;
        return this;
    }

    @Override
    public void reset() {
        side = Side.NEUTRAL;
    }
}
