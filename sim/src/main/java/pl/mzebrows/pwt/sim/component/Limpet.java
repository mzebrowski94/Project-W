package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * A limpet (G0100): thrown by a detonator, it flies the gun's reach, <strong>sticks</strong> to the first rock, door,
 * scenery or alien it touches - or to the ground where its reach runs out - and calibrates there.
 *
 * <p><strong>It explodes by dying</strong> (G0018), like everything that explodes: what is here is the throw, where
 * it is stuck and its clock, and the circle is a {@code BlastsOnDeath} whose size and harm change once it has
 * calibrated. Its owner and what it rides are named by handle (ADR 0016), never held.
 */
public final class Limpet implements Component, Pool.Poolable {

    /** Who threw it: the one man whose press sets it off, and whose losing the gun sets it off too. */
    public long owner = Handle.NOBODY;

    /** The side it was thrown from. It passes through that side's men and sticks to nobody on it. */
    public Side side = Side.NEUTRAL;

    /** How much of its reach is left while it flies, in world units. */
    public float remainingDistance;

    /** How far it has flown, in world units - what its turning is drawn from. Stops once it is stuck. */
    public float flown;

    /** True once it has stuck to something, or to the ground. It never comes unstuck. */
    public boolean stuck;

    /** What it rides, or {@link Handle#NOBODY} for ground, rock or the arena's edge that does not move. */
    public long host = Handle.NOBODY;

    /** Which way from its host's middle it sits, as a unit vector: the side it struck. Meaningless with no host. */
    public float bearingX;

    public float bearingY;

    /**
     * Whether it is stuck to a wall - rock or a shut door - and the point inside the wall it is stuck to. A door opened
     * or broken leaves that point open ground, and what it was stuck to has left the field.
     */
    public boolean onWall;

    public float wallX;

    public float wallY;

    /**
     * What it touched this tick, recorded by contact resolution and acted on afterwards, as a grenade's bounce is
     * (G0019): {@link #touched} says whether there was anything, {@link #touchedHost} what, and where it stood.
     */
    public boolean touched;

    public long touchedHost = Handle.NOBODY;

    public float touchedX;

    public float touchedY;

    /** How long it must be stuck before it has calibrated, and how much of that is left, in ticks. */
    public int calibrateTicks;

    public int remainingTicks;

    /** True once it has calibrated: its big blast is ready, and it waits for as long as it takes. */
    public boolean calibrated;

    /** The circle it leaves set off once calibrated, and what that does to a man. */
    public float calibratedBlastRadius;

    public Harm calibratedBlastHarm = Harm.DOWN;

    /** How far the cry of its sticking carries, in world units; zero is silent. */
    public float rouseRadius;

    /** Sets the throw and returns this component, for fluent spawning. */
    public Limpet set(
            long newOwner,
            Side newSide,
            float range,
            int newCalibrateTicks,
            float newCalibratedBlastRadius,
            Harm newCalibratedBlastHarm,
            float newRouseRadius) {
        this.owner = newOwner;
        this.side = newSide;
        this.remainingDistance = range;
        this.flown = 0f;
        this.stuck = false;
        this.host = Handle.NOBODY;
        this.bearingX = 0f;
        this.bearingY = 0f;
        this.onWall = false;
        this.wallX = 0f;
        this.wallY = 0f;
        this.touched = false;
        this.touchedHost = Handle.NOBODY;
        this.touchedX = 0f;
        this.touchedY = 0f;
        this.calibrateTicks = newCalibrateTicks;
        this.remainingTicks = newCalibrateTicks;
        this.calibrated = false;
        this.calibratedBlastRadius = newCalibratedBlastRadius;
        this.calibratedBlastHarm = newCalibratedBlastHarm;
        this.rouseRadius = newRouseRadius;
        return this;
    }

    /** Records the first thing it touched this tick; anything else touched on the same tick is passed. */
    public void touch(long what, float x, float y) {
        if (stuck || touched) {
            return;
        }
        this.touched = true;
        this.touchedHost = what;
        this.touchedX = x;
        this.touchedY = y;
    }

    /** How far it is through calibrating, in {@code [0, 1]}: one once calibrated, zero before it is stuck. */
    public float calibration() {
        if (!stuck) {
            return 0f;
        }
        if (calibrated || calibrateTicks <= 0) {
            return 1f;
        }
        return 1f - remainingTicks / (float) calibrateTicks;
    }

    @Override
    public void reset() {
        owner = Handle.NOBODY;
        side = Side.NEUTRAL;
        remainingDistance = 0f;
        flown = 0f;
        stuck = false;
        host = Handle.NOBODY;
        bearingX = 0f;
        bearingY = 0f;
        onWall = false;
        wallX = 0f;
        wallY = 0f;
        touched = false;
        touchedHost = Handle.NOBODY;
        touchedX = 0f;
        touchedY = 0f;
        calibrateTicks = 0;
        remainingTicks = 0;
        calibrated = false;
        calibratedBlastRadius = 0f;
        calibratedBlastHarm = Harm.DOWN;
        rouseRadius = 0f;
    }
}
