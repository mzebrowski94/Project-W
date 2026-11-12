package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESERVE_START;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SQUAD_GROWTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SQUAD_SIZE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_REINFORCEMENT_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * The reserve: the men a side has off the field, and the one pool every man who comes onto it is
 * drawn from after the level starts (G0055) - the muster's troopers and a returning commander alike.
 *
 * <p>Four numbers make it: how many start on the field and how many it holds (the squad and its
 * headroom), and how many start in reserve and how many the reserve holds. The field fills from the
 * reserve a few men at a time, every muster, at the rally point - the spawn area until a point is
 * taken, the point taken last after that. A side with nobody on the field and nobody in reserve has
 * lost the level.
 */
class ReserveTest {

    private static final float MUSTER_SECONDS = 1f;

    private static final int MUSTER = 2;

    /** A few ticks past one muster, so the one that was due has happened. */
    private static final int ONE_MUSTER = ticksIn(MUSTER_SECONDS) + 2;

    /** From the killing blow to being back on his feet: the body lingers, and then he is away. */
    private static final int RETURN = ticksIn(DEATH_THROES + RESPAWN_DELAY) + 3;

    @Test
    void startsTheLevelWithTheReserveTheConfigNames() {
        Simulation simulation = new Simulation(config());

        assertThat(simulation.level().reserve).isEqualTo(RESERVE_START);
    }

    @Test
    void mustersMenFromTheReserveOntoTheFieldEveryPeriod() {
        Simulation simulation = new Simulation(mustering(20));

        steps(simulation, ONE_MUSTER);

        assertThat(count(simulation, Trooper.class)).isEqualTo(MUSTER);
        assertThat(simulation.level().reserve).isEqualTo(20 - MUSTER);
    }

    @Test
    void mustersAgainEveryPeriodAfterThat() {
        Simulation simulation = new Simulation(mustering(20));

        steps(simulation, ONE_MUSTER + ticksIn(MUSTER_SECONDS));

        assertThat(count(simulation, Trooper.class)).isEqualTo(MUSTER * 2);
    }

    /** A full field takes nothing from the reserve: the men wait, they are not spent. */
    @Test
    void mustersNobodyWhileTheFieldIsFull() {
        Simulation simulation = new Simulation(mustering(20));
        for (int i = 0; i < SQUAD_SIZE + SQUAD_GROWTH; i++) {
            simulation.spawnTrooper(60f + i * 3f, 50f);
        }

        steps(simulation, ONE_MUSTER);

        assertThat(count(simulation, Trooper.class)).isEqualTo(SQUAD_SIZE + SQUAD_GROWTH);
        assertThat(simulation.level().reserve).isEqualTo(20);
    }

    @Test
    void mustersOnlyAsManyAsTheFieldHasRoomFor() {
        Simulation simulation = new Simulation(mustering(20));
        for (int i = 0; i < SQUAD_SIZE + SQUAD_GROWTH - 1; i++) {
            simulation.spawnTrooper(60f + i * 3f, 50f);
        }

        steps(simulation, ONE_MUSTER);

        assertThat(count(simulation, Trooper.class)).isEqualTo(SQUAD_SIZE + SQUAD_GROWTH);
        assertThat(simulation.level().reserve).isEqualTo(19);
    }

    @Test
    void mustersNoMoreThanTheReserveHolds() {
        Simulation simulation = new Simulation(mustering(1));

        steps(simulation, ONE_MUSTER + ticksIn(MUSTER_SECONDS));

        assertThat(count(simulation, Trooper.class)).isEqualTo(1);
        assertThat(simulation.level().reserve).isZero();
    }

    /** Before any point is taken the rally point is where the level started the side. */
    @Test
    void mustersMenInTheSpawnAreaWhileNoPointIsTaken() {
        Simulation simulation = new Simulation(mustering(20));

        steps(simulation, ONE_MUSTER);

        TileMap map = simulation.map();
        TileRect spawn = simulation.config().spawn().on(map);
        for (Entity trooper : troopers(simulation)) {
            Position at = trooper.getComponent(Position.class);
            assertThat(spawn.contains(map.colAt(at.x), map.rowAt(at.y))).isTrue();
        }
    }

    /** After that it is the point taken last - ground the side holds, and the one nearest the fight. */
    @Test
    void mustersMenAtThePointTakenLast() {
        Simulation simulation = new Simulation(mustering(20));
        take(simulation.spawnControlPoint(40f, 50f), 1);
        take(simulation.spawnControlPoint(80f, 50f), 2);

        steps(simulation, ONE_MUSTER);

        TileMap map = simulation.map();
        TileRect around = TileRect.around(map, 80f, 50f, TROOPER_REINFORCEMENT_RADIUS);
        for (Entity trooper : troopers(simulation)) {
            Position at = trooper.getComponent(Position.class);
            assertThat(around.contains(map.colAt(at.x), map.rowAt(at.y))).isTrue();
        }
    }

    /** Ground held afterwards is where the last fight is, never where a man comes onto the field (G0061). */
    @Test
    void neverMustersMenOnGroundThatIsHeldAfterwards() {
        Simulation simulation = new Simulation(mustering(20));
        take(simulation.spawnControlPoint(40f, 50f), 1);
        Entity held = simulation.spawnControlPoint(80f, 50f);
        held.getComponent(ControlPoint.class).holdAfterwards(0f);
        take(held, 2);

        steps(simulation, ONE_MUSTER);

        TileMap map = simulation.map();
        TileRect around = TileRect.around(map, 40f, 50f, TROOPER_REINFORCEMENT_RADIUS);
        for (Entity trooper : troopers(simulation)) {
            Position at = trooper.getComponent(Position.class);
            assertThat(around.contains(map.colAt(at.x), map.rowAt(at.y))).isTrue();
        }
    }

