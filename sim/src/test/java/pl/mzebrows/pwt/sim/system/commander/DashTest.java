package pl.mzebrows.pwt.sim.system.commander;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DASH_DISTANCE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DASH_MOST;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DASH_RECHARGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DASH_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DASH_SLOW_FACTOR;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dash;
import pl.mzebrows.pwt.sim.component.Drag;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Puff;
import pl.mzebrows.pwt.sim.component.Shield;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * The commander's dash (G0107): a burst along the way he is moving, over a fixed distance, stopped by whatever would
 * stop him walking and protecting him from nothing; a few held, each coming back on its own clock, one at a time.
 */
class DashTest {

    private static final float START_X = 30f;

    private static final float START_Y = 50f;

    private static final Offset<Float> NEAR = Offset.offset(0.01f);

    /** Long enough for any dash to be over and nothing else to have moved him. */
    private static final int PAST_A_DASH = ticksIn(DASH_SECONDS) + 3;

    /** Standing still, aimed east. */
    private static final CommanderInput STILL = new CommanderInput(0f, 0f, START_X + 50f, START_Y, false, false);

    @Test
    void coversItsDistanceOnOpenGround() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);

        dash(simulation, 1f, 0f);

        assertThat(x(commander)).isCloseTo(START_X + DASH_DISTANCE, NEAR);
    }

    /** Aim and movement stay independent (G0002): the burst goes where he walks, whatever he points at. */
    @Test
    void goesAlongHisMovementRatherThanHisAim() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);

        dash(simulation, 0f, 1f);

        assertThat(x(commander)).isCloseTo(START_X, NEAR);
        assertThat(y(commander)).isCloseTo(START_Y + DASH_DISTANCE, NEAR);
    }

    @Test
    void goesTheWayHeLastMovedWithTheStickAtRest() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        steps(simulation, 3, new CommanderInput(0f, -1f, START_X + 50f, START_Y, false, false));
        simulation.step(STILL);
        float fromY = y(commander);

        dash(simulation, 0f, 0f);

        assertThat(y(commander)).isCloseTo(fromY - DASH_DISTANCE, NEAR);
    }

    @Test
    void goesAlongHisAimIfHeHasNeverMoved() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        CommanderInput aimingNorth = new CommanderInput(0f, 0f, START_X, START_Y + 50f, false, false);
        simulation.step(aimingNorth);

        simulation.step(aimingNorth.dashing(true));
        steps(simulation, PAST_A_DASH, aimingNorth);

        assertThat(x(commander)).isCloseTo(START_X, NEAR);
        assertThat(y(commander)).isCloseTo(START_Y + DASH_DISTANCE, NEAR);
    }

    /** A refused press spends nothing and shows nothing. */
    @Test
    void isRefusedWhileHeIsHeld() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        commander.getComponent(Stunned.class).remainingTicks = PAST_A_DASH * 2;

        simulation.step(moving(1f, 0f).dashing(true));

        assertThat(simulation.dashes(commander)).isEqualTo(DASH_MOST);
        assertThat(count(simulation, Puff.class)).isZero();
    }

    @Test
    void spendsNothingAndGoesNowhereOnAPressWithNoneLeft() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        for (int i = 0; i < DASH_MOST; i++) {
            dash(simulation, 0f, 1f);
        }
        float fromY = y(commander);

        simulation.step(STILL.dashing(true));
        steps(simulation, PAST_A_DASH, STILL);

        assertThat(simulation.dashes(commander)).isZero();
        assertThat(y(commander)).isEqualTo(fromY);
    }

    /** All a press with none left does is the puff, at his feet. */
    @Test
    void leavesOnlyAPuffOnAPressWithNoneLeft() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        for (int i = 0; i < DASH_MOST; i++) {
            dash(simulation, 0f, 1f);
        }
        steps(simulation, PAST_A_DASH, STILL);
        int before = count(simulation, Puff.class);

        simulation.step(STILL.dashing(true));

        assertThat(count(simulation, Puff.class)).isEqualTo(before + 1);
        assertThat(commander.getComponent(Dash.class).running()).isFalse();
    }

    @Test
    void leavesAPuffWhereTheDashBegan() {
        Simulation simulation = simulation();
        simulation.spawnCommander(START_X, START_Y);

        simulation.step(moving(1f, 0f).dashing(true));

        Entity puff =
                simulation.engine().getEntitiesFor(Family.all(Puff.class).get()).first();
        assertThat(puff.getComponent(Position.class).x).isEqualTo(START_X);
    }

    @Test
    void givesThemBackOneAtATime() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        int recharge = ticksIn(DASH_RECHARGE);
        dash(simulation, 1f, 0f);
        simulation.step(moving(-1f, 0f).dashing(true));

        steps(simulation, recharge - (PAST_A_DASH + 2), STILL);
        int beforeTheFirst = simulation.dashes(commander);
        simulation.step(STILL);
        int afterTheFirst = simulation.dashes(commander);
        steps(simulation, recharge - 1, STILL);
        int beforeTheSecond = simulation.dashes(commander);
        simulation.step(STILL);

        assertThat(beforeTheFirst).isZero();
        assertThat(afterTheFirst).isEqualTo(1);
        assertThat(beforeTheSecond).isEqualTo(1);
        assertThat(simulation.dashes(commander)).isEqualTo(DASH_MOST);
    }

    /** A second press while a burst still runs is not a second dash: nothing is spent and the burst is not turned. */
    @Test
    void ignoresAPressWhileABurstStillRuns() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        simulation.step(moving(1f, 0f).dashing(true));
        simulation.step(moving(0f, 1f));

        simulation.step(moving(0f, 1f).dashing(true));
        steps(simulation, PAST_A_DASH, STILL);

        assertThat(simulation.dashes(commander)).isEqualTo(DASH_MOST - 1);
        assertThat(y(commander)).isCloseTo(START_Y, NEAR);
    }

    @Test
    void reachesHalfAsFarAcrossAThicket() {
        Simulation simulation = new Simulation(
                config(), ground().rect(0, 0, 12, 20, Terrain.THICKET).build());
        Entity commander = simulation.spawnCommander(START_X, START_Y);

        dash(simulation, 1f, 0f);

        assertThat(x(commander)).isCloseTo(START_X + DASH_DISTANCE * DASH_SLOW_FACTOR, NEAR);
    }

    /** Glue underfoot is a slow done to him; a dash through it goes at half its speed, whatever the glue's own factor. */
    @Test
    void reachesHalfAsFarThroughGlue() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        commander.getComponent(Slowed.class).apply(0.2f, PAST_A_DASH * 2);

        dash(simulation, 1f, 0f);

        assertThat(x(commander)).isCloseTo(START_X + DASH_DISTANCE * DASH_SLOW_FACTOR, NEAR);
    }

    /** A wound slows his walk and not his dash. */
    @Test
    void goesItsWholeDistanceWounded() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        commander.getComponent(Invulnerable.class).remainingTicks = 0;
        commander.getComponent(Damage.class).land(0, Harm.WOUND);
        simulation.step(STILL);
        assertThat(commander.getComponent(Vitality.class).paceFactor()).isLessThan(1f);
        float fromX = x(commander);

        dash(simulation, 1f, 0f);

        assertThat(x(commander)).isCloseTo(fromX + DASH_DISTANCE, NEAR);
    }

    @Test
    void isStoppedByAWall() {
        int wallCol = 7;
        Simulation simulation = new Simulation(
                config(), ground().rect(wallCol, 0, 1, 20, Terrain.ROCK).build());
        Entity commander = simulation.spawnCommander(START_X, START_Y);

        dash(simulation, 1f, 0f);

        assertThat(x(commander)).isLessThanOrEqualTo(wallCol * TILE);
    }

    /** No i-frames, no shield (pillar 2): a blow that lands mid-dash lands. */
    @Test
    void neverProtectsHim() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        commander.getComponent(Invulnerable.class).remainingTicks = 0;
        simulation.step(moving(1f, 0f).dashing(true));

        assertThat(commander.getComponent(Invulnerable.class).active()).isFalse();
        assertThat(commander.getComponent(Shield.class).up).isFalse();
        commander.getComponent(Damage.class).land(0, Harm.DOWN);
        simulation.step(STILL);

        assertThat(Life.down(commander)).isTrue();
    }

    @Test
    void losesTheWorkOfAStabiliseInHand() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        Entity trooper = simulation.spawnTrooper(START_X + 2f, START_Y);
        trooper.getComponent(Damage.class).land(0, Harm.DOWN);
        steps(simulation, 10, STILL.acting(true));
        assertThat(commander.getComponent(Action.class).progress).isPositive();

        simulation.step(STILL.acting(true).dashing(true));

        assertThat(trooper.getComponent(Vitality.class).stabilised).isZero();
    }

    /** He keeps hold of what he hauls, and it follows him through the burst. */
    @Test
    void keepsHoldOfWhatHeHauls() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X + 2f, START_Y);
        Entity trooper = simulation.spawnTrooper(START_X, START_Y);
        trooper.getComponent(Damage.class).land(0, Harm.DOWN);
        simulation.step(STILL);
        simulation.step(STILL.dragging(true));

        dash(simulation, 1f, 0f);

        assertThat(commander.getComponent(Drag.class).active()).isTrue();
        assertThat(x(trooper)).isGreaterThan(START_X + DASH_DISTANCE / 2f);
    }

    /** A press with the stick held this way, then the stick let go until the burst is over. */
    private static void dash(Simulation simulation, float moveX, float moveY) {
        simulation.step(moving(moveX, moveY).dashing(true));
        steps(simulation, PAST_A_DASH, STILL);
    }

    private static CommanderInput moving(float moveX, float moveY) {
        return new CommanderInput(moveX, moveY, START_X + 50f, START_Y, false, false);
    }

    private static float x(Entity entity) {
        return entity.getComponent(Position.class).x;
    }

    private static float y(Entity entity) {
        return entity.getComponent(Position.class).y;
    }
}
