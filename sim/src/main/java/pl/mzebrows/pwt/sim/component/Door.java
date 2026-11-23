package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.map.TileRect;

/**
 * What stands in a doorway (G0069): a wall that opens. Shut, its doorway's tiles are solid ground to everything that
 * moves, shoots, explodes or looks - not a body, which a crowd could lean through (ADR 0022) - and a route still runs
 * through it. It is opened by hand, on stabilise's rules, and never shut again: an opened door sinks out of sight and
 * leaves the field. A leap, a blast or a heavy gun breaks it ({@link Health}); a broken door is simply gone.
 *
 * <p>Every door starts shut. One door fits every doorway, so there is no table of them: one {@code sim.door} block.
 *
 * <p><strong>Ice is a door nobody opens</strong> (ADR 0025): across a way of a maze rather than in a doorway, its
 * {@code openTicks} zero, chipped by every shot from the squad's side, its health {@code sim.ice.health}.
 */
public final class Door implements Component, Pool.Poolable {

    /** The doorway's leftmost column. */
    public int col;

    /** The doorway's bottom row. */
    public int row;

    /** How many columns the doorway spans. */
    public int width;

    /** How many rows it spans. */
    public int height;

    /** How many ticks of work open it; zero for one nobody opens by hand - ice (ADR 0025). */
    public int openTicks;

    /**
     * Whether every shot from the squad's side chips it, by the shot's damage, rather than a heavy gun's alone - ice
     * (ADR 0025). False for a door in a doorway.
     */
    public boolean chipped;

    /** How much work has been done on it, in ticks. Lost on any tick nobody works on it. */
    public int workTicks;

    /** Whether somebody worked on it this tick: set by whoever is opening it, spent when the tick's work is counted. */
    public boolean worked;

    /** The one trooper opening it, by handle, or nobody (G0069). */
    public long trooper = Handle.NOBODY;

    /** Opened: its doorway is open ground from the tick the work was done, and it is sinking out of sight. */
    public boolean open;

    /** How many ticks an opened door is seen sinking for. */
    public int sinkTicks;

    /** How many of them are left; at none it leaves the field. */
    public int sinkingTicks;

    /** Sets its doorway and how it opens, shut and untouched, and returns this component, for fluent spawning. */
    public Door set(TileRect doorway, int newOpenTicks, int newSinkTicks) {
        this.col = doorway.col();
        this.row = doorway.row();
        this.width = doorway.width();
        this.height = doorway.height();
        this.openTicks = newOpenTicks;
        this.workTicks = 0;
        this.worked = false;
        this.trooper = Handle.NOBODY;
        this.open = false;
        this.sinkTicks = newSinkTicks;
        this.sinkingTicks = 0;
        this.chipped = false;
        return this;
    }

    /** Makes it chipped by every shot from the squad's side, and returns this component, for fluent spawning. */
    public Door chippedByEveryShot() {
        this.chipped = true;
        return this;
    }

    /** Whether anybody can open it by hand: a door can, ice cannot (ADR 0025). */
    public boolean opensByHand() {
        return openTicks > 0;
    }

    /**
     * How far a point is from the nearest part of its doorway, in world units: zero inside it. What arm's length,
     * a blast's radius and a leap's landing are measured to - a door has no body.
     */
    public float distanceFrom(float x, float y, float tileSize) {
        float left = col * tileSize;
        float bottom = row * tileSize;
        float outX = Math.max(Math.max(left - x, 0f), x - (left + width * tileSize));
        float outY = Math.max(Math.max(bottom - y, 0f), y - (bottom + height * tileSize));
        return (float) Math.sqrt(outX * outX + outY * outY);
    }

    /** Whether this tile is one of its doorway's. */
    public boolean covers(int atCol, int atRow) {
        return atCol >= col && atCol < col + width && atRow >= row && atRow < row + height;
    }

    /** How far through being opened it is, in {@code [0, 1]}: the door's own bar (G0069). */
    public float progress() {
        return open || openTicks <= 0 ? 0f : Math.min(1f, workTicks / (float) openTicks);
    }

    /** How far an opened door has sunk, in {@code [0, 1]}: one when it has gone. A look only. */
    public float sunk() {
        return !open || sinkTicks <= 0 ? (open ? 1f : 0f) : 1f - sinkingTicks / (float) sinkTicks;
    }

    @Override
    public void reset() {
        col = 0;
        row = 0;
        width = 0;
        height = 0;
        openTicks = 0;
        workTicks = 0;
        worked = false;
        trooper = Handle.NOBODY;
        open = false;
        sinkTicks = 0;
        sinkingTicks = 0;
        chipped = false;
    }
}
