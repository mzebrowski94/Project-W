package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BLAST_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LASER_BLOAT_PER_SHOT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.burning;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Bloat;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * Turning a body into a bomb.
 *
 * <p>Sustained beam pumps a thing up. What fills is <strong>drawn on</strong> it - a ring growing
 * over the body, published on the charge channel - and the body itself is never touched: health is
 * still what kills, and a gun that killed by inflation would be a gun with two ways of killing and
 * one heat bar. At the brim a thing that is already dying bursts <strong>by dying</strong>, through
 * the one death reaction everything that explodes uses (G0018), and it leaves nothing burning.
 *
 * <p>A bigger body takes proportionally longer to fill and leaves a proportionally wider circle, so
 * a brute is a decision and a grunt is an afterthought - and nothing was said per strain.
 *
 * <p>Let go and it goes down again, and the corpse waits while there is any charge on it at all -
 * which is what makes a half-filled body something to come back to rather than something that had to
 * be finished in one go.
 *
 * <p>The fixture's beam is worth a quarter of a body per shot and half a grunt's health, so two
 * shots kill one and leave it half full, and four kill it and burst it. That is a fixture number and
 * not the game's: what balances the mechanic in play is the heat bar, and a test cannot see one.
 */
class BloatTest {

    private static final float LANE_Y = 50f;
    private static final float TARGET_X = 50f;
    private static final float MUZZLE_X = 47f;

    /** Long enough for a shot at sixty a second to cross three units and for the death chain to run. */
    private static final int UNTIL_IT_LANDS = 8;

    /** Off the lane, close enough to be inside the circle a body leaves, and never in the way. */
    private static final float BYSTANDER_Y = LANE_Y + 4f;

    /** A fraction for the one test that is about the fraction. */
    private static final float HALF = 0.5f;

    /** Faster than the gun can fill, so a charge that settled on a fed tick could never rise. */
    private static final float DRAINING = 30f;

    /** Long enough for a beam held point blank to put several shots into the same body. */
    private static final int UNDER_A_HELD_BEAM = 12;

    /** How many shots of the fixture's beam one ordinary body is worth. */
    private static final int ENOUGH_TO_FILL_ONE = 4;

    /** Enough health to outlive every shot a test about a full body has to fire into it. */
    private static final float TOUGH = 10_000f;

