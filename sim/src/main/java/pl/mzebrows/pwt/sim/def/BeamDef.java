package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * What a shot that beams keeps doing while it flies (G0128): a plasma cannon's <strong>orb</strong>. It keeps a beam -
 * the laser's kind, a stream of short fast shots drawn as a line - on each of up to {@code count} aliens within its
 * {@code reach}, nearest first, re-picked as it flies, and its beams spare our men. Every point they land on anything
 * alive comes off its {@code power}, and once that is spent it goes off where it is.
 *
 * <p>"Zero means not for me": {@link #off()} is every gun but the plasma cannon.
 *
 * @param count how many aliens it beams at once, at most; zero is a shot that beams nothing
 * @param reach how far from its middle an alien may stand to be beamed, in world units
 * @param cooldownSeconds the wait between one beam shot at each of them and the next
 * @param power how much damage its beams may land in all before it goes off
 * @param gun what each beam shot is - its speed, size, flight, damage and look - a gun no man carries
 */
@With
public record BeamDef(int count, float reach, float cooldownSeconds, int power, WeaponKind gun) {

    public BeamDef {
        Require.notNegative("beam count", count);
        Require.notNegative("beam reach", reach);
        Require.notNegative("beam cooldownSeconds", cooldownSeconds);
        Require.notNegative("beam power", power);
        if (count > 0 && (reach <= 0f || cooldownSeconds <= 0f || power <= 0)) {
            throw new IllegalArgumentException(
                    "a shot that beams has a reach, a wait between its shots and a power, was reach " + reach
                            + ", cooldownSeconds " + cooldownSeconds + ", power " + power);
        }
    }

    /** A shot that beams nothing. What every gun but the plasma cannon declares. */
    public static BeamDef off() {
        return new BeamDef(0, 0f, 0f, 0, WeaponKind.SIDEARM);
    }

    /** Whether its shot beams at all. */
    public boolean enabled() {
        return count > 0;
    }
}
