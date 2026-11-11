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

    /**
     * The chance a man it could catch gives it room, for ground (G0076). One - always - for everything but an
     * alien's toxic blood; decided once per man per {@link #spatter}, so he goes round all of it or none of it.
     */
    public float heeded = 1f;

    /** Which spatter this circle belongs to - the handle of what died to leave it - for {@link #heeded}. */
    public long spatter = Handle.NOBODY;

    /** Sets both flags and returns this component, for fluent spawning. */
    public Collider set(boolean newSolid, boolean newMovable) {
        this.solid = newSolid;
        this.movable = newMovable;
        this.present = true;
        this.avoided = newSolid;
        this.stopsShots = true;
        this.swept = false;
        this.throughBodies = false;
        this.heeded = 1f;
        this.spatter = Handle.NOBODY;
        return this;
    }

    /** Says a man gives this room only on a chance, decided once per man for the whole of its spatter (G0076). */
    public Collider heeded(float chance, long ofSpatter) {
        this.heeded = chance;
        this.spatter = ofSpatter;
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
        heeded = 1f;
        spatter = Handle.NOBODY;
    }
}
