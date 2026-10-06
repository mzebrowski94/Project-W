package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.SlingDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The riders a unit carries on its back and where it is in slinging the next of them (G0074).
 *
 * <p>The riders are a <strong>count</strong>, never entities: a rider is on the field only once it has been slung,
 * or spilled when the unit dies (ADR 0016). Carried by every unit; {@code riders} zero is a unit with nothing to
 * sling. The configuration half is per strain, written with it; the rest is {@code SlingSystem}'s.
 */
public final class Sling implements Component, Pool.Poolable {

    /** Where a unit is in slinging a rider. */
    public enum Phase {
        /** Walking, and slinging the moment what it hunts is within range. */
        READY,
        /** Standing still, tinted: the tell before the rider leaves. */
        WINDING_UP,
        /** The rider is away; the next may not be wound up until this runs out. */
        RESTING
    }

    /** How many riders it still carries. Never reloaded. */
    public int riders;

    public @Nullable UnitKind rider;
    public float minRange;
    public float maxRange;
    public int windUpTicks;
    public int restTicks;
    public float speed;
    public float landBehind;
    public int landingTicks;
    public float spillRadius;

    public Phase phase = Phase.READY;

    /** Counts down the phase it is in. */
    public int remainingTicks;

    /** Sets the configuration and a full back, for spawning and evolving alike. */
    public Sling set(SlingDef def, int newWindUpTicks, int newRestTicks, int newLandingTicks) {
        this.riders = def.riders();
        this.rider = def.rider();
        this.minRange = def.minRange();
        this.maxRange = def.maxRange();
        this.windUpTicks = newWindUpTicks;
        this.restTicks = newRestTicks;
        this.speed = def.speed();
        this.landBehind = def.landBehind();
        this.landingTicks = newLandingTicks;
        this.spillRadius = def.spillRadius();
        this.phase = Phase.READY;
        this.remainingTicks = 0;
        return this;
    }

    /** Whether it still has a rider aboard: its hands are full, and it does not leap (G0074). */
    public boolean loaded() {
        return riders > 0 && rider != null;
    }

    /** True while it stands showing the sling to come. */
    public boolean windingUp() {
        return phase == Phase.WINDING_UP;
    }

    /** The fraction of its pace it keeps: none while it winds up a sling, which it stands for. */
    public float paceFactor() {
        return windingUp() ? 0f : 1f;
    }

    /** Back to walking, with the rider it was winding up still aboard. */
    public void breakOff() {
        phase = Phase.READY;
        remainingTicks = 0;
    }

    @Override
    public void reset() {
        riders = 0;
        rider = null;
        minRange = 0f;
        maxRange = 0f;
        windUpTicks = 0;
        restTicks = 0;
        speed = 0f;
        landBehind = 0f;
        landingTicks = 0;
        spillRadius = 0f;
        phase = Phase.READY;
        remainingTicks = 0;
    }
}
