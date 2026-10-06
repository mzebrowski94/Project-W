package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How an entity takes part in collision, as data rather than as a class of entity.
 *
 * <p>What keeps the solver purely geometric. {@code solid}: two of these push each other apart; with
 * either non-solid, a contact is only reported. {@code movable}: the solver may write its position.
 */
public final class Collider implements Component, Pool.Poolable {

    public boolean solid;
    public boolean movable;

    /**
     * Whether it takes part in collision at all.
     *
     * <p>False for a man aboard a turret: a flag, since removing the component mid-tick would mutate
     * families.
     */
    public boolean present;

    /**
     * Whether a unit steers around it.
     *
     * <p>Defaults to {@code solid}; burning ground (avoided, not solid) and mines (solid, not avoided)
     * differ. Decided at the spawn (G0034, G0035).
     */
    public boolean avoided;

    /**
     * Whether a shot is used up on it.
     *
     * <p>Defaults to {@code solid}; ruined ground lies on the floor and stops nothing.
     */
    public boolean stopsShots;

    /**
     * Touches whatever lies along the way it came this tick, not only where it ended up. Shots are;
     * nothing solid is.
     */
    public boolean swept;

    /**
     * Passes through every body but scenery - what is solid and immovable - and touches nothing: a slung rider in
     * the air (G0074). Rock and the arena still hold it. Written as it is slung and cleared as it comes down.
     */
    public boolean throughBodies;

    /** Sets both flags and returns this component, for fluent spawning. */
    public Collider set(boolean newSolid, boolean newMovable) {
        this.solid = newSolid;
        this.movable = newMovable;
        this.present = true;
        this.avoided = newSolid;
        this.stopsShots = true;
        this.swept = false;
        this.throughBodies = false;
        return this;
    }

    /** Says whether a unit steers around this, where being solid is not the answer. */
    public Collider avoided(boolean steeredAround) {
        this.avoided = steeredAround;
        return this;
    }

    /** Says whether a shot is used up on this, where being solid is not the answer. */
    public Collider swept(boolean alongItsPath) {
        this.swept = alongItsPath;
        return this;
    }

    public Collider stopsShots(boolean stopsAShot) {
        this.stopsShots = stopsAShot;
        return this;
    }

    @Override
    public void reset() {
        solid = false;
        movable = false;
        present = false;
        avoided = false;
        stopsShots = false;
        swept = false;
        throughBodies = false;
    }
}
