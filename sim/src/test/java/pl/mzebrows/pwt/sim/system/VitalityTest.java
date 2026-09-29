package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_MAX_WOUNDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_MAX_WOUNDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WOUND_GRACE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WOUND_SHOVE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * A man is hurt in wounds, not health (G0053): every blow declares a harm, and what each harm does is
 * one table. These write straight to {@link Damage}, as contact resolution and a blast both do.
 */
class VitalityTest {

    /** Far above anything a health bar in the fixture holds, so an amount can be seen not to matter. */
    private static final int HUGE = 1_000_000;

    private static final int SMALL = 1;

    private static final float THROW = 20f;

    @Test
    void carriesNoHealthOnAManAtAll() {
        Simulation simulation = simulation();

        Entity trooper = simulation.spawnTrooper(50f, 50f);
        Entity commander = simulation.spawnCommander(20f, 20f);

        assertThat(trooper.getComponent(Health.class)).isNull();
        assertThat(commander.getComponent(Health.class)).isNull();
    }

    @Test
    void woundsAManWithoutPuttingHimDownHoweverHardTheBlow() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        hit(trooper, HUGE, Harm.WOUND);
        simulation.step();

        assertThat(vitality(trooper).wounds).isEqualTo(1);
        assertThat(vitality(trooper).begun).isFalse();
        assertThat(dying(trooper)).isFalse();
    }

    @Test
    void putsHimDownWithOneWoundMoreThanHeCanCarry() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        for (int wound = 0; wound <= TROOPER_MAX_WOUNDS; wound++) {
            hit(trooper, SMALL, Harm.WOUND);
            steps(simulation, ticksIn(WOUND_GRACE) + 2);
        }

        assertThat(vitality(trooper).begun).isTrue();
        assertThat(dying(trooper)).isFalse();
    }

    @Test
    void countsSeveralWoundsLandingOnOneTickAsOne() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        hit(trooper, SMALL, Harm.WOUND);
        hit(trooper, SMALL, Harm.WOUND);
        hit(trooper, SMALL, Harm.WOUND);
        simulation.step();

        assertThat(vitality(trooper).wounds).isEqualTo(1);
        assertThat(vitality(trooper).begun).isFalse();
    }

    @Test
    void turnsAWoundAwayDuringGrace() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        hit(trooper, SMALL, Harm.WOUND);
        simulation.step();
        hit(trooper, SMALL, Harm.WOUND);
        simulation.step();

        assertThat(vitality(trooper).wounds).isEqualTo(1);
        assertThat(vitality(trooper).begun).isFalse();
    }

    /** A pellet must never buy protection from a leap. */
    @Test
    void letsABlowThatPutsHimDownThroughGrace() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        hit(trooper, SMALL, Harm.WOUND);
        simulation.step();
        hit(trooper, SMALL, Harm.DOWN);
        simulation.step();

        assertThat(vitality(trooper).begun).isTrue();
    }

    @Test
    void putsHimDownWithADownBlowWhateverHisWounds() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        hit(trooper, SMALL, Harm.DOWN);
        simulation.step();

        assertThat(vitality(trooper).begun).isTrue();
        assertThat(dying(trooper)).isFalse();
    }

    @Test
    void killsHimOutrightWithAKillingBlow() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        hit(trooper, SMALL, Harm.KILL);
        simulation.step();

        assertThat(vitality(trooper).begun).isFalse();
        assertThat(dying(trooper)).isTrue();
    }

    @Test
    void letsTheWorstHarmOfATickWin() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        hit(trooper, SMALL, Harm.WOUND);
        hit(trooper, SMALL, Harm.KILL);
        hit(trooper, SMALL, Harm.DOWN);
        simulation.step();

        assertThat(dying(trooper)).isTrue();
    }

    @Test
    void takesNoWoundWhileHeIsDown() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        hit(trooper, SMALL, Harm.DOWN);
        simulation.step();

        hit(trooper, HUGE, Harm.WOUND);
        simulation.step();

        assertThat(vitality(trooper).active()).isTrue();
        assertThat(dying(trooper)).isFalse();
    }

    @Test
    void finishesAManWhoIsDownWithABlowThatWouldHavePutHimDownStanding() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        hit(trooper, SMALL, Harm.DOWN);
        simulation.step();

        hit(trooper, SMALL, Harm.DOWN);
        simulation.step();

        assertThat(dying(trooper)).isTrue();
    }

    @Test
    void woundsTheCommanderByTheSameTableAsHisMen() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        for (int wound = 0; wound < COMMANDER_MAX_WOUNDS; wound++) {
            hit(commander, SMALL, Harm.WOUND);
            steps(simulation, ticksIn(WOUND_GRACE) + 2);
        }

        assertThat(vitality(commander).wounds).isEqualTo(COMMANDER_MAX_WOUNDS);
        assertThat(vitality(commander).begun).isFalse();
    }

    @Test
    void showsHowManyWoundsHeIsCarryingWhereThePlayerCanSeeIt() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        hit(trooper, SMALL, Harm.WOUND);
        simulation.step();

        assertThat(trooper.getComponent(Appearance.class).wounds).isEqualTo(1);
        assertThat(trooper.getComponent(Appearance.class).state).isNotEqualTo(Appearance.State.CASUALTY);
    }

    /** Ground that wounds a man throws him off itself, and he is not his own for as long as it lasts. */
    @Test
    void throwsAManAWoundLeavesOnHisFeet() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        hit(trooper, SMALL, Harm.WOUND);
        trooper.getComponent(Damage.class).shove(1f, 0f, THROW, ticksIn(WOUND_SHOVE), THROW / WOUND_SHOVE);
        simulation.step();

        assertThat(trooper.getComponent(Velocity.class).x).isPositive();
        assertThat(trooper.getComponent(Stunned.class).active()).isTrue();
    }

    @Test
    void throwsNobodyAWoundPutsDown() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        hit(trooper, SMALL, Harm.WOUND);
        steps(simulation, ticksIn(WOUND_GRACE) + 2);

        hit(trooper, SMALL, Harm.WOUND);
        trooper.getComponent(Damage.class).shove(1f, 0f, THROW, ticksIn(WOUND_SHOVE), THROW / WOUND_SHOVE);
        simulation.step();

        assertThat(vitality(trooper).begun).isTrue();
        assertThat(trooper.getComponent(Velocity.class).x).isZero();
    }

    private static void hit(Entity entity, int amount, Harm harm) {
        entity.getComponent(Damage.class).add(amount, 0, harm);
    }

    private static Vitality vitality(Entity entity) {
        return entity.getComponent(Vitality.class);
    }

    private static boolean dying(Entity entity) {
        return entity.getComponent(Dying.class).begun;
    }
}
