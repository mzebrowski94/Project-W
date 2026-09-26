package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CASUALTY_BLEED_OUT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CASUALTY_STABILISE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.HAULED_STABILISE_FACTOR;
import static pl.mzebrows.pwt.sim.AlphaWorlds.REVIVE_INVULNERABLE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TICK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Drag;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;

/**
 * Hauling a man out. The grip itself is barely the mechanic - what makes it a decision is everything
 * it costs while it lasts: you back away, slowly, with no hand for the gun, and you pay for the
 * journey when you finally put him down.
 *
 * <p>The commander stands to the <em>right</em> of the man he is dragging throughout, so "away from
 * the load" is the positive x direction and every assertion about which way he may walk can be read
 * without working out a bearing.
 */
class DragTest {

    private static final float MIDDLE = 50f;

    /** Close enough that the two are touching; the reach test is {@code ActionTest}'s. */
    private static final float BESIDE = 2f;

    private static final CommanderInput STILL = CommanderInput.IDLE;

    /** Straight at the man he is dragging, who is to his left. */
    private static final CommanderInput FORWARDS = new CommanderInput(-1f, 0f, 0f, 0f, false, false);

    /** Directly away from him. */
    private static final CommanderInput BACKWARDS = new CommanderInput(1f, 0f, 0f, 0f, false, false);

    @Test
    void takesHoldOfAManWhoIsDownBesideHim() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        Entity trooper = downedTrooper(simulation);

        simulation.step(STILL.dragging(true));

