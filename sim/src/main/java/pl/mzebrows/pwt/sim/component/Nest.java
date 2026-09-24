package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A hole in the ground that keeps sending more of one strain out of it.
 *
 * <p>Everything a level had was on the field from the first tick, or arrived on the level's own
 * schedule ({@code Waves}). A nest is the third thing: a <strong>place</strong> that produces, which
 * is what turns clearing ground into holding it. Leave one standing and the ground you walked through
 * fills in behind you.
 *
 * <p><strong>It sleeps until somebody comes near.</strong> On a march four screens long, a nest that
 * hatched from the first tick would have sent twenty creatures at empty ground before the squad
 * arrived, and the player would meet the accumulation rather than the nest. {@link #awake} latches:
 * once it has seen somebody it keeps working, because a nest that went quiet when you backed off
 * would be a nest you could wait out.
 *
 * <p>{@link #watchedHealth} is how it knows it is being attacked. Damage is applied and cleared long
 * before the system that runs a nest, so what is left to read is the bar itself - a nest with less
 * health than it had last tick is a nest somebody is hitting, and that is when it calls the
 * neighbours (G0016, the same answer a control point gives when an attempt starts on it).
 */
public final class Nest implements Component, Pool.Poolable {

    /** What comes out of it. The whole of what makes one nest different from another. */
    public UnitKind kind = UnitKind.GRUNT;

    /** How long between broods, in seconds. */
    public float periodSeconds;

    /** How long until the next one. Only counted down while it is awake. */
    public float untilNext;

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

    /** What its health was last tick, so a system can tell it has been hit without reading damage. */
    public float watchedHealth;

    /**
     * Which brood sizes have already come out of the current bag, one bit per size above
     * {@link #broodMin} ({@code map.Bag}). Zero is a full bag.
     */
    public long broodsDealt;

    /** Sets what this nest produces and how, and returns this component, for fluent spawning. */
    public Nest set(
            UnitKind newKind,
            float newPeriodSeconds,
            int newBroodMin,
            int newBroodMax,
            int newDeathBroodMin,
            int newDeathBroodMax,
            float newHatchRadius,
            float newWakeRadius,
            float newRouseRadius) {
        this.kind = newKind;
        this.periodSeconds = newPeriodSeconds;
        this.untilNext = newPeriodSeconds;
        this.broodMin = newBroodMin;
        this.broodMax = newBroodMax;
        this.deathBroodMin = newDeathBroodMin;
        this.deathBroodMax = newDeathBroodMax;
        this.hatchRadius = newHatchRadius;
        this.wakeRadius = newWakeRadius;
        this.rouseRadius = newRouseRadius;
        return this;
    }

    /** Starts the first clock. Called once, when somebody first comes near enough. */
    public void wake() {
        this.awake = true;
        this.untilNext = periodSeconds;
    }

    /**
     * How close the next brood is, in {@code [0, 1]}.
     *
     * <p>Published on the charge channel so the player can see one coming rather than be surprised by
     * it (pillar 5). A nest that has not woken reports nothing, which is the honest answer: its clock
     * is not running.
     */
    public float progress() {
        if (!awake || periodSeconds <= 0f) {
            return 0f;
        }
        float spent = (periodSeconds - untilNext) / periodSeconds;
        if (spent < 0f) {
            return 0f;
        }
        return spent > 1f ? 1f : spent;
    }

    @Override
    public void reset() {
        kind = UnitKind.GRUNT;
        periodSeconds = 0f;
        untilNext = 0f;
        broodMin = 0;
        broodMax = 0;
        deathBroodMin = 0;
        deathBroodMax = 0;
        hatchRadius = 0f;
        wakeRadius = 0f;
        rouseRadius = 0f;
        awake = false;
        watchedHealth = 0f;
        broodsDealt = 0L;
    }
}
