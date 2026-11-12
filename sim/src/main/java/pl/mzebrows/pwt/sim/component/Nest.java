package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A hole in the ground that keeps sending more of one strain out of it.
 *
 * <p>A <strong>place</strong> that produces. <strong>It sleeps until somebody in its biome comes
 * near</strong> (G0062), and {@link #awake} latches. {@link #watchedHealth} is how it knows it is
 * being hit (G0016).
 */
public final class Nest implements Component, Pool.Poolable {

    /** What comes out of it. The whole of what makes one nest different from another. */
    public UnitKind kind = UnitKind.GRUNT;

    /** How long between broods, in ticks. */
    public int periodTicks;

    /** How long until the next one. Only counted down while it is awake. */
    public int untilNext;

    /** How many come out at a time, drawn between the two. */
    public int broodMin;

    public int broodMax;

    /** How many come out when it is destroyed. A nest is worst at the moment you kill it. */
    public int deathBroodMin;

    public int deathBroodMax;

    /** How far around it they are put down, in world units. */
    public float hatchRadius;

    /** How close somebody on the player's side must come before it starts working, in world units. */
    public float wakeRadius;

    /** How far its cry for help carries when it is attacked, in world units. */
    public float rouseRadius;

    /** True once somebody has come close enough. Latched: backing off does not put it to sleep. */
    public boolean awake;

    /**
     * Ticks spent left behind - nobody near - since its countdown last moved. Its countdown moves once
     * every {@code sim.nest.leftBehindSlowdown} of them, and this is back to zero the tick a man is near.
     */
    public int leftBehindTicks;

    /** What its health was last tick, so a system can tell it has been hit without reading damage. */
    public int watchedHealth;

    /**
     * Which brood sizes have already come out of the current bag, one bit per size above
     * {@link #broodMin} ({@code map.Bag}). Zero is a full bag.
     */
    public long broodsDealt;

    /** Sets what this nest produces and how, and returns this component, for fluent spawning. */
    public Nest set(
            UnitKind newKind,
            int newPeriodTicks,
            int newBroodMin,
            int newBroodMax,
            int newDeathBroodMin,
            int newDeathBroodMax,
            float newHatchRadius,
            float newWakeRadius,
            float newRouseRadius) {
        this.kind = newKind;
        this.periodTicks = newPeriodTicks;
        this.untilNext = newPeriodTicks;
        this.broodMin = newBroodMin;
        this.broodMax = newBroodMax;
        this.deathBroodMin = newDeathBroodMin;
        this.deathBroodMax = newDeathBroodMax;
        this.hatchRadius = newHatchRadius;
        this.wakeRadius = newWakeRadius;
        this.rouseRadius = newRouseRadius;
        return this;
    }

    /**
     * Starts the first clock, at the first brood rather than a whole period (G0062). Called once, when
     * somebody first comes near enough.
     */
    public void wake(int firstBroodTicks) {
        this.awake = true;
        this.untilNext = firstBroodTicks;
    }

    /**
     * How close the next brood is, in {@code [0, 1]}.
     *
     * <p>Pillar 5. A nest that has not woken reports nothing.
     */
    public float progress() {
        if (!awake || periodTicks <= 0) {
            return 0f;
        }
        float spent = (periodTicks - untilNext) / (float) periodTicks;
        if (spent < 0f) {
            return 0f;
        }
        return spent > 1f ? 1f : spent;
    }

    @Override
    public void reset() {
        kind = UnitKind.GRUNT;
        periodTicks = 0;
        untilNext = 0;
        broodMin = 0;
        broodMax = 0;
        deathBroodMin = 0;
        deathBroodMax = 0;
        hatchRadius = 0f;
        wakeRadius = 0f;
        rouseRadius = 0f;
        awake = false;
        leftBehindTicks = 0;
        watchedHealth = 0;
        broodsDealt = 0L;
    }
}
