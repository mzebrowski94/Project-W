package pl.mzebrows.pwt.sim.system.commander;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Sentry;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * The sentry (G0129): a standing gun nobody crews, on the players' side. It fires at the nearest alien in its reach, the
 * aliens come for it as for a man, and it goes off - out of time or destroyed, one way - in a small blast that wounds a
 * man and a stun on the aliens round it, never on a man.
 */
class SentryTest {

    private static final float X = 40f;

    private static final float Y = 50f;

    // ---- its fire ----

    @Test
    void firesAtAnAlienInItsReach() {
        Simulation simulation = harmless();
        simulation.spawnSentry(X, Y, 0);
        Entity alien = simulation.spawnEnemy(X + range(simulation) * 0.5f, Y);

        steps(simulation, ticksIn(1f));

        assertThat(hurt(alien)).isTrue();
    }

    @Test
    void holdsItsFireAtAnAlienBeyondItsReach() {
        Simulation simulation = harmless();
        simulation.spawnSentry(X, Y, 0);
        simulation.spawnEnemy(X + range(simulation) * 2f, Y);

        steps(simulation, ticksIn(0.3f));

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void turnsOntoTheNearerOfTwoAliens() {
        Simulation simulation = harmless();
        Entity sentry = simulation.spawnSentry(X, Y, 0);
        simulation.spawnEnemy(X + range(simulation) * 0.8f, Y);
        simulation.spawnEnemy(X, Y + range(simulation) * 0.4f);

        steps(simulation, ticksIn(0.5f));

        assertThat(sentry.getComponent(Sentry.class).aimY).isGreaterThan(0.95f);
    }

    /** Its shots are on the players' side, so the man in their line takes nothing. */
    @Test
    void sparesAManInTheLineOfItsFire() {
        Simulation simulation = harmless();
        simulation.spawnSentry(X, Y, 0);
        Entity commander = simulation.spawnCommander(X + 6f, Y);
        Entity alien = simulation.spawnEnemy(X + 14f, Y);

        steps(simulation, ticksIn(1f));

        assertThat(unhurt(commander)).isTrue();
        assertThat(hurt(alien)).isTrue();
    }

    // ---- hunted ----

    @Test
    void drawsTheAliensFromTheMomentItStands() {
        Simulation simulation = harmless();
        simulation.spawnCommander(X - 40f, Y);
        Entity sentry = simulation.spawnSentry(X, Y, 0);
        Entity alien = simulation.spawnEnemy(X + 12f, Y);

        simulation.step();

        assertThat(alien.getComponent(Perception.class).kept).isEqualTo(Handles.of(sentry));
    }

    // ---- going off ----

    @Test
    void goesOffAtTheEndOfItsLife() {
        Simulation simulation = simulation();
        simulation.spawnSentry(X, Y, 0);

        steps(simulation, ticksIn(life(simulation)) - 2);
        assertThat(count(simulation, Sentry.class)).isOne();
        stepUntilABlast(simulation, 5);

        assertThat(count(simulation, Sentry.class)).isZero();
        assertThat(blast(simulation).damage)
                .isEqualTo(simulation.config().sentry().blastDamage());
    }

    @Test
    void goesOffTheSameWayWhenItIsDestroyed() {
        Simulation simulation = simulation();
        Entity sentry = simulation.spawnSentry(X, Y, 0);
        simulation.step();

        sentry.getComponent(Damage.class).land(sentry.getComponent(Health.class).max, Harm.NONE);
        stepUntilABlast(simulation, 3);

        assertThat(count(simulation, Sentry.class)).isZero();
        assertThat(blast(simulation).damage)
                .isEqualTo(simulation.config().sentry().blastDamage());
    }

    @Test
    void woundsAManCaughtInTheBlastItGoesOffIn() {
        Simulation simulation = simulation();
        Entity sentry = simulation.spawnSentry(X, Y, 0);
        Entity commander = simulation.spawnCommander(X + blastRadius(simulation) * 0.5f, Y);
        simulation.step();

        destroy(simulation, sentry);

        assertThat(commander.getComponent(Vitality.class).wounds).isEqualTo(1);
        assertThat(commander.getComponent(Vitality.class).begun).isFalse();
    }

    /** Round it, past its blast: the aliens are stunned, and a man standing among them is not. */
    @Test
    void stunsTheAliensRoundItAsItGoesOffAndNeverAMan() {
        Simulation simulation = harmless();
        Entity sentry = simulation.spawnSentry(X, Y, 0);
        float between = (blastRadius(simulation) + stunRadius(simulation)) * 0.5f;
        Entity alien = simulation.spawnEnemy(X - between, Y);
        Entity commander = simulation.spawnCommander(X, Y + between);
        simulation.step();

        destroy(simulation, sentry);

        assertThat(alien.getComponent(Stunned.class).active()).isTrue();
        assertThat(commander.getComponent(Stunned.class).active()).isFalse();
    }

    @Test
    void stunsNoAlienBeyondItsStunsReach() {
        Simulation simulation = harmless();
        Entity sentry = simulation.spawnSentry(X, Y, 0);
        Entity alien = simulation.spawnEnemy(X - stunRadius(simulation) * 1.5f, Y);
        simulation.step();

        destroy(simulation, sentry);

        assertThat(alien.getComponent(Stunned.class).active()).isFalse();
    }

    @Test
    void showsHowMuchOfItsLifeIsLeftOnItsCharge() {
        Simulation simulation = simulation();
        Entity sentry = simulation.spawnSentry(X, Y, 0);

        steps(simulation, ticksIn(life(simulation) * 0.5f));

        assertThat(sentry.getComponent(pl.mzebrows.pwt.sim.component.Appearance.class).charge)
                .isBetween(0.45f, 0.55f);
    }

    // ---- helpers ----

    /** Destroys it and steps on until its blast and its stun have landed. */
    private static void destroy(Simulation simulation, Entity sentry) {
        sentry.getComponent(Damage.class).land(sentry.getComponent(Health.class).max, Harm.NONE);
        steps(simulation, 3);
    }

    private static void stepUntilABlast(Simulation simulation, int most) {
        for (int tick = 0; tick < most && count(simulation, Blast.class) == 0; tick++) {
            simulation.step();
        }
    }

    private static Blast blast(Simulation simulation) {
        ImmutableArray<Entity> blasts =
                simulation.engine().getEntitiesFor(Family.all(Blast.class).get());
        assertThat(blasts.size()).isOne();
        return blasts.first().getComponent(Blast.class);
    }

    private static boolean hurt(Entity alien) {
        Health health = alien.getComponent(Health.class);
        return health.current < health.max;
    }

    private static SimConfig.SentryConfig sentry(Simulation simulation) {
        return simulation.config().sentry();
    }

    private static float range(Simulation simulation) {
        return sentry(simulation).range();
    }

    private static float life(Simulation simulation) {
        return sentry(simulation).lifeSeconds();
    }

    private static float blastRadius(Simulation simulation) {
        return sentry(simulation).blastRadius();
    }

    private static float stunRadius(Simulation simulation) {
        return sentry(simulation).stunRadius();
    }
}
