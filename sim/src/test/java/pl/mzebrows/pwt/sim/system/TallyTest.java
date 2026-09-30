package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CASUALTY_STABILISE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GRENADE_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_CAPTURE_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * A tally belongs to a life (G0044): what a commander killed, took and stabilised, counted on him by the
 * slot the blow carried, never by whoever the grid visited first. Shown, never spent.
 */
class TallyTest {

    private static final float LANE = 50f;

    private static final float SHOOTER = 20f;

    /** Well inside a sidearm's reach, down the lane. */
    private static final float TARGET = 30f;

    /** Somewhere nothing on the lane reaches. */
    private static final float ASIDE = 90f;

    private static final float BESIDE = 2f;

    private static final int A_WHILE = ticksIn(3f);

    private static final CommanderInput STILL = CommanderInput.IDLE;

    /** As good as standing still: a strain must move, but this one gets nowhere in a test. */
    private static final float CRAWL = 0.001f;

    /** Creatures that stand where they are put and hurt nobody: a test about credit, not about a fight. */
    private static Simulation stillWorld() {
        return new Simulation(
                everyStrain(config(), strain -> strain.withSpeed(CRAWL).withContactDamage(0)));
    }

    @Test
    void creditsTheKillToTheSlotWhoseShotLandedIt() {
        Simulation simulation = stillWorld();
        Entity bystander = simulation.spawnCommander(SHOOTER, ASIDE, 0);
        Entity shooter = simulation.spawnCommander(SHOOTER, LANE, 1);
        simulation.spawnEnemy(TARGET, LANE);

        stepWhileAnEnemyStands(simulation, 1, firingAt(TARGET));

        assertThat(countLiving(simulation, Enemy.class)).isZero();
        assertThat(tally(shooter).kills).isOne();
        assertThat(tally(bystander).kills).isZero();
    }

    @Test
    void creditsNothingForAKillNoPlayerCaused() {
        Simulation simulation = stillWorld();
        Entity commander = simulation.spawnCommander(SHOOTER, ASIDE, 0);
        simulation.spawnEnemy(TARGET, LANE);

        for (int shot = 0; shot < 4; shot++) {
            simulation.spawnProjectile(TARGET - 0.5f, LANE, 1f, 0f);
            simulation.step();
        }

        assertThat(countLiving(simulation, Enemy.class)).isZero();
        assertThat(tally(commander).kills).isZero();
    }

    @Test
    void creditsTheKillToWhoeverThrewTheGrenade() {
        Simulation simulation = stillWorld();
        Entity thrower = simulation.spawnCommander(SHOOTER, LANE, 1);
        simulation.spawnEnemy(SHOOTER + GRENADE_RANGE, LANE);

        simulation.step(as(1, throwingAt(SHOOTER + GRENADE_RANGE)));
        stepWhileAnEnemyStands(simulation, 1, STILL);

        assertThat(countLiving(simulation, Enemy.class)).isZero();
        assertThat(tally(thrower).kills).isOne();
    }

    /** A barrel is nobody's, but the blow that set it off was somebody's - and so is what it kills. */
    @Test
    void creditsABlastToWhoeverSetItOff() {
        Simulation simulation = stillWorld();
        Entity shooter = simulation.spawnCommander(SHOOTER, LANE, 1);
        simulation.spawnBarrel(TARGET, LANE);
        simulation.spawnEnemy(TARGET + BESIDE, LANE + BESIDE * 2f);

        stepWhileAnEnemyStands(simulation, 1, firingAt(TARGET));

        assertThat(countLiving(simulation, Enemy.class)).isZero();
        assertThat(tally(shooter).kills).isOne();
    }

    @Test
    void creditsTheKillToTheCommanderCrewingTheGun() {
        Simulation simulation = stillWorld();
        Entity gunner = simulation.spawnCommander(SHOOTER, LANE, 1);
        simulation.spawnTurret(SHOOTER + BESIDE * 2f, LANE);
        simulation.spawnEnemy(TARGET + 10f, LANE);

        simulation.step(as(1, STILL.acting(true)));
        stepWhileAnEnemyStands(simulation, 1, firingAt(TARGET + 10f));

        assertThat(countLiving(simulation, Enemy.class)).isZero();
        assertThat(tally(gunner).kills).isOne();
    }

    /** Friendly fire is ignored (Q13): a man of his own finished by his blast is no kill. */
    @Test
    void countsNothingForKillingSomebodyOnHisOwnSide() {
        Simulation simulation = stillWorld();
        Entity shooter = simulation.spawnCommander(SHOOTER, LANE, 1);
        simulation.spawnBarrel(TARGET, LANE);
        Entity trooper = simulation.spawnTrooper(TARGET + BESIDE, LANE + BESIDE * 2f);
        trooper.getComponent(Damage.class).land(0, Harm.DOWN);

        for (int tick = 0; tick < A_WHILE && !trooper.getComponent(Dying.class).begun; tick++) {
            simulation.step(as(1, firingAt(TARGET)));
        }

        assertThat(trooper.getComponent(Dying.class).begun).isTrue();
        assertThat(tally(shooter).kills).isZero();
    }

