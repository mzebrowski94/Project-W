package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GRENADE_COOLDOWN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GRENADE_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GRENADE_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Thrown;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * A grenade is thrown along the aim <em>direction</em> for a fixed distance, bounces off whatever it
 * runs into, and blasts where the distance runs out.
 *
 * <p>The direction test is the one that matters most. Throwing to the aim <em>point</em> would be the
 * obvious implementation and would quietly break the standing rule that aim is a direction and never a
 * distance - a player who pulled the view back with Tab would be able to throw further, and a
 * rendering setting would have become extra range.
 */
class GrenadeTest {

    private static final float THROW_X = 30f;

    /** Mid-tile, so a bounce off rock is not read on a tile boundary. */
    private static final float THROW_Y = 52f;

    /** Rock fills this column of tiles, so the wall's near face is at x = 40. */
    private static final int ROCK_COL = 8;

    private static final float WALL_FACE = 40f;

    private static final Offset<Float> TOLERANCE = Offset.offset(0.05f);

    /** A whole flight, plus a tick for the blast to be left behind. */
    private static final int WHOLE_FLIGHT = ticksIn(GRENADE_RANGE / GRENADE_SPEED) + 1;

    /** Past the wall, and well short of the flight running out. */
    private static final int PAST_THE_WALL = 12;

    /** Long enough after a bounce that a grenade shivering against the wall would be obvious. */
    private static final int AFTERWARDS = 4;

    /** Just wide enough to kill the one enemy standing in it, and leave a body to throw past. */
    private static final float CORPSE_MAKER = 2f;

    /** Enough ticks for that blast to land and the body to start its throes. */
    private static final int SETTLE = 3;

    /** Close enough to the far edge that a throw reaches it well inside one flight. */
    private static final float NEAR_THE_EDGE = ARENA - 5f;

    /** Touching him, so the first tick of the throw is a tick he spends being killed. */
    private static final float ON_TOP_OF_HIM = 1.9f;

    @Test
    void travelsItsFullRangeWhenNothingIsInTheWay() {
        Simulation simulation = simulation();
        simulation.spawnGrenade(THROW_X, THROW_Y, 1f, 0f);

        steps(simulation, WHOLE_FLIGHT);

        assertThat(blastPosition(simulation).x - THROW_X).isCloseTo(GRENADE_RANGE, TOLERANCE);
    }

    @Test
    void throwsAlongTheAimDirectionRatherThanAtTheAimPoint() {
        assertThat(thrownAt(THROW_X + 1f)).isCloseTo(thrownAt(THROW_X + 60f), TOLERANCE);
    }

    @Test
    void bouncesOffAWallInsteadOfFlyingThroughIt() {
        Simulation simulation =
                on(ground().rect(ROCK_COL, 0, 1, ARENA_TILES, Terrain.ROCK).build());
        Entity grenade = simulation.spawnGrenade(THROW_X, THROW_Y, 1f, 0f);

        steps(simulation, PAST_THE_WALL);

        assertThat(grenade.getComponent(Velocity.class).x).isNegative();
        assertThat(grenade.getComponent(Position.class).x).isLessThan(WALL_FACE);
    }

    /**
     * Found by playing it. Coming off units is fine in an open field and unreadable in a crowd: with a
     * dozen bodies in the way, where a throw ends up stops being predictable and the wall bank is lost
     * in the noise.
     */
    @Test
    void passesThroughAnEnemyInsteadOfComingOffIt() {
        Simulation simulation = simulation();
        simulation.spawnEnemy(WALL_FACE, THROW_Y);
        Entity grenade = simulation.spawnGrenade(THROW_X, THROW_Y, 1f, 0f);

        steps(simulation, PAST_THE_WALL);

        assertThat(grenade.getComponent(Velocity.class).x).isPositive();
        assertThat(grenade.getComponent(Position.class).x).isGreaterThan(WALL_FACE);
        assertThat(count(simulation, Blast.class)).isZero();
    }

    @Test
    void comesOffAnObstacle() {
        Simulation simulation = simulation();
        simulation.spawnObstacle(WALL_FACE, THROW_Y);
        Entity grenade = simulation.spawnGrenade(THROW_X, THROW_Y, 1f, 0f);

        steps(simulation, PAST_THE_WALL);

        assertThat(grenade.getComponent(Velocity.class).x).isNegative();
    }

