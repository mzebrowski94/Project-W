package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BOSS_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.PULSE_STUN_AFTER_HITS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SHOCK_DRAIN_PER_SECOND;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Shock;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The pulse rifle's shock (G0059): each bolt that lands builds it, a bigger body takes more, and full is
 * a stun - the ordinary declared stun. The stun empties it and nothing builds while it runs, so a held
 * trigger is not a stun-lock; let alone, it drains. The boss takes none.
 *
 * <p>The fixture's rifle stuns an ordinary body with four bolts, which a test fires all at once down the
 * lane so they land on one tick.
 */
class ShockTest {

    private static final float LANE_Y = 50f;
    private static final float TARGET_X = 50f;
    private static final float MUZZLE_X = 47f;

    /** Long enough for a bolt at sixty a second to cross three units and land. */
    private static final int UNTIL_IT_LANDS = 8;

    /** Enough health to outlive every bolt a test fires into it. */
    private static final int TOUGH = 10_000;

    private static final Offset<Float> CLOSE = Offset.offset(1e-3f);

    @Test
    void stunsAThingOnceEnoughBoltsHaveLandedOnIt() {
        Simulation simulation = tough();
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        bolts(simulation, PULSE_STUN_AFTER_HITS);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Stunned.class).active()).isTrue();
        assertThat(enemy.getComponent(Appearance.class).state).isEqualTo(Appearance.State.STUNNED);
    }

    @Test
    void leavesAThingActingUntilTheLastBoltLands() {
        Simulation simulation = tough();
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        bolts(simulation, PULSE_STUN_AFTER_HITS - 1);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Stunned.class).active()).isFalse();
        assertThat(enemy.getComponent(Shock.class).charge).isGreaterThan(0f);
    }

    /** In proportion to the radius, as a bloat is: a body twice as wide takes twice the bolts. */
    @Test
    void takesMoreBoltsToStunABiggerBody() {
        Simulation simulation = new Simulation(
                everyStrain(config(), strain -> strain.withHealth(TOUGH).withRadius(ENEMY_RADIUS * 2f)));
        Entity enemy = simulation.spawnEnemy(TARGET_X + ENEMY_RADIUS, LANE_Y);
        bolts(simulation, PULSE_STUN_AFTER_HITS);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Stunned.class).active()).isFalse();
        assertThat(enemy.getComponent(Shock.class).charge).isCloseTo(0.5f, Offset.offset(0.05f));
    }

    @Test
    void emptiesWhenTheStunLands() {
        Simulation simulation = tough();
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        bolts(simulation, PULSE_STUN_AFTER_HITS);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Shock.class).charge).isZero();
    }

    /** Or a held trigger keeps it stunned for ever, which is a stun-lock rather than an opening. */
    @Test
    void buildsNothingWhileTheStunRuns() {
        Simulation simulation = tough();
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        bolts(simulation, PULSE_STUN_AFTER_HITS);
        steps(simulation, UNTIL_IT_LANDS);
        bolts(simulation, PULSE_STUN_AFTER_HITS - 1);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Stunned.class).active()).isTrue();
        assertThat(enemy.getComponent(Shock.class).charge).isZero();
    }

    @Test
    void drainsOnceNothingHitsIt() {
        Simulation simulation = tough();
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        bolts(simulation, 2);
        steps(simulation, UNTIL_IT_LANDS);
        float built = enemy.getComponent(Shock.class).charge;

        steps(simulation, ticksIn(built / SHOCK_DRAIN_PER_SECOND) + 1);

        assertThat(built).isGreaterThan(0f);
        assertThat(enemy.getComponent(Shock.class).charge).isZero();
    }

    /** Pillar 5: how close a thing is to being stunned is on its charge ring. */
    @Test
    void showsItOnTheChargeRing() {
        Simulation simulation = tough();
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        bolts(simulation, 2);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Appearance.class).chargeLook).isEqualTo(Look.SHOCK);
        assertThat(enemy.getComponent(Appearance.class).charge)
                .isCloseTo(enemy.getComponent(Shock.class).charge, CLOSE)
                .isGreaterThan(0f);
    }

    /** A stun would not pause its barrage (G0048), so a stunned boss is a lie on the screen (G0059). */
    @Test
    void neverBuildsOnTheBoss() {
        Simulation simulation = tough();
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MUZZLE_X + 1f + BOSS_RADIUS, LANE_Y);
        bolts(simulation, PULSE_STUN_AFTER_HITS * 8);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(boss.getComponent(Shock.class).charge).isZero();
        assertThat(boss.getComponent(Stunned.class).active()).isFalse();
    }

    /** One or none is the stun every gun had before shock: on every hit. */
    @Test
    void stunsOnEveryHitForAGunThatNamesOneHit() {
        SimConfig config = everyStrain(config(), strain -> strain.withHealth(TOUGH));
        Simulation simulation = new Simulation(
                config.withWeapons(config.weapons().map(WeaponKind.PULSE, gun -> gun.withStunAfterHits(1))));
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        bolts(simulation, 1);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Stunned.class).active()).isTrue();
        assertThat(enemy.getComponent(Shock.class).charge).isZero();
    }

    @Test
    void buildsNothingFromAGunThatDoesNotStun() {
        Simulation simulation = tough();
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        simulation.spawnProjectile(MUZZLE_X, LANE_Y, 1f, 0f, Side.PLAYER);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Shock.class).charge).isZero();
    }

    private static Simulation tough() {
        return new Simulation(everyStrain(config(), strain -> strain.withHealth(TOUGH)));
    }

    /** That many bolts, all on the player's side and all down the lane at once. */
    private static void bolts(Simulation simulation, int count) {
        for (int bolt = 0; bolt < count; bolt++) {
            simulation.spawnProjectile(MUZZLE_X, LANE_Y, 1f, 0f, WeaponKind.PULSE, Side.PLAYER);
        }
    }
}