    @Test
    void chargesUpAThingTheBeamHasBeenHeldOn() {
        Simulation simulation = new Simulation(config());
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        beam(simulation, 2);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Bloat.class).charge).isGreaterThan(0f);
    }

    /**
     * The charge is a ring drawn over the body, never the body. A corpse that physically swelled
     * shoved everything standing near it around and quietly changed what a doorway was worth, which
     * is a great deal of consequence for a tell.
     */
    @Test
    void neverTouchesTheBodyItIsDrawnOn() {
        Simulation simulation = new Simulation(config());
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        beam(simulation, 2);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Body.class).radius).isCloseTo(ENEMY_RADIUS, Offset.offset(1e-5f));
    }

    /**
     * Drawn on the living as readily as on the dead, so the player can watch the thing he is burning
     * fill up and decide whether to keep the beam on it through the kill.
     */
    @Test
    void showsItOnSomethingStillOnItsFeet() {
        Simulation simulation = new Simulation(config());
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        beam(simulation, 1);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Dying.class).begun).isFalse();
        assertThat(enemy.getComponent(Appearance.class).chargeLook).isEqualTo(Look.BLOAT);
        assertThat(enemy.getComponent(Appearance.class).charge).isGreaterThan(0f);
    }

    /** Health is what kills. A living thing at the brim waits to be killed rather than going off. */
    @Test
    void leavesSomethingStillOnItsFeetStanding() {
        Simulation simulation = new Simulation(everyStrain(config(), strain -> strain.withHealth(TOUGH)));
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        beam(simulation, ENOUGH_TO_FILL_ONE * 2);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Dying.class).begun).isFalse();
        assertThat(count(simulation, Blast.class)).isZero();
    }

    /**
     * <strong>A thing being charged does not settle.</strong> Draining it on the very ticks the beam
     * is on it makes the gun fight the clock rather than the body - and the clock wins on anything
     * large, because a bigger body takes the beam more slowly while the clock empties it at the same
     * rate. That is what made this read as broken: a grunt's corpse gained almost nothing per second
     * and a brute's went <em>down</em> while it was being burnt.
     *
     * <p>The world here drains far faster than the gun can fill, so a charge that settled on a fed
     * tick could never rise at all.
     */
    @Test
    void goesOnFillingWhileTheBeamIsOnItRatherThanFightingTheClock() {
        Simulation simulation = new Simulation(everyStrain(config(), strain -> strain.withHealth(TOUGH))
                .withBloat(config().bloat().withSettlePerSecond(DRAINING)));
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);

        for (int tick = 0; tick < UNDER_A_HELD_BEAM; tick++) {
            pointBlank(simulation);
            simulation.step();
        }

        assertThat(enemy.getComponent(Bloat.class).charge).isGreaterThan(0f);
    }

    /** And lets go of it once the beam does, which is what a partial charge is worth coming back to. */
    @Test
    void settlesOnceNothingIsPointedAtItAnyMore() {
        Simulation simulation = new Simulation(everyStrain(config(), strain -> strain.withHealth(TOUGH)));
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        beam(simulation, 2);

        steps(simulation, UNTIL_IT_LANDS);
        float banked = enemy.getComponent(Bloat.class).charge;
        steps(simulation, ticksIn(0.5f));

        assertThat(enemy.getComponent(Bloat.class).charge).isLessThan(banked);
    }

    /**
     * <strong>A bigger body takes proportionally longer, and leaves a wider circle.</strong> Nothing
     * was said per strain - a strain's size is already what differs between them, so the same shot
     * is worth less against more creature and the bang it eventually makes is worth more.
     *
     * <p>Asked of the numbers rather than of two creatures under fire, deliberately. A bigger body
     * is also a body a piercing shot spends longer inside, so a test that shot at one would be
     * measuring the pierce interval as much as this, and would go green or red on a tuning change
     * that has nothing to do with it.
     */
    @Test
    void takesLongerToFillABiggerBody() {
        SimConfig.BloatConfig bloat = config().bloat();

        float ordinary = bloat.perShotAgainst(ENEMY_RADIUS, LASER_BLOAT_PER_SHOT, true);
        float larger = bloat.perShotAgainst(ENEMY_RADIUS * 2f, LASER_BLOAT_PER_SHOT, true);

        assertThat(larger).isLessThan(ordinary);
    }

    /**
     * <strong>A thing still on its feet banks only a fraction.</strong> That is the whole of "partly
     * charged beforehand and finished later": burning something down leaves its body part of the way
     * there and a decision still to make about it, rather than handing over a bomb for nothing.
     */
    @Test
    void banksLessFromSomethingStillOnItsFeetThanFromABodyOnTheGround() {
        SimConfig.BloatConfig bloat = config().bloat().withLivingFactor(HALF);

        float standing = bloat.perShotAgainst(ENEMY_RADIUS, LASER_BLOAT_PER_SHOT, false);
        float down = bloat.perShotAgainst(ENEMY_RADIUS, LASER_BLOAT_PER_SHOT, true);

        assertThat(standing).isLessThan(down);
    }

    @Test
    void leavesAWiderCircleForABiggerBody() {
        SimConfig.BloatConfig bloat = config().bloat();

        assertThat(bloat.blastFor(ENEMY_RADIUS * 2f)).isGreaterThan(bloat.blastFor(ENEMY_RADIUS));
    }

    /**
     * It goes off by dying, so what catches the bystander is an ordinary blast - on nobody's side and
     * arriving through the one path everything that explodes arrives through (G0018).
     */
    @Test
    void burstsOneThatFillsAndCatchesWhatIsStandingBesideIt() {
        Simulation simulation = new Simulation(config());
        simulation.spawnEnemy(TARGET_X, LANE_Y);
        Entity bystander = simulation.spawnEnemy(TARGET_X, BYSTANDER_Y);
        beam(simulation, 4);

        steps(simulation, UNTIL_IT_LANDS + 4);

        assertThat(bystander.getComponent(Dying.class).begun).isTrue();
    }

    @Test
    void leavesABystanderOutsideTheCircleStanding() {
        Simulation simulation = new Simulation(config());
        simulation.spawnEnemy(TARGET_X, LANE_Y);
        Entity faraway = simulation.spawnEnemy(TARGET_X, LANE_Y + 20f);
        beam(simulation, 4);

        steps(simulation, UNTIL_IT_LANDS + 4);

        assertThat(faraway.getComponent(Dying.class).begun).isFalse();
    }

    /**
     * A charged body waits. Without it, half a heat bar spent on a corpse would be half a heat bar
     * lost the moment anything else needed shooting.
     */
    @Test
    void keepsAChargedCorpseOnTheFieldLongAfterItsThroesWouldHaveEnded() {
        Simulation simulation = new Simulation(config());
        simulation.spawnEnemy(TARGET_X, LANE_Y);
        beam(simulation, 2);

        steps(simulation, UNTIL_IT_LANDS + ticksIn(1f));

        assertThat(count(simulation, Enemy.class)).isEqualTo(1);
    }

    @Test
    void letsItDownAgainOnceTheBeamComesOff() {
        Simulation simulation = new Simulation(config());
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        beam(simulation, 2);

        steps(simulation, UNTIL_IT_LANDS);
        float charged = enemy.getComponent(Bloat.class).charge;
        steps(simulation, ticksIn(0.5f));

        assertThat(enemy.getComponent(Bloat.class).charge).isLessThan(charged);
    }

    /**
     * A flash, not a fire. A corpse bomb that fenced off the ground it went off on would be a tool
     * the player could not use twice in the same place - and it is the one explosion in the game
     * that says so, through a field on the reaction rather than a clause anywhere (G0035).
     */
    @Test
    void leavesNothingBurningWhereItWentOff() {
        Simulation simulation = new Simulation(burning());
        simulation.spawnEnemy(TARGET_X, LANE_Y);
        beam(simulation, 4);

        steps(simulation, UNTIL_IT_LANDS + ticksIn(BLAST_SECONDS) + 2);

        assertThat(count(simulation, Slick.class)).isZero();
    }

    /** The control: the same world burns the ground when an ordinary explosion goes off in it. */
    @Test
    void stillBurnsTheGroundWhereAnOrdinaryExplosionWent() {
        Simulation simulation = new Simulation(burning());
        simulation.spawnBarrel(TARGET_X, LANE_Y);
        simulation.spawnProjectile(MUZZLE_X, LANE_Y, 1f, 0f, Side.PLAYER);

        steps(simulation, UNTIL_IT_LANDS + ticksIn(BLAST_SECONDS) + 2);

        assertThat(count(simulation, Slick.class)).isPositive();
    }

    @Test
    void doesNothingAtAllForAGunThatDoesNotPump() {
        Simulation simulation = new Simulation(config());
        Entity enemy = simulation.spawnEnemy(TARGET_X, LANE_Y);
        simulation.spawnProjectile(MUZZLE_X, LANE_Y, 1f, 0f, Side.PLAYER);
        simulation.spawnProjectile(MUZZLE_X, LANE_Y, 1f, 0f, Side.PLAYER);

        steps(simulation, UNTIL_IT_LANDS);

        assertThat(enemy.getComponent(Dying.class).begun).isTrue();
        assertThat(enemy.getComponent(Bloat.class).charge).isZero();
    }

    /** That many shots of the beam, all on the player's side and all down the lane at once. */
    private static void beam(Simulation simulation, int shots) {
        beamAt(simulation, LANE_Y, shots);
    }

    /** One shot of the beam with its muzzle against the target, so it lands within a tick. */
    private static void pointBlank(Simulation simulation) {
        simulation.spawnProjectile(TARGET_X - 1f, LANE_Y, 1f, 0f, WeaponKind.LASER, Side.PLAYER);
    }

    private static void beamAt(Simulation simulation, float y, int shots) {
        for (int shot = 0; shot < shots; shot++) {
            simulation.spawnProjectile(MUZZLE_X, y, 1f, 0f, WeaponKind.LASER, Side.PLAYER);
        }
    }
}
