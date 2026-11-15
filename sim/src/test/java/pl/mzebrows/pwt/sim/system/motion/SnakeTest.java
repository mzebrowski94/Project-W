package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TICK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slither;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.SlitherDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The desert snake (G0087): it never stops, goes round in wide arcs, turns hard only where its way is shut, passes
 * through whoever stands in its way and wounds him with its head, and comes apart a part at a time from the tail.
 */
class SnakeTest {

    private static final float SPEED = 10f;
    private static final float TURN_DEGREES = 40f;
    private static final float TIGHT_DEGREES = 220f;
    private static final float LOOK_AHEAD = 8f;
    private static final int PARTS = 4;
    private static final int HEALTH = 400;

    private static final float MIDDLE = ARENA * 0.5f;

    /** Well inside its roam, so a test of its arcs is not a test of it going home. */
    private static final float ROAM = ARENA;

    private static final float HUNT_RANGE = 40f;
    private static final float HUNT_DEGREES = 120f;
    private static final float CHASE_SECONDS = 4f;

    @Test
    void neverStopsInTheOpen() {
        Simulation simulation = new Simulation(snakes());
        Entity snake = simulation.spawnUnit(UnitKind.SNAKE, MIDDLE, MIDDLE);
        Position at = snake.getComponent(Position.class);

        for (int tick = 0; tick < ticksIn(3f); tick++) {
            float fromX = at.x;
            float fromY = at.y;
            simulation.step();
            float moved = (float) StrictMath.hypot(at.x - fromX, at.y - fromY);
            assertThat(moved).isGreaterThan(SPEED * TICK * 0.9f);
        }
    }

    /** In the open it bends no faster than its turn: a wide arc, never a corner. */
    @Test
    void bendsNoFasterThanItsTurnInTheOpen() {
        Simulation simulation = new Simulation(snakes());
        Entity snake = facingEast(simulation, MIDDLE, MIDDLE);
        Slither slither = snake.getComponent(Slither.class);
        float most = (float) StrictMath.toRadians(TURN_DEGREES) * TICK * 1.01f;
        simulation.step();

        for (int tick = 0; tick < ticksIn(1f); tick++) {
            float beforeX = slither.headingX;
            float beforeY = slither.headingY;
            simulation.step();
            float turned =
                    (float) StrictMath.acos(Math.min(1f, beforeX * slither.headingX + beforeY * slither.headingY));
            assertThat(turned).isLessThanOrEqualTo(most);
        }
    }

    /** Pointed at the edge of the field, it turns away before it reaches it and goes on. */
    @Test
    void turnsHardAwayFromWhereItsWayIsShut() {
        Simulation simulation = new Simulation(snakes());
        Entity snake = facingEast(simulation, ARENA - LOOK_AHEAD * 1.5f, MIDDLE);
        Position at = snake.getComponent(Position.class);

        steps(simulation, ticksIn(2f));

        assertThat(at.x).isLessThan(ARENA - 2f);
        assertThat(snake.getComponent(Slither.class).headingX).isNegative();
    }

    /** Rock in its way is shut way too: it never runs into a wall and stays there. */
    @Test
    void goesOnRoundRockRatherThanStoppingAtIt() {
        TileMap walled = AlphaWorlds.ground()
                .rect(12, 0, 1, AlphaWorlds.ARENA_TILES, Terrain.ROCK)
                .build();
        Simulation simulation = new Simulation(snakes(), walled);
        Entity snake = facingEast(simulation, 45f, MIDDLE);
        Position at = snake.getComponent(Position.class);
        steps(simulation, ticksIn(2f));
        float fromX = at.x;
        float fromY = at.y;

        steps(simulation, ticksIn(1f));

        assertThat(at.x).isLessThan(60f);
        assertThat(StrictMath.hypot(at.x - fromX, at.y - fromY)).isGreaterThan(SPEED * 0.6f);
    }

