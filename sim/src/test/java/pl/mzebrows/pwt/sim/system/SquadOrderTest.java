package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.FORWARD_FACTOR;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ORDER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * What the commander can tell his squad, and who hears him (G0017).
 *
 * <p>The two rules worth defending here are the ones that make commanding a decision rather than a
 * menu. <strong>Only what is near him hears an order</strong>, so where he stands matters; and
 * <strong>an order never interrupts a fight</strong>, so a squad under fire cannot be talked out of
 * defending itself - it does as it is told when the shooting stops, which needs no queue because
 * nothing consumed the order in the first place.
 */
class SquadOrderTest {

    private static final float MIDDLE = 50f;

    /** Far enough away that he is out of earshot but still on the map. */
    private static final float OUT_OF_EARSHOT = MIDDLE + ORDER_RADIUS + 10f;

    private static final Offset<Float> TOLERANCE = Offset.offset(1f);

    @Test
    void reachesATrooperStandingNextToHim() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);

        simulation.step(order(Order.DEFENCE));

        assertThat(orderOf(trooper)).isEqualTo(Order.DEFENCE);
    }

    /** The rule the radius exists for: a stray trooper has to be gone and fetched. */
    @Test
    void reachesNobodyOutOfEarshot() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(OUT_OF_EARSHOT, MIDDLE);

        simulation.step(order(Order.DEFENCE));

        assertThat(orderOf(trooper)).isNull();
    }

    /** 3.c. Stationary, and still watching - the sweep goes on without it. */
    @Test
    void holdsATrooperStillWhileItGoesOnLookingAround() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(90f, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        simulation.step(order(Order.DEFENCE));

        float wasX = at(trooper).x;
        float firstLook = trooper.getComponent(Appearance.class).facingY;
        steps(simulation, 120);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.HOLD);
        assertThat(at(trooper).x).isCloseTo(wasX, TOLERANCE);
        assertThat(trooper.getComponent(Appearance.class).facingY).isNotEqualTo(firstLook);
    }

    /** 3.a. It comes with him, and stops crowding him once it is close. */
    @Test
    void bringsATrooperAlongBehindHim() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 15f, MIDDLE);
        simulation.step(order(Order.FOLLOW_ME));

        commander.getComponent(Position.class).set(20f, MIDDLE);
        steps(simulation, 60);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FOLLOW);
        assertThat(at(trooper).x).isLessThan(MIDDLE + 15f);
    }

    /** 3.b. Faster, for a short distance, and then back to the ordinary pace. */
    @Test
    void pushesOnFasterForAShortDistanceAndThenSettlesBack() {
        Simulation simulation = simulation();
        // The long way across the arena on purpose: the burst has to be spent and the walk still
        // going when the second sample is taken, or what is measured is a trooper that has arrived
        // and settled onto the point rather than one back at its ordinary pace.
        simulation.spawnCommander(10f, MIDDLE);
        simulation.spawnControlPoint(95f, MIDDLE);
        Entity trooper = simulation.spawnTrooper(14f, MIDDLE);
        simulation.step(order(Order.FORWARD));
        steps(simulation, 5);

        float pushing = pace(trooper);
        steps(simulation, 120);
        float settled = pace(trooper);

        assertThat(pushing).isCloseTo(TROOPER_SPEED * FORWARD_FACTOR, TOLERANCE);
        assertThat(settled).isCloseTo(TROOPER_SPEED, TOLERANCE);
    }

    /** 3.d. One key puts everything back, so the player is never in a mode they have forgotten. */
    @Test
    void putsATrooperBackToItsOwnJudgement() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(90f, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        simulation.step(order(Order.DEFENCE));
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.HOLD);

        simulation.step(order(Order.CANCEL));
        steps(simulation, 3);

        assertThat(orderOf(trooper)).isNull();
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.ADVANCE);
    }

    /**
     * 3.e, and the reason the order is a standing field rather than a message. A trooper in a fight
     * keeps fighting; nothing consumed what it was told, so it obeys the moment it is free.
     */
    @Test
    void neverInterruptsAFightAndObeysAsSoonAsItIsOver() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + 18f, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);
        simulation.step();
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);

        simulation.step(order(Order.DEFENCE));

        assertThat(orderOf(trooper)).isEqualTo(Order.DEFENCE);
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);

        enemy.getComponent(pl.mzebrows.pwt.sim.component.Damage.class)
                .add(pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH, 0f);
        steps(simulation, 3);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.HOLD);
    }

    /** 3.f. Pillar 5: he has to be able to see who heard him, or he is guessing at his own range. */
    @Test
    void showsWhichTroopersHeardHim() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity heard = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        Entity missedIt = simulation.spawnTrooper(OUT_OF_EARSHOT, MIDDLE);

        simulation.step(order(Order.DEFENCE));

        assertThat(heard.getComponent(Appearance.class).ordered).isTrue();
        assertThat(missedIt.getComponent(Appearance.class).ordered).isFalse();
    }

    /** An order given once stands; it is not re-issued to whatever has since wandered into range. */
    @Test
    void deliversAnOrderOnceRatherThanForAsLongAsItIsHeld() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity latecomer = simulation.spawnTrooper(OUT_OF_EARSHOT, MIDDLE);

        simulation.step(order(Order.DEFENCE));
        latecomer.getComponent(Position.class).set(MIDDLE + 4f, MIDDLE);
        steps(simulation, 3);

        assertThat(orderOf(latecomer)).isNull();
    }

    /**
     * Following him is not standing round him staring. Each man takes a bearing of his own once he
     * stops, so a squad at rest is watching outwards rather than watching the commander.
     */
    @Test
    void spreadsTheSquadsAttentionInsteadOfAllStaringAtHim() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity first = simulation.spawnTrooper(MIDDLE + 3f, MIDDLE);
        Entity second = simulation.spawnTrooper(MIDDLE - 3f, MIDDLE);
        Entity third = simulation.spawnTrooper(MIDDLE, MIDDLE + 3f);

        simulation.step(order(Order.FOLLOW_ME));
        steps(simulation, 10);

        assertThat(facing(first)).isNotEqualTo(facing(second));
        assertThat(facing(second)).isNotEqualTo(facing(third));
    }

    private static float facing(Entity trooper) {
        return trooper.getComponent(Appearance.class).facingX;
    }

    /**
     * His going down releases the squad, which is the same rule the other side already lives by:
     * nothing stands to attention over a corpse.
     */
    @Test
    void releasesTheSquadWhenTheCommanderGoesDown() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(90f, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        simulation.step(order(Order.FOLLOW_ME));
        assertThat(orderOf(trooper)).isEqualTo(Order.FOLLOW_ME);

        commander.getComponent(Damage.class).add(COMMANDER_HEALTH, 0f);
        steps(simulation, 3);

        assertThat(orderOf(trooper)).isNull();
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.ADVANCE);
    }

    /** The ring goes out with the order, so the picture and the behaviour agree again. */
    @Test
    void takesTheOrderRingOffWhenHeGoesDown() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        simulation.step(order(Order.DEFENCE));
        assertThat(trooper.getComponent(Appearance.class).ordered).isTrue();

        commander.getComponent(Damage.class).add(COMMANDER_HEALTH, 0f);
        steps(simulation, 3);

        assertThat(trooper.getComponent(Appearance.class).ordered).isFalse();
    }

    /**
     * And they stay released. An order is a live instruction from a living commander; resuming a
     * formation he asked for before he died would be the game remembering something the player has
     * stopped meaning.
     */
    @Test
    void doesNotResumeAnOldOrderWhenHeComesBack() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(90f, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        simulation.step(order(Order.FOLLOW_ME));

        commander.getComponent(Damage.class).add(COMMANDER_HEALTH, 0f);
        steps(simulation, ticksIn(DEATH_THROES + RESPAWN_DELAY) + 30);

        assertThat(countLiving(simulation, Commander.class)).isEqualTo(1);
        assertThat(orderOf(trooper)).isNull();
    }

    /**
     * Forward is what sends a squad on, so every reason not to advance has to go with it. Having to
     * cancel first was one press too many for the order that sets a level's pace.
     */
    @Test
    void dropsFollowMeWhenHeSaysForward() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(90f, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        simulation.step(order(Order.FOLLOW_ME));
        assertThat(orderOf(trooper)).isEqualTo(Order.FOLLOW_ME);

        simulation.step(order(Order.FORWARD));

        assertThat(orderOf(trooper)).isNull();
        assertThat(trooper.getComponent(Trooper.class).pushingForward()).isTrue();
    }

    @Test
    void dropsDefenceWhenHeSaysForward() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(90f, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        simulation.step(order(Order.DEFENCE));

        simulation.step(order(Order.FORWARD));

        assertThat(orderOf(trooper)).isNull();
    }

    /** The burst of pace goes with them, so one death means one rule rather than a partial release. */
    @Test
    void dropsThePushOnAsWellWhenHeGoesDown() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(90f, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        simulation.step(order(Order.FORWARD));
        assertThat(trooper.getComponent(Trooper.class).pushingForward()).isTrue();

        commander.getComponent(Damage.class).add(COMMANDER_HEALTH, 0f);
        steps(simulation, 3);

        assertThat(trooper.getComponent(Trooper.class).pushingForward()).isFalse();
    }

    private static CommanderInput order(Order given) {
        return CommanderInput.IDLE.ordering(given);
    }

    private static float pace(Entity trooper) {
        Velocity velocity = trooper.getComponent(Velocity.class);
        return (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
    }

    private static Order orderOf(Entity trooper) {
        return trooper.getComponent(Trooper.class).order;
    }

    private static Trooper.Mode mode(Entity trooper) {
        return trooper.getComponent(Trooper.class).mode;
    }

    private static Position at(Entity trooper) {
        return trooper.getComponent(Position.class);
    }
}