    /**
     * A body is scenery everywhere else - solid, immovable, and it stops bullets - but it is scenery
     * that appears and vanishes on a one-second clock, and a surface like that is the unpredictability
     * the rule above exists to remove.
     */
    @Test
    void passesThroughABodyInItsThroes() {
        Simulation simulation = simulation();
        simulation.spawnEnemy(WALL_FACE, THROW_Y);
        simulation.spawnBlast(WALL_FACE, THROW_Y, CORPSE_MAKER);
        steps(simulation, SETTLE);

        Entity grenade = simulation.spawnGrenade(THROW_X, THROW_Y, 1f, 0f);
        steps(simulation, PAST_THE_WALL);

        assertThat(grenade.getComponent(Velocity.class).x).isPositive();
    }

    @Test
    void spendsTheSameFlightWhetherItBouncesOrNot() {
        assertThat(flightOf(on(
                        ground().rect(ROCK_COL, 0, 1, ARENA_TILES, Terrain.ROCK).build())))
                .isEqualTo(flightOf(simulation()));
    }

    @Test
    void willNotThrowAgainUntilItsCooldownHasRun() {
        Simulation simulation = simulation();
        simulation.spawnCommander(THROW_X, THROW_Y);

        steps(simulation, 5, throwing(THROW_X + 10f));

        assertThat(count(simulation, Thrown.class)).isOne();
    }

    @Test
    void throwsAgainOnceItHas() {
        Simulation simulation = simulation();
        simulation.spawnCommander(THROW_X, THROW_Y);

        steps(simulation, ticksIn(GRENADE_COOLDOWN) + 2, throwing(THROW_X + 10f));

        assertThat(count(simulation, Thrown.class)).isEqualTo(2);
    }

    /**
     * A grenade is non-solid, so nothing separates it from what it hit and the same contact is
     * reported again next tick. Without the guard that it only comes off a surface it is still heading
     * into, it turns straight back into the wall and sits there shivering.
     */
    @Test
    void keepsGoingAwayFromAWallInsteadOfShiveringAgainstIt() {
        Simulation simulation =
                on(ground().rect(ROCK_COL, 0, 1, ARENA_TILES, Terrain.ROCK).build());
        Entity grenade = simulation.spawnGrenade(THROW_X, THROW_Y, 1f, 0f);
        steps(simulation, PAST_THE_WALL);

        float justAfter = grenade.getComponent(Position.class).x;
        steps(simulation, AFTERWARDS);

        assertThat(grenade.getComponent(Position.class).x).isLessThan(justAfter - AFTERWARDS + 1f);
    }

    /** The solver never keeps a grenade in the arena, because it is not solid. This does. */
    @Test
    void comesOffTheEdgeOfTheArena() {
        Simulation simulation = simulation();
        Entity grenade = simulation.spawnGrenade(NEAR_THE_EDGE, THROW_Y, 1f, 0f);

        steps(simulation, 8);

        assertThat(grenade.getComponent(Velocity.class).x).isNegative();
    }

    @Test
    void throwsNothingWhileHeIsDown() {
        Simulation simulation = simulation();
        simulation.spawnCommander(THROW_X, THROW_Y);
        simulation.spawnEnemy(THROW_X + ON_TOP_OF_HIM, THROW_Y);

        steps(simulation, 10, throwing(THROW_X + 10f));

        assertThat(count(simulation, Thrown.class)).isZero();
    }

    /** The pointer sitting on top of him gives no bearing, and a normalised nothing is a NaN. */
    @Test
    void throwsNothingWhenThereIsNoDirectionToThrowIn() {
        Simulation simulation = simulation();
        simulation.spawnCommander(THROW_X, THROW_Y);

        steps(simulation, 5, throwing(THROW_X));

        assertThat(count(simulation, Thrown.class)).isZero();
    }

    /** One tick of input asking for a throw, aimed along the x axis at the given point. */
    private static CommanderInput throwing(float aimX) {
        return new CommanderInput(0f, 0f, aimX, THROW_Y, false, false, true, null);
    }

    /** How far the blast ends up from the commander, for a throw aimed at a point this far out. */
    private static float thrownAt(float aimX) {
        Simulation simulation = simulation();
        simulation.spawnCommander(THROW_X, THROW_Y);

        steps(simulation, WHOLE_FLIGHT + 1, throwing(aimX));

        return blastPosition(simulation).x - THROW_X;
    }

    /** How many ticks a grenade thrown across this world stays in the air. */
    private static int flightOf(Simulation simulation) {
        simulation.spawnGrenade(THROW_X, THROW_Y, 1f, 0f);
        int ticks = 0;
        while (count(simulation, Blast.class) == 0 && ticks < WHOLE_FLIGHT * 3) {
            simulation.step();
            ticks++;
        }
        return ticks;
    }

    private static Position blastPosition(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Blast.class, Position.class).get())
                .first()
                .getComponent(Position.class);
    }
}
