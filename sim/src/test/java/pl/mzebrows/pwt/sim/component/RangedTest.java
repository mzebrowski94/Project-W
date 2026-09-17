package pl.mzebrows.pwt.sim.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic behind "standing in a shooter's sights is the mistake". A cone that narrows with
 * every shot it lands on you, and a cooldown that shortens when you are close, are the two numbers
 * that turn a firing line from something to trade with into something to leave.
 */
class RangedTest {

    private static final float CONE = 1f;
    private static final float FAR = 100f;
    private static final float CLOSE_RANGE = 10f;
    private static final float CLOSE_FACTOR = 0.5f;
    private static final int SETTLE_SHOTS = 4;
    private static final float SETTLED = 0.25f;
    private static final float COOLDOWN = 2f;

    private static final Offset<Float> CLOSE_ENOUGH = Offset.offset(1e-5f);

    @Test
    void firesItsFirstShotAsWideAsTheConeItDeclares() {
        assertThat(shooter().halfSpreadFor(FAR)).isCloseTo(CONE, CLOSE_ENOUGH);
    }

    @Test
    void narrowsTheConeWithEveryShotItLands() {
        Ranged ranged = shooter();

        ranged.shotsOnTarget = 2;

        assertThat(ranged.halfSpreadFor(FAR)).isLessThan(CONE).isGreaterThan(CONE * SETTLED);
    }

    @Test
    void stopsNarrowingOnceItsAimIsWalkedIn() {
        Ranged ranged = shooter();

        ranged.shotsOnTarget = SETTLE_SHOTS * 3;

        assertThat(ranged.halfSpreadFor(FAR)).isCloseTo(CONE * SETTLED, CLOSE_ENOUGH);
    }

    /** Losing you is the counter to it, and it has to cost the shooter everything it had learnt. */
    @Test
    void goesBackToTheWildConeWhenItLosesWhatItWasShootingAt() {
        Ranged ranged = shooter();
        ranged.shotsOnTarget = SETTLE_SHOTS;

        ranged.loseTarget();

        assertThat(ranged.halfSpreadFor(FAR)).isCloseTo(CONE, CLOSE_ENOUGH);
    }

    @Test
    void shootsFasterAndStraighterAtSomethingClose() {
        Ranged ranged = shooter();

        assertThat(ranged.halfSpreadFor(CLOSE_RANGE - 1f)).isCloseTo(CONE * CLOSE_FACTOR, CLOSE_ENOUGH);
        assertThat(ranged.cooldownFor(COOLDOWN, CLOSE_RANGE - 1f)).isCloseTo(COOLDOWN * CLOSE_FACTOR, CLOSE_ENOUGH);
    }

    @Test
    void leavesBothAloneAtADistance() {
        Ranged ranged = shooter();

        assertThat(ranged.halfSpreadFor(FAR)).isCloseTo(CONE, CLOSE_ENOUGH);
        assertThat(ranged.cooldownFor(COOLDOWN, FAR)).isCloseTo(COOLDOWN, CLOSE_ENOUGH);
    }

    private static Ranged shooter() {
        return new Ranged()
                .set(true, 20f, CONE, true, true, -1f, 0f, CLOSE_RANGE, CLOSE_FACTOR, SETTLE_SHOTS, SETTLED, null);
    }
}
