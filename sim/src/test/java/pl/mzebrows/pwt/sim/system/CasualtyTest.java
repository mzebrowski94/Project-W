package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CASUALTY_BLEED_OUT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COOLDOWN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Weapon;

/**
 * What a blow costs now depends on what kind of blow it was. Gunfire and explosions put a man on the
 * ground with a clock running; claws, a sweep and a leap kill him.
 *
 * <p>These tests write straight to the {@link Damage} component, which is exactly what contact
 * resolution and a blast both do - the whole point of the split is that neither of them knows
 * casualties exist.
 */
class CasualtyTest {

    /** Everything the player can ask for at once, so nothing is left as a maybe. */
    private static final CommanderInput WALKING_AND_SHOOTING = new CommanderInput(1f, 1f, 90f, 90f, true, true);

    /**
     * Long enough to be unmistakable, and asked for only <em>after</em> the hit has landed. The blow
     * is applied twenty systems after the one that drives him, so the tick he is hit is a tick he
     * still gets to act on - which is correct, and would otherwise look like the bug.
     */
    private static final int WHILE_HE_ASKS = 30;

    @Test
    void leavesATrooperShotDownOnTheGroundRatherThanDead() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        wound(trooper, TROOPER_HEALTH);
        simulation.step();

        assertThat(trooper.getComponent(Casualty.class).begun).isTrue();
        assertThat(trooper.getComponent(Dying.class).begun).isFalse();
    }

    @Test
    void killsATrooperOutrightWhenTheBlowIsOneThatKills() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        kill(trooper, TROOPER_HEALTH);
        simulation.step();

        assertThat(trooper.getComponent(Casualty.class).begun).isFalse();
        assertThat(trooper.getComponent(Dying.class).begun).isTrue();
    }

    /**
     * He is at zero health, so anything that reaches him takes him below it again and the ordinary
     * death path runs. Being shot while down and being eaten while down are the same rule, and
     * neither is a special case anybody had to write.
     */
    @Test
    void finishesACasualtyWithTheNextHitThatReachesHimWhateverItIs() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        wound(trooper, TROOPER_HEALTH);
        simulation.step();
        wound(trooper, 1f);
        simulation.step();

        assertThat(trooper.getComponent(Dying.class).begun).isTrue();
    }

    @Test
    void killsACasualtyNobodyReachedInTime() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        wound(trooper, TROOPER_HEALTH);
        steps(simulation, ticksIn(CASUALTY_BLEED_OUT) + 2);

        assertThat(trooper.getComponent(Dying.class).begun).isTrue();
    }

    @Test
    void leavesHimStandingUntilTheBleedOutIsActuallySpent() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        wound(trooper, TROOPER_HEALTH);
        steps(simulation, ticksIn(CASUALTY_BLEED_OUT) - 2);

        assertThat(trooper.getComponent(Dying.class).begun).isFalse();
        assertThat(trooper.getComponent(Casualty.class).active()).isTrue();
    }

    /** Nothing on the alien side can be rescued, so nothing on the alien side goes down instead of dying. */
    @Test
    void killsAnAlienOutrightHoweverItWasHurt() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        wound(enemy, ENEMY_HEALTH);
        simulation.step();

        assertThat(enemy.getComponent(Casualty.class)).isNull();
        assertThat(countLiving(simulation, Enemy.class)).isZero();
    }

    @Test
    void showsACasualtyAsOneAndCountsHisBleedOutDownWhereThePlayerCanSeeIt() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);

        wound(trooper, TROOPER_HEALTH);
        simulation.step();

        Appearance appearance = trooper.getComponent(Appearance.class);
        assertThat(appearance.state).isEqualTo(Appearance.State.CASUALTY);
        assertThat(appearance.charge).isCloseTo(1f, Offset.offset(0.05f));

        steps(simulation, ticksIn(CASUALTY_BLEED_OUT * 0.5f));

        assertThat(appearance.charge).isLessThan(0.6f).isGreaterThan(0.35f);
    }

    /**
     * A man on the ground is out of the fight. He gets off whatever he had already fired on the tick
     * he was hit - the shot left before the bullet landed - and then nothing, though there is still
     * something standing in front of him and his gun has long since cooled.
     */
    @Test
    void leavesACasualtyOutOfTheFight() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        simulation.spawnEnemy(60f, 50f);

        wound(trooper, TROOPER_HEALTH);
        steps(simulation, ticksIn(COOLDOWN) + 2);

        for (int i = 0; i < ticksIn(COOLDOWN); i++) {
            simulation.step();
            assertThat(trooper.getComponent(Weapon.class).remainingSeconds).isZero();
        }
        assertThat(trooper.getComponent(Casualty.class).active()).isTrue();
    }

    /**
     * He is one of his men in this as in everything else (pillar 2). It is only legitimate because
     * somebody would come and get him - see {@code AidTest} - and it is one line in {@code Spawner}
     * to take back if trooper revival ever goes away (G0023).
     */
    @Test
    void leavesTheCommanderShotDownOnTheGroundRatherThanDead() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        wound(commander, COMMANDER_HEALTH);
        simulation.step();

        assertThat(commander.getComponent(Casualty.class).begun).isTrue();
        assertThat(commander.getComponent(Dying.class).begun).isFalse();
    }

    /** Claws still finish him where a bullet only puts him down. That asymmetry is the whole rule. */
    @Test
    void killsTheCommanderOutrightWhenTheBlowIsOneThatKills() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        kill(commander, COMMANDER_HEALTH);
        simulation.step();

        assertThat(commander.getComponent(Casualty.class).begun).isFalse();
        assertThat(commander.getComponent(Dying.class).begun).isTrue();
    }

    /**
     * The bug playtesting found: he was still walking and still firing with an empty bar, because
     * the system that drives him carried its own copy of "is this man out of it" and that copy knew
     * about being stunned and dying and not about being a casualty. Every driver asks {@code Drivers}
     * now, which is what it was written for.
     */
    @Test
    void leavesACommanderWhoIsDownWhereHeFell() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        wound(commander, COMMANDER_HEALTH);
        simulation.step();
        steps(simulation, WHILE_HE_ASKS, WALKING_AND_SHOOTING);

        assertThat(commander.getComponent(Position.class).x).isEqualTo(50f);
        assertThat(commander.getComponent(Position.class).y).isEqualTo(50f);
    }

    @Test
    void takesNoShotFromACommanderWhoIsDown() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        wound(commander, COMMANDER_HEALTH);
        simulation.step();
        steps(simulation, WHILE_HE_ASKS, WALKING_AND_SHOOTING);

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    private static void wound(Entity entity, float amount) {
        entity.getComponent(Damage.class).add(amount, 0f, false);
    }

    private static void kill(Entity entity, float amount) {
        entity.getComponent(Damage.class).add(amount, 0f);
    }
}