    @Test
    void creditsTheHigherSlotWhenTwoPlayersLandBlowsOnOneTick() {
        Damage forwards = new Damage();
        Damage backwards = new Damage();

        forwards.land(0, Harm.KILL, 1);
        forwards.land(0, Harm.KILL, 3);
        backwards.land(0, Harm.KILL, 3);
        backwards.land(0, Harm.KILL, 1);

        assertThat(forwards.credit).isEqualTo(3);
        assertThat(backwards.credit).isEqualTo(3);
    }

    @Test
    void startsFromNothingWhenHeComesBack() {
        Simulation simulation = stillWorld();
        Entity commander = simulation.spawnCommander(SHOOTER, LANE, 0);
        tally(commander).kills = 3;
        tally(commander).goals = 2;

        commander.getComponent(Invulnerable.class).remainingTicks = 0;
        commander.getComponent(Damage.class).land(0, Harm.KILL);
        for (int tick = 0; tick < ticksIn(DEATH_THROES + RESPAWN_DELAY) + 2; tick++) {
            simulation.step();
        }

        Entity back = inSlot(simulation, 0);
        assertThat(tally(back).kills).isZero();
        assertThat(tally(back).goals).isZero();
    }

    @Test
    void countsAStabiliseForTheManWhoFinishedIt() {
        Simulation simulation = stillWorld();
        Entity medic = simulation.spawnCommander(LANE, LANE, 0);
        Entity bystander = simulation.spawnCommander(SHOOTER, ASIDE, 1);
        Entity trooper = simulation.spawnTrooper(LANE + BESIDE, LANE);
        trooper.getComponent(Damage.class).land(0, Harm.DOWN);

        for (int tick = 0; tick < ticksIn(CASUALTY_STABILISE) + 2; tick++) {
            simulation.step(as(0, STILL.acting(true)));
        }

        assertThat(tally(medic).stabilised).isOne();
        assertThat(tally(bystander).stabilised).isZero();
    }

    /** A stabilise is a stabilise (Q14): another player's commander counts as much as a trooper. */
    @Test
    void countsPuttingAnotherPlayerBackOnHisFeet() {
        Simulation simulation = stillWorld();
        Entity medic = simulation.spawnCommander(LANE, LANE, 0);
        Entity patient = simulation.spawnCommander(LANE + BESIDE, LANE, 1);
        patient.getComponent(Invulnerable.class).remainingTicks = 0;
        patient.getComponent(Damage.class).land(0, Harm.DOWN);

        for (int tick = 0; tick < ticksIn(CASUALTY_STABILISE) + 4; tick++) {
            simulation.step(as(0, STILL.acting(true)));
        }

        assertThat(tally(medic).stabilised).isOne();
    }

    @Test
    void countsAPointTakenWhileHeStoodOnIt() {
        Simulation simulation = stillWorld();
        Entity holder = simulation.spawnCommander(LANE, LANE, 0);
        Entity elsewhere = simulation.spawnCommander(SHOOTER, ASIDE, 1);
        simulation.spawnControlPoint(LANE, LANE);

        for (int tick = 0; tick < ticksIn(POINT_CAPTURE_SECONDS) + 2; tick++) {
            simulation.step();
        }

        assertThat(tally(holder).goals).isOne();
        assertThat(tally(elsewhere).goals).isZero();
    }

    private static void stepWhileAnEnemyStands(Simulation simulation, int slot, CommanderInput input) {
        for (int tick = 0; tick < A_WHILE && countLiving(simulation, Enemy.class) > 0; tick++) {
            simulation.step(as(slot, input));
        }
    }

    private static PlayerInputs as(int slot, CommanderInput input) {
        return new PlayerInputs().set(slot, input);
    }

    private static CommanderInput firingAt(float x) {
        return new CommanderInput(0f, 0f, x, LANE, true, false);
    }

    private static CommanderInput throwingAt(float x) {
        return new CommanderInput(0f, 0f, x, LANE, false, false, true, false, false, false);
    }

    private static Tally tally(Entity commander) {
        return commander.getComponent(Tally.class);
    }

    private static Entity inSlot(Simulation simulation, int slot) {
        Entity commander = Players.inSlot(
                simulation.engine().getEntitiesFor(Family.all(Commander.class).get()), slot);
        assertThat(commander).as("a commander in slot %s", slot).isNotNull();
        return commander;
    }
}
