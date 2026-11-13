package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.SlickDef;

/**
 * A geyser's cycle (G0089): it lies dormant, fills, and erupts in fire, over and over.
 *
 * <p><strong>Dormant it is ground</strong> - no collider, so nothing stops on it, steers round it or is hurt by it. The
 * only dangerous thing about a geyser is its eruption, and that is ordinary fire: {@link #eruption} is laid through the
 * one slick path, by something dying (G0018, G0035), so a geyser's fire is a blast's fire in every way.
 *
 * <p>The phases run {@code DORMANT -> FILLING -> ERUPTING -> DORMANT}. How long each dormancy lasts is keyed off the
 * geyser and {@link #cycle} (G0086), so the cycle count is all a snapshot needs to carry the rhythm on.
 */
public final class Geyser implements Component, Pool.Poolable {

    /** Where a geyser is in its cycle. */
    public enum Phase {
        /** Quiet, walkable ground. */
        DORMANT,
        /** About to erupt: its fire mark pulses (pillar 5). */
        FILLING,
        /** Its fire is on the ground, until it burns out. */
        ERUPTING
    }

    public Phase phase = Phase.DORMANT;

    /** Ticks left of the phase it is in. */
    public int remainingTicks;

    /** How long it fills before it erupts, in ticks. */
    public int fillingTicks;

    /** How long one eruption's fire burns, in ticks; it lies dormant only once that is out. */
    public int burningTicks;

    /** How many eruptions it has finished: what the length of its next dormancy is keyed by. */
    public int cycle;

    /** What one eruption lays, sized to this geyser when it was put down. */
    public SlickDef eruption = SlickDef.off();

    /** Sets a geyser lying dormant for {@code dormantTicks} before its first filling, for fluent spawning. */
    public Geyser set(int dormantTicks, int newFillingTicks, int newBurningTicks, SlickDef newEruption) {
        this.phase = Phase.DORMANT;
        this.remainingTicks = dormantTicks;
        this.fillingTicks = newFillingTicks;
        this.burningTicks = newBurningTicks;
        this.cycle = 0;
        this.eruption = newEruption;
        return this;
    }

    /** How far through filling it is, in {@code (0, 1]} while filling and zero otherwise, for the renderer. */
    public float filled() {
        if (phase != Phase.FILLING || fillingTicks <= 0) {
            return 0f;
        }
        return 1f - remainingTicks / (float) fillingTicks;
    }

    @Override
    public void reset() {
        phase = Phase.DORMANT;
        remainingTicks = 0;
        fillingTicks = 0;
        burningTicks = 0;
        cycle = 0;
        eruption = SlickDef.off();
    }
}