    /** A man in its way is gone through, not pushed: it holds its line, and its head wounds him once. */
    @Test
    void passesThroughAManInItsWayAndWoundsHimOnce() {
        Simulation simulation = new Simulation(straight());
        Entity trooper = simulation.spawnTrooper(MIDDLE + 6f, MIDDLE);
        Position his = trooper.getComponent(Position.class);
        float hisY = his.y;
        Entity snake = facingEast(simulation, MIDDLE, MIDDLE);
        Position at = snake.getComponent(Position.class);

        steps(simulation, ticksIn(1.5f));

        assertThat(at.x).isGreaterThan(his.x + 3f);
        assertThat(at.y).isCloseTo(MIDDLE, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(Math.abs(his.y - hisY)).isLessThan(0.5f);
        assertThat(trooper.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    /** Two men in its way one behind the other: each is passed through, and each takes his wound. */
    @Test
    void woundsEveryManItPassesThrough() {
        Simulation simulation = new Simulation(straight());
        Entity first = simulation.spawnTrooper(MIDDLE + 6f, MIDDLE);
        Entity second = simulation.spawnTrooper(MIDDLE + 9.5f, MIDDLE);
        facingEast(simulation, MIDDLE, MIDDLE);

        steps(simulation, ticksIn(2f));

        assertThat(first.getComponent(Vitality.class).wounds).isEqualTo(1);
        assertThat(second.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    /** The edge of its biome is shut way: it goes across its own and turns back at the edge. */
    @Test
    void staysInItsOwnBiome() {
        Simulation simulation = new Simulation(snakes(), AlphaWorlds.twoBiomes());
        Entity snake = facingEast(simulation, MIDDLE - LOOK_AHEAD * 1.5f, MIDDLE);
        Position at = snake.getComponent(Position.class);

        for (int tick = 0; tick < ticksIn(4f); tick++) {
            simulation.step();
            assertThat(at.x).isLessThan(MIDDLE);
        }
    }

    @Test
    void leavesAManItDoesNotReachAlone() {
        Simulation simulation = new Simulation(straight());
        Entity trooper = simulation.spawnTrooper(MIDDLE + 6f, MIDDLE + 10f);
        facingEast(simulation, MIDDLE, MIDDLE);

        steps(simulation, ticksIn(1.5f));

        assertThat(trooper.getComponent(Vitality.class).wounds).isZero();
    }

    /** A shot hits it as it hits anything, passing through bodies or not. */
    @Test
    void isHitByAShot() {
        Simulation simulation = new Simulation(straight());
        simulation.spawnCommander(10f, MIDDLE);
        Entity snake = facingEast(simulation, 30f, MIDDLE);
        int whole = snake.getComponent(Health.class).current;

        steps(simulation, ticksIn(0.5f), new CommanderInput(0f, 0f, 90f, MIDDLE, true, false));

        assertThat(snake.getComponent(Health.class).current).isLessThan(whole);
    }

    /** Nothing slows it: wading mud it keeps its pace (G0087). */
    @Test
    void keepsItsPaceInMud() {
        TileMap mud = AlphaWorlds.ground()
                .rect(0, 0, AlphaWorlds.ARENA_TILES, AlphaWorlds.ARENA_TILES, Terrain.MUD)
                .build();
        SimConfig config = straight();
        Simulation simulation = new Simulation(
                config.withGround(
                        config.ground().map(Terrain.MUD, ground -> new pl.mzebrows.pwt.sim.def.GroundDef(0.4f, 0.4f))),
                mud);
        Entity snake = facingEast(simulation, 20f, MIDDLE);
        Position at = snake.getComponent(Position.class);
        simulation.step();
        float fromX = at.x;

        steps(simulation, ticksIn(1f));

        assertThat(at.x - fromX).isGreaterThan(SPEED * 0.9f);
    }

    /** Now and then it picks a man off its line and goes through him once, then goes back to its own way. */
    @Test
    void picksAManOffItsLineAndGoesThroughHim() {
        Simulation simulation = new Simulation(hunting());
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE + 15f);
        facingEast(simulation, 20f, MIDDLE);

        steps(simulation, ticksIn(4f));

        assertThat(trooper.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    @Test
    void picksNobodyOutsideItsRange() {
        Simulation simulation = new Simulation(hunting());
        Entity trooper = simulation.spawnTrooper(ARENA - 5f, ARENA - 5f);
        facingEast(simulation, 10f, 10f);

        steps(simulation, ticksIn(1f));

        assertThat(trooper.getComponent(Vitality.class).wounds).isZero();
    }

    /** Each part's worth of health lost takes the last part off; the head is the last to go. */
    @Test
    void losesAPartFromTheTailForEveryPartsWorthOfHealthLost() {
        Simulation simulation = new Simulation(snakes());
        Entity snake = simulation.spawnUnit(UnitKind.SNAKE, MIDDLE, MIDDLE);
        Slither slither = snake.getComponent(Slither.class);
        int part = snake.getComponent(Health.class).max / PARTS;
        simulation.step();
        assertThat(slither.partsLeft).isEqualTo(PARTS);

        hit(simulation, snake, part);
        assertThat(slither.partsLeft).isEqualTo(PARTS - 1);

        hit(simulation, snake, part * 2);
        assertThat(slither.partsLeft).isEqualTo(1);
    }

    /** Its parts trail its head, each its spacing behind the one in front. */
    @Test
    void trailsItsPartsBehindItsHead() {
        Simulation simulation = new Simulation(straight());
        Entity snake = facingEast(simulation, 20f, MIDDLE);
        Slither slither = snake.getComponent(Slither.class);
        Position head = snake.getComponent(Position.class);

        steps(simulation, ticksIn(1.5f));

        assertThat(slither.partX(1)).isLessThan(head.x);
        assertThat(slither.partX(2)).isLessThan(slither.partX(1));
        assertThat(slither.partX(3)).isLessThan(slither.partX(2));
    }

    private static Entity facingEast(Simulation simulation, float x, float y) {
        Entity snake = simulation.spawnUnit(UnitKind.SNAKE, x, y);
        snake.getComponent(pl.mzebrows.pwt.sim.component.Facing.class).face(1f, 0f);
        return snake;
    }

    private static void hit(Simulation simulation, Entity snake, int amount) {
        snake.getComponent(Damage.class).land(amount, Harm.WOUND);
        snake.getComponent(pl.mzebrows.pwt.sim.component.Invulnerable.class).remainingTicks = 0;
        // Landed this tick; its parts are shared out from its health on the next.
        steps(simulation, 2);
    }

    private static SimConfig snakes() {
        return slithering(TURN_DEGREES);
    }

    /** One that never bends in the open, so where it goes is a straight line a test can stand a man on. */
    private static SimConfig straight() {
        return slithering(0f);
    }

    /** One that picks a man straight away and goes on picking, for the tests of its charge. */
    private static SimConfig hunting() {
        return slithering(0f, TICK);
    }

    private static SimConfig slithering(float turnDegrees) {
        return slithering(turnDegrees, 0f);
    }

    private static SimConfig slithering(float turnDegrees, float huntSeconds) {
        return config().withUnit(
                        UnitKind.SNAKE,
                        snake -> snake.withSpeed(SPEED)
                                .withHealth(HEALTH)
                                .withAttackArcDegrees(360f)
                                .withAttackCooldownSeconds(0.1f)
                                .withVictimInvulnerableSeconds(1f)
                                .withLockSeconds(0f)
                                .withPerception(new PerceptionDef(false, true, 0f, 0f, 0f, 360f, false, 0f))
                                .withSlither(new SlitherDef(
                                        PARTS,
                                        0.8f,
                                        0.9f,
                                        turnDegrees,
                                        TIGHT_DEGREES,
                                        LOOK_AHEAD,
                                        ROAM,
                                        1f,
                                        2f,
                                        Harm.WOUND,
                                        true,
                                        huntSeconds,
                                        huntSeconds,
                                        HUNT_RANGE,
                                        HUNT_DEGREES,
                                        CHASE_SECONDS)));
    }
}
