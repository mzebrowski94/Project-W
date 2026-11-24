package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * An orb (G0128): a plasma cannon's shot, beaming as it flies at up to {@link #count} aliens within {@link #reach} of
 * it - the laser's kind of beam, a stream of short shots on the side it was thrown from, so they spare our men - and
 * going off once they have landed its {@link #power}.
 *
 * <p>The orb itself is a shot on nobody's side, as a limpet is, so it goes off on our men as on theirs, and it
 * <strong>explodes by dying</strong> (G0018): what is here is the beaming and what is left to spend. Every point a beam
 * shot of its lands on anything alive comes off {@link #power}, read from the one record of the blow (G0057).
 */
public final class Orb implements Component, Pool.Poolable {

    /** The side it was thrown from: its beams are on it, and pass that side's men. */
    public Side side = Side.NEUTRAL;

    /** The slot whose tally what its beams kill counts for (G0044), or {@link Tally#NOBODY}. */
    public int credit = Tally.NOBODY;

    /** How much damage its beams may still land before it goes off, and how much they could at the throw. */
    public int power;

    public int powerAtTheThrow;

    /** How many aliens it beams at once, at most, and how far from its middle they may stand, in world units. */
    public int count;

    public float reach;

    /** The wait between one beam shot at each of them and the next, and how much of it is left, in ticks. */
    public int cooldownTicks;

    public int remainingTicks;

    /** What each beam shot is, and what it takes off what it lands on - its gun's damage scale folded in at the throw. */
    public WeaponKind gun = WeaponKind.SIDEARM;

    public int beamDamage;

    /** Sets the orb and returns this component, for fluent spawning. It beams on its first tick. */
    public Orb set(
            Side newSide,
            int newCredit,
            int newPower,
            int newCount,
            float newReach,
            int newCooldownTicks,
            WeaponKind newGun,
            int newBeamDamage) {
        this.side = newSide;
        this.credit = newCredit;
        this.power = newPower;
        this.powerAtTheThrow = newPower;
        this.count = newCount;
        this.reach = newReach;
        this.cooldownTicks = newCooldownTicks;
        this.remainingTicks = 0;
        this.gun = newGun;
        this.beamDamage = newBeamDamage;
        return this;
    }

    /** Takes what a beam shot of its landed off its power. */
    public void spend(int landed) {
        power -= landed;
    }

    /** Whether its beams have landed all its power: it goes off where it is. */
    public boolean spent() {
        return power <= 0;
    }

    /** How much of its power is left, in {@code [0, 1]}: what it dims by (pillar 5). */
    public float fraction() {
        return powerAtTheThrow <= 0 ? 0f : Math.max(0, power) / (float) powerAtTheThrow;
    }

    @Override
    public void reset() {
        side = Side.NEUTRAL;
        credit = Tally.NOBODY;
        power = 0;
        powerAtTheThrow = 0;
        count = 0;
        reach = 0f;
        cooldownTicks = 0;
        remainingTicks = 0;
        gun = WeaponKind.SIDEARM;
        beamDamage = 0;
    }
}
