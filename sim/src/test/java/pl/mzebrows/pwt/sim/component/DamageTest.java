package pl.mzebrows.pwt.sim.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * How a tick's blows are recorded and combined, before anything decides what they cost. Whatever lands a
 * blow - contact, a grip, a blast - says so through {@link Damage#land}, so the rules for combining them
 * live here once rather than in each of those.
 */
class DamageTest {

    private static final int SOME = 30;
    private static final int MORE = 45;
    private static final int RECOVERY = 12;
    private static final int STUN = 20;
    private static final float SLOW = 0.5f;
    private static final int SLOW_TICKS = 15;
    private static final float BLOAT = 0.1f;
    private static final int SLOT = 2;

    /** A blow that owes nothing and harms nobody still landed (G0057). */
    @Test
    void landsABlowThatOwesNothing() {
        Damage damage = new Damage();

        damage.land(0, Harm.NONE);

        assertThat(damage.landed).isTrue();
        assertThat(damage.amount).isZero();
    }

    @Test
    void addsUpWhatEveryBlowOwesAndKeepsTheWorstHarm() {
        Damage damage = new Damage();

        damage.land(SOME, Harm.WOUND);
        damage.land(MORE, Harm.NONE);

        assertThat(damage.amount).isEqualTo(SOME + MORE);
        assertThat(damage.harm).isEqualTo(Harm.WOUND);
    }

    /** Everything a source declares arrives with its blow, so no writer has to copy a field of it. */
    @Test
    void takesEverythingASourceDeclaresFromIt() {
        Damage damage = new Damage();
        ContactDamage claws = new ContactDamage()
                .set(SOME, 0, RECOVERY)
                .harming(Harm.DOWN)
                .stunning(STUN)
                .slowing(SLOW, SLOW_TICKS)
                .pumping(BLOAT)
                .credited(SLOT);

        damage.land(claws);

        assertThat(damage.landed).isTrue();
        assertThat(damage.amount).isEqualTo(SOME);
        assertThat(damage.recoveryTicks).isEqualTo(RECOVERY);
        assertThat(damage.harm).isEqualTo(Harm.DOWN);
        assertThat(damage.stunTicks).isEqualTo(STUN);
        assertThat(damage.slowFactor).isEqualTo(SLOW);
        assertThat(damage.slowTicks).isEqualTo(SLOW_TICKS);
        assertThat(damage.bloat).isEqualTo(BLOAT);
        assertThat(damage.credit).isEqualTo(SLOT);
    }

    /** Nothing a blow recorded - where it came from above all - outlives the tick it landed on. */
    @Test
    void forgetsEverythingOnceTheTickIsCleared() {
        Damage damage = new Damage();
        damage.land(
                new ContactDamage().set(SOME, 0, RECOVERY).harming(Harm.KILL).credited(SLOT));
        damage.cameFrom(1f, 2f);

        damage.reset();

        assertThat(damage.landed).isFalse();
        assertThat(damage.hasSource).isFalse();
        assertThat(damage.harm).isEqualTo(Harm.NONE);
        assertThat(damage.credit).isEqualTo(Tally.NOBODY);
    }
}
