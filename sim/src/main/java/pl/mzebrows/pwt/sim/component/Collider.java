package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How an entity takes part in collision, as data rather than as a class of entity.
 *
 * <p>This is what keeps the constraint solver purely geometric: it never asks whether something is a
 * projectile or a wall, only whether it is solid and whether it can be pushed. A trigger volume, a
 * pickup, a destructible crate and a moving hazard are all combinations of these two flags rather
 * than new branches in the solver.
 *
 * <p>{@code solid} means two of these push each other out of the way; a pair where either is
 * non-solid still reports a contact, which is how a projectile hits without shoving. {@code movable}
 * means the solver may write to its position - a false here is what makes an obstacle immovable and
 * what keeps a projectile from being clamped inside the arena instead of leaving it.
 */
public final class Collider implements Component, Pool.Poolable {

    public boolean solid;
    public boolean movable;

    /**
     * Whether it takes part in collision at all.
     *
     * <p>The third flag, and the one that says "not on the field right now" - a man aboard a turret
     * is inside the thing rather than standing beside it, so nothing may shove him, shoot him or
     * claw him while he is on it. Everything else that is out of the pairwise path is out of it by
     * carrying no collider at all; a man who is sometimes aboard cannot be, because adding and
     * removing a component mid-tick mutates the families being iterated.
     *
     * <p>It is still a fact about how this entity collides rather than a question about what it is,
     * which is the whole point of this component: the solver reads the flag and asks nothing.
     */
    public boolean present;

    /**
     * Whether a unit steers around it.
     *
     * <p><strong>Being in the way and being worth going round stopped being the same question</strong>
     * the moment two things wanted to disagree about it. Burning ground pushes nobody and everybody
     * should give it room; a mine is a solid little thing standing in the open and the whole mechanic
     * is that the other side walks into it. Being solid is still the answer for everything else, so
     * {@link #set} defaults this to it and only those two say otherwise.
     *
     * <p>Decided at the spawn rather than asked at the time, exactly as a pickup's {@code shunned}
     * is - and for ruined ground it is derived rather than declared: fire is on nobody's side and
     * catches everyone, so everyone gives it room; venom is somebody's problem and not everybody's,
     * and the things that made it walk through it (G0034, G0035).
     */
    public boolean avoided;

    /**
     * Whether a shot is used up on it.
     *
     * <p>The third question of the same shape, and it was found the same way {@link #avoided} was -
     * by two things wanting to disagree. A shot is spent on whatever stops it, whether or not that
     * thing can be hurt, which is right for a wall and for a body and wrong for <em>ground</em>:
     * ruined ground is a circle lying on the floor and it was eating every bullet fired across it,
     * so venom fenced the player's gun off as well as his feet.
     *
     * <p>Being in the way, being worth going round and stopping a shot are three questions, and this
     * component is where all three are answered as data. Being solid is still the answer for
     * everything else, so {@link #set} defaults this to it and only ruined ground says otherwise.
     */
    public boolean stopsShots;

    /**
     * Touches whatever lies along the way it came this tick, not only where it ended up. A shot is: it
     * crosses more ground in a tick than a small body is wide, and tested only where it stands it went
     * clean through a barrel one time in five. Nothing solid is, because nothing solid is fast.
     */
    public boolean swept;

    /** Sets both flags and returns this component, for fluent spawning. */
    public Collider set(boolean newSolid, boolean newMovable) {
        this.solid = newSolid;
        this.movable = newMovable;
        this.present = true;
        this.avoided = newSolid;
        this.stopsShots = true;
        this.swept = false;
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
    }
}
