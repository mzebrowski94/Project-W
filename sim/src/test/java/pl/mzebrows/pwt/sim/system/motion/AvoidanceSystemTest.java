package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.avoiding;
import static pl.mzebrows.pwt.sim.AlphaWorlds.inRuinedGround;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;

class AvoidanceSystemTest {

    private static final float LANE = 50f;
    private static final float START = 20f;
    private static final float BLOCKER = 50f;
    private static final float TARGET = 80f;

    /** Far enough short of the blocker that a unit only touching it would still be dead straight. */
    private static final int WELL_BEFORE_CONTACT = 40;

    private static final float A_CLEAR_DEVIATION = 1f;

    /** Where a shot of venom is thrown from, east along the lane, and where a man sets off beside it. */
    private static final float SHOT_X = 20f;

    private static final float SET_OFF_X = 30f;

    /** Where a man is sent, well past anything the shot or a blast leaves. */
    private static final float GOAL_X = 95f;

    /** Where a blast goes off across the lane. */
    private static final float BLAST_X = 60f;

    /** How far either side of the lane the men set off, one unit apart. */
    private static final int WIDEST_OFFSET = 6;

    /** The lane beside a shipped spatter where a man running past is level with its flank. */
    private static final float ALONGSIDE = 5f;

    /** Long enough for a shipped shot of venom to fly its whole reach and burst, or a blast to burn out. */
    private static final int UNTIL_IT_IS_LAID = 90;

    /** Long enough to walk from the start to the goal at a trooper's pace, the long way round. */
    private static final int THE_WALK = 400;

    private static final float CORNER = 5f;

    @Test
    void leansOffALineThatWouldRunIntoSomethingLongBeforeItGetsThere() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);
        simulation.spawnObstacle(BLOCKER, LANE);

        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(offLane(enemy)).isGreaterThan(A_CLEAR_DEVIATION);
    }

    @Test
    void walksDeadStraightWhenNothingIsInTheWay() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);

        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(offLane(enemy)).isLessThan(A_CLEAR_DEVIATION);
    }

    @Test
    void neverAvoidsTheThingItIsHunting() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);

        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(enemy.getComponent(Position.class).x).isGreaterThan(START);
    }

    @Test
    void getsPastABlockerRatherThanGrindingIntoIt() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);
        simulation.spawnObstacle(BLOCKER, LANE);

        steps(simulation, 100);

        assertThat(enemy.getComponent(Position.class).x).isGreaterThan(BLOCKER);
    }

    @Test
    void walksStraightOverItsOwnDinner() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);
        simulation.spawnPickup(PickupKind.MUSHROOM, BLOCKER, LANE);

        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(offLane(enemy)).isLessThan(A_CLEAR_DEVIATION);
    }

    @Test
    void leansOffABarrelAsReadilyAsOffARock() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);
        simulation.spawnBarrel(BLOCKER, LANE);

        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(offLane(enemy)).isGreaterThan(A_CLEAR_DEVIATION);
    }

    @Test
    void neverClipsTheFlankOfGroundItIsRunningAlongside() {
        assertThat(ticksInRuinedGround(venomLane(ALONGSIDE))).isZero();
    }

    @Test
    void walksPastEveryShippedSpatterWithoutSettingFootInIt() {
        SimConfig shipped = SimConfig.defaults();
        float[] blasts = {shipped.grenade().blastRadius(), shipped.barrel().blastRadius()};
        for (int offset = -WIDEST_OFFSET; offset <= WIDEST_OFFSET; offset++) {
            assertThat(ticksInRuinedGround(venomLane(offset)))
                    .as("venom, lane %d", offset)
                    .isZero();
            for (float radius : blasts) {
                assertThat(ticksInRuinedGround(fireLane(offset, radius)))
                        .as("fire of a %.0f blast, lane %d", radius, offset)
                        .isZero();
            }
        }
    }

    /** A shipped shot of venom thrown east along the lane, and a man sent past it {@code offset} off it. */
    private static Lane venomLane(float offset) {
        Simulation simulation = new Simulation(SimConfig.defaults());
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnProjectile(SHOT_X, LANE, 1f, 0f, WeaponKind.VENOM, Side.ALIEN);
        return sent(simulation, offset);
    }

    /** A shipped blast across the lane, and a man sent past the fire it leaves {@code offset} off it. */
    private static Lane fireLane(float offset, float radius) {
        Simulation simulation = new Simulation(SimConfig.defaults());
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnBlast(BLAST_X, LANE, radius);
        return sent(simulation, offset);
    }

    /** The ground is laid before the man sets off: a burst at his feet is no test of going round it. */
    private static Lane sent(Simulation simulation, float offset) {
        steps(simulation, UNTIL_IT_IS_LAID);
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Slick.class).get())
                        .size())
                .as("ground laid before he sets off")
                .isPositive();
        simulation.spawnControlPoint(GOAL_X, LANE + offset);
        return new Lane(simulation, simulation.spawnTrooper(SET_OFF_X, LANE + offset));
    }

    private record Lane(Simulation simulation, Entity man) {}

    /** How many ticks of the walk the man's body overlapped any ruined ground. */
    private static int ticksInRuinedGround(Lane lane) {
        int ticks = 0;
        for (int tick = 0; tick < THE_WALK; tick++) {
            lane.simulation().step();
            if (inRuinedGround(lane.simulation(), lane.man())) {
                ticks++;
            }
        }
        return ticks;
    }

    private static float offLane(Entity entity) {
        return Math.abs(entity.getComponent(Position.class).y - LANE);
    }
}