        assertThat(commander.getComponent(Drag.class).active()).isTrue();
        assertThat(trooper.getComponent(Dragged.class).held()).isTrue();
    }

    @Test
    void willNotTakeHoldOfAManStillOnHisFeet() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        simulation.spawnTrooper(MIDDLE, MIDDLE);

        simulation.step(STILL.dragging(true));

        assertThat(commander.getComponent(Drag.class).active()).isFalse();
    }

    /** One press, one grab: the second press lets go rather than taking hold all over again. */
    @Test
    void letsGoWhenTheKeyIsPressedAgain() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        Entity trooper = downedTrooper(simulation);

        steps(simulation, 5, STILL.dragging(true));
        simulation.step(STILL);
        simulation.step(STILL.dragging(true));

        assertThat(commander.getComponent(Drag.class).active()).isFalse();
        assertThat(trooper.getComponent(Dragged.class).held()).isFalse();
    }

    @Test
    void bringsTheManAlongWithHim() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        Entity trooper = downedTrooper(simulation);

        simulation.step(STILL.dragging(true));
        steps(simulation, 30, BACKWARDS.dragging(true));

        assertThat(trooper.getComponent(Position.class).x).isGreaterThan(MIDDLE + 2f);
    }

    /**
     * He backs out, and cannot press in. Pushing straight at the man he is holding buys him almost
     * nothing - what little it does is the sideways allowance, which lets him arc round rather than
     * bear down, and that is the "slightly left and right" the drag is meant to leave him.
     *
     * <p>Measured against backing away over the same ticks rather than against a fixed direction,
     * because the bearing to the load turns as he moves: an assertion about his x would be an
     * assertion that he never arcs, which is not the rule.
     */
    @Test
    void barelyMovesAtAllWhenHePressesStraightAtTheManHeIsDragging() {
        float forwards = travelWhileDragging(FORWARDS);
        float backwards = travelWhileDragging(BACKWARDS);

        assertThat(forwards).isLessThan(backwards * 0.4f);
    }

    @Test
    void haulsHimSlowerThanTheCommanderWalks() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        downedTrooper(simulation);

        simulation.step(STILL.dragging(true));
        float from = commander.getComponent(Position.class).x;
        steps(simulation, 30, BACKWARDS.dragging(true));

        float travelled = commander.getComponent(Position.class).x - from;
        assertThat(travelled).isPositive().isLessThan(COMMANDER_SPEED * TICK * 30f * 0.8f);
    }

    @Test
    void leavesHimNoHandForTheGun() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        downedTrooper(simulation);

        simulation.step(STILL.dragging(true));
        steps(simulation, 30, new CommanderInput(0f, 0f, 90f, MIDDLE, true, false).dragging(true));

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /** The ground you cross carrying him is free; what you pay is at the other end. */
    @Test
    void stopsHisClockWhileHeIsBeingCarried() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        Entity trooper = downedTrooper(simulation);

        steps(simulation, ticksIn(CASUALTY_BLEED_OUT) + 30, STILL.dragging(true));

        assertThat(trooper.getComponent(Casualty.class).active()).isTrue();
    }

    @Test
    void makesAManWhoHasBeenHauledFarSlowerToPatchUp() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        Entity trooper = downedTrooper(simulation);

        steps(simulation, 5, STILL.dragging(true));
        simulation.step(STILL);
        steps(simulation, ticksIn(CASUALTY_STABILISE) + 2, STILL.acting(true));

        assertThat(trooper.getComponent(Casualty.class).begun).isTrue();

        steps(simulation, ticksIn(CASUALTY_STABILISE * HAULED_STABILISE_FACTOR) + 4, STILL.acting(true));

        assertThat(trooper.getComponent(Casualty.class).begun).isFalse();
    }

    /** A man who has been hauled stays expensive. Putting him down a moment early undoes nothing. */
    @Test
    void remembersThatAManWasHauledAfterHeIsPutDown() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        Entity trooper = downedTrooper(simulation);

        steps(simulation, 5, STILL.dragging(true));
        simulation.step(STILL);
        simulation.step(STILL.dragging(true));

        assertThat(trooper.getComponent(Dragged.class).hauled).isTrue();
        assertThat(trooper.getComponent(Dragged.class).held()).isFalse();
    }

    /** Being carried out of one firefight does not make a man expensive for the rest of the level. */
    @Test
    void forgetsTheJourneyWhenAManGoesDownAgainLater() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        Entity trooper = downedTrooper(simulation);

        steps(simulation, 5, STILL.dragging(true));
        simulation.step(STILL);
        steps(simulation, ticksIn(CASUALTY_STABILISE * HAULED_STABILISE_FACTOR) + 4, STILL.acting(true));
        assertThat(trooper.getComponent(Casualty.class).begun).isFalse();

        steps(simulation, ticksIn(REVIVE_INVULNERABLE) + 2, STILL);
        trooper.getComponent(Damage.class).add(TROOPER_HEALTH, 0, false);
        simulation.step(STILL);

        assertThat(trooper.getComponent(Casualty.class).begun).isTrue();
        assertThat(trooper.getComponent(Dragged.class).hauled).isFalse();
    }

    @Test
    void haulsABarrelAsReadilyAsAMan() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        Entity barrel = simulation.spawnBarrel(MIDDLE, MIDDLE);

        simulation.step(STILL.dragging(true));
        steps(simulation, 30, BACKWARDS.dragging(true));

        assertThat(barrel.getComponent(Position.class).x).isGreaterThan(MIDDLE + 2f);
    }

    /**
     * A barrel is scenery until somebody picks it up, and scenery again the moment he lets go. It has
     * to become a body while it is in his hands or the solver would let him pull it through rock -
     * only movable solids are kept out of terrain - and it has to stop being one afterwards, or a
     * crowd could shuffle it across the level.
     */
    @Test
    void putsABarrelBackToSceneryWhenHeLetsGoOfIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        Entity barrel = simulation.spawnBarrel(MIDDLE, MIDDLE);

        simulation.step(STILL.dragging(true));
        assertThat(barrel.getComponent(Collider.class).movable).isTrue();

        simulation.step(STILL);
        simulation.step(STILL.dragging(true));

        assertThat(barrel.getComponent(Collider.class).movable).isFalse();
    }

    /** How far he gets in thirty ticks of holding one direction, with a man in his hands. */
    private static float travelWhileDragging(CommanderInput direction) {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE);
        downedTrooper(simulation);
        simulation.step(STILL.dragging(true));

        Position at = commander.getComponent(Position.class);
        float fromX = at.x;
        float fromY = at.y;
        steps(simulation, 30, direction.dragging(true));

        float dx = at.x - fromX;
        float dy = at.y - fromY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static Entity downedTrooper(Simulation simulation) {
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        trooper.getComponent(Damage.class).add(TROOPER_HEALTH, 0, false);
        simulation.step(STILL);
        return trooper;
    }
}