    /** A commander coming back is a man from the reserve like any other. */
    @Test
    void takesAManFromTheReserveToBringACommanderBack() {
        Simulation simulation = new Simulation(config());
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, RETURN);

        assertThat(count(simulation, Commander.class)).isEqualTo(1);
        assertThat(simulation.level().reserve).isEqualTo(RESERVE_START - 1);
    }

    /**
     * He counts against the field's most once he is on it, and is never kept off it by that: a full field
     * stops troopers, not a player coming back.
     */
    @Test
    void bringsACommanderBackEvenOntoAFullField() {
        SimConfig base = config();
        Simulation simulation = new Simulation(base.withTrooper(base.trooper().withSquadGrowth(0)));
        for (int i = 0; i < SQUAD_SIZE; i++) {
            simulation.spawnTrooper(20f + i * 3f, 20f);
        }
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, RETURN);

        assertThat(count(simulation, Commander.class)).isEqualTo(1);
    }

    @Test
    void bringsACommanderBackAtTheRallyPointRatherThanWhereHeFell() {
        Simulation simulation = new Simulation(config());
        take(simulation.spawnControlPoint(40f, 50f), 1);
        kill(simulation.spawnCommander(90f, 90f));

        steps(simulation, RETURN);

        TileMap map = simulation.map();
        TileRect around = TileRect.around(map, 40f, 50f, TROOPER_REINFORCEMENT_RADIUS);
        Position at = commander(simulation).getComponent(Position.class);
        assertThat(around.contains(map.colAt(at.x), map.rowAt(at.y))).isTrue();
    }

    /** Nobody in reserve is nobody to come back as: he stays out, and his ticket waits. */
    @Test
    void keepsACommanderOutWhileTheReserveIsEmpty() {
        Simulation simulation = new Simulation(reserveOf(0));
        simulation.spawnCommander(20f, 20f);
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, RETURN);

        assertThat(count(simulation, Commander.class)).isEqualTo(1);
    }

    @Test
    void bringsHimBackOnceTheReserveHasAManAgain() {
        Simulation simulation = new Simulation(reserveOf(0));
        simulation.spawnCommander(20f, 20f);
        kill(simulation.spawnCommander(50f, 50f));
        steps(simulation, RETURN);

        simulation.level().reserve = 1;
        steps(simulation, RETURN);

        assertThat(count(simulation, Commander.class)).isEqualTo(2);
        assertThat(simulation.level().reserve).isZero();
    }

    /**
     * A commander is paid for the moment he is gone, not when he comes back: the last man in reserve
     * brings a player back rather than going to a muster that falls while he waits.
     */
    @Test
    void givesTheLastManInReserveToACommanderBeforeTheMuster() {
        SimConfig base = mustering(1);
        Simulation simulation =
                new Simulation(base.withReserve(base.reserve().withMusterSeconds(DEATH_THROES + RESPAWN_DELAY * 0.5f)));
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, RETURN);

        assertThat(count(simulation, Commander.class)).isEqualTo(1);
        assertThat(count(simulation, Trooper.class)).isZero();
    }

    /** Every commander out and nobody left to send: the level is lost, and stays lost. */
    @Test
    void losesTheLevelWhenEveryCommanderIsOutAndTheReserveIsEmpty() {
        Simulation simulation = new Simulation(reserveOf(0));
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, RETURN);
        simulation.level().reserve = 5;
        steps(simulation, RETURN);

        assertThat(simulation.level().failed).isTrue();
        assertThat(count(simulation, Commander.class)).isZero();
    }

    @Test
    void goesOnWhileOneCommanderIsStillOnTheField() {
        Simulation simulation = new Simulation(reserveOf(0));
        simulation.spawnCommander(20f, 20f);
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, RETURN);

        assertThat(simulation.level().failed).isFalse();
    }

    @Test
    void goesOnWhileTheReserveCanStillBringSomebodyBack() {
        Simulation simulation = new Simulation(reserveOf(1));
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, RETURN);

        assertThat(simulation.level().failed).isFalse();
        assertThat(count(simulation, Commander.class)).isEqualTo(1);
    }

    private static SimConfig mustering(int reserve) {
        SimConfig base = reserveOf(reserve);
        return base.withReserve(base.reserve()
                .withMusterSeconds(MUSTER_SECONDS)
                .withMusterMin(MUSTER)
                .withMusterMax(MUSTER));
    }

    private static SimConfig reserveOf(int reserve) {
        SimConfig base = config();
        return base.withReserve(base.reserve().withStart(reserve));
    }

    /** Marks a point taken, and in what order, as {@code ControlPointSystem} does on the tick it falls. */
    private static void take(Entity point, int order) {
        ControlPoint taken = point.getComponent(ControlPoint.class);
        taken.captured = true;
        taken.takenOrder = order;
    }

    private static void kill(Entity entity) {
        entity.getComponent(Damage.class).land(0, Harm.KILL);
        entity.getComponent(Invulnerable.class).remainingTicks = 0;
    }

    private static Entity commander(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Commander.class).get())
                .first();
    }

    private static Iterable<Entity> troopers(Simulation simulation) {
        return simulation.engine().getEntitiesFor(Family.all(Trooper.class).get());
    }
}
