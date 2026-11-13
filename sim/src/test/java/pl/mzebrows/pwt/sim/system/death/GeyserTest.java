package pl.mzebrows.pwt.sim.system.death;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.FIRE_BLOBS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GEYSER_BURN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GEYSER_DORMANT_MAX;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GEYSER_DORMANT_MIN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GEYSER_FILLING;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GEYSER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.burning;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Geyser;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * A geyser (G0089): ground that lies dormant, fills, and erupts in fire, over and over.
 *
 * <p><strong>Dormant it is ground.</strong> Nothing stops on it, steers round it or is hurt by it. <strong>Its
 * eruption is ordinary fire</strong> - the second source of it beside a blast (G0035, amended), sized to the geyser,
 * on nobody's side and steered round - so a trooper whose way crosses one waits it out or goes round, and gets past
 * once it is quiet again, by the avoidance every fire already gets (G0046).
 */
class GeyserTest {

    private static final float MIDDLE = 50f;

    /** Past the longest first dormancy and the filling after it: an eruption has been laid. */
    private static final int UNTIL_IT_HAS_ERUPTED = ticksIn(GEYSER_DORMANT_MAX + GEYSER_FILLING) + 2;

    /** A tick short of the shortest a geyser may lie quiet. */
    private static final int WHILE_IT_MUST_BE_QUIET = ticksIn(GEYSER_DORMANT_MIN) - 1;

    /** How long a trooper is given to cross once the fire is out: well inside the shortest dormancy after it. */
    private static final float SECONDS_TO_GET_PAST = 4f;

    /** Where the commander waits, where the point is, and where the trooper sets off from: the geyser between. */
    private static final float WEST_END = 5f;

    private static final float EAST_END = 90f;

    private static final float TROOPER_START = 25f;

    /** Within a stride of the fire's edge: waiting for it, not hanging back where he started. */
    private static final float CLOSE_ENOUGH_TO_WAIT = 10f;

    /** Enough geysers that a rhythm shared by all of them would be no accident. */
    private static final int SEVERAL = 6;

    @Test
    void liesDormantAsGroundAManStandsOnUnharmed() {
        Simulation simulation = new Simulation(burning());
        Entity geyser = simulation.spawnGeyser(MIDDLE, MIDDLE, GEYSER_RADIUS);
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);

        steps(simulation, WHILE_IT_MUST_BE_QUIET);

        assertThat(geyser.getComponent(Collider.class)).isNull();
        assertThat(count(simulation, Slick.class)).isZero();
        assertThat(unhurt(commander)).isTrue();
        assertThat(commander.getComponent(Position.class).x).isEqualTo(MIDDLE);
    }

    @Test
    void eruptsInFireOnNobodysSideOnceItHasFilled() {
        Simulation simulation = new Simulation(burning());
        Entity geyser = simulation.spawnGeyser(MIDDLE, MIDDLE, GEYSER_RADIUS);
        int ticks = 0;
        while (geyser.getComponent(Geyser.class).phase != Geyser.Phase.ERUPTING) {
            simulation.step();
            ticks++;
        }
        simulation.step();

        assertThat(ticks).isLessThanOrEqualTo(UNTIL_IT_HAS_ERUPTED);
        assertThat(count(simulation, Slick.class)).isEqualTo(FIRE_BLOBS);
        for (Entity fire : fires(simulation)) {
            assertThat(fire.getComponent(Appearance.class).look).isEqualTo(Look.FIRE);
            assertThat(fire.getComponent(Faction.class).side).isEqualTo(Side.NEUTRAL);
            Position at = fire.getComponent(Position.class);
            assertThat(Math.hypot(at.x - MIDDLE, at.y - MIDDLE)).isLessThanOrEqualTo(GEYSER_RADIUS);
        }
    }

    /** The warning is on the screen, never a number to count down (pillar 5). */
    @Test
    void wearsItsFireMarkWhileItFillsAndNothingWhileItIsQuiet() {
        Simulation simulation = new Simulation(burning());
        Entity geyser = simulation.spawnGeyser(MIDDLE, MIDDLE, GEYSER_RADIUS);
        Appearance appearance = geyser.getComponent(Appearance.class);
        steps(simulation, 2);
        assertThat(appearance.charge).isZero();

        while (geyser.getComponent(Geyser.class).phase != Geyser.Phase.FILLING) {
            simulation.step();
        }
        steps(simulation, ticksIn(GEYSER_FILLING) / 2);

        assertThat(appearance.chargeLook).isEqualTo(Look.FIRE);
        assertThat(appearance.charge).isGreaterThan(0f);
    }

    @Test
    void goesQuietOnceItsFireIsOutAndThenEruptsAgain() {
        Simulation simulation = new Simulation(burning());
        Entity geyser = simulation.spawnGeyser(MIDDLE, MIDDLE, GEYSER_RADIUS);
        Geyser cycle = geyser.getComponent(Geyser.class);
        int eruptions = 0;
        boolean burningWhileQuiet = false;
        Geyser.Phase was = cycle.phase;
        for (int tick = 0; tick < 2 * UNTIL_IT_HAS_ERUPTED + ticksIn(2 * GEYSER_BURN); tick++) {
            simulation.step();
            eruptions += cycle.phase == Geyser.Phase.ERUPTING && was != Geyser.Phase.ERUPTING ? 1 : 0;
            burningWhileQuiet |= cycle.phase == Geyser.Phase.DORMANT && count(simulation, Slick.class) > 0;
            was = cycle.phase;
        }

        assertThat(eruptions).isGreaterThanOrEqualTo(2);
        assertThat(burningWhileQuiet)
                .as("fire on the ground while it is dormant")
                .isFalse();
    }

    /** Each keeps a time of its own, so a field of them is a rhythm to read rather than one clock. */
    @Test
    void keepsATimeOfItsOwn() {
        Simulation simulation = new Simulation(burning());
        Set<Integer> firstWaits = new HashSet<>();
        for (int i = 0; i < SEVERAL; i++) {
            Entity geyser = simulation.spawnGeyser(10f + i * 12f, MIDDLE, GEYSER_RADIUS);
            firstWaits.add(geyser.getComponent(Geyser.class).remainingTicks);
        }

        assertThat(firstWaits).hasSizeGreaterThan(1);
    }

    @Test
    void neverLiesQuietForLessThanTheFloor() {
        SimConfig.GeyserConfig geyser = burning().geyser();

        for (int cycle = 0; cycle < 64; cycle++) {
            assertThat(geyser.dormantTicks(cycle * 31L, cycle, cycle, AlphaWorlds.TICK))
                    .isGreaterThanOrEqualTo(ticksIn(SimConfig.GeyserConfig.DORMANT_FLOOR_SECONDS));
        }
        assertThatThrownBy(() -> geyser.withDormantMinSeconds(SimConfig.GeyserConfig.DORMANT_FLOOR_SECONDS - 0.5f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * <strong>Troopers wait</strong> (ticket 15): a man whose way to the point runs through an erupting geyser, in a
     * corridor its fire all but fills, holds short of it and is past once it has gone quiet - by the avoidance every
     * fire gets, weighed along the way the wall leaves him (G0090), with no hold of a geyser's own. Shipped numbers.
     */
    @Test
    void aTrooperHoldsShortOfAnEruptionAcrossACorridorAndGetsPastOnceItIsQuiet() {
        Crossing crossing = crossing(corridor());

        assertThat(crossing.burned).as("walked into the fire").isFalse();
        assertThat(crossing.furthestWhileBurning)
                .as("held short of it")
                .isBetween(MIDDLE - CLOSE_ENOUGH_TO_WAIT, MIDDLE);
        assertThat(crossing.endedAt).as("got past once it was quiet").isGreaterThan(MIDDLE + crossing.radius);
    }

    /** On open ground the same avoidance takes him round it, and nothing waits at all. */
    @Test
    void aTrooperGoesRoundAnEruptionOnOpenGround() {
        Crossing crossing = crossing(ground().build());

        assertThat(crossing.burned).as("walked into the fire").isFalse();
        assertThat(crossing.endedAt).isGreaterThan(MIDDLE + crossing.radius);
    }

    /** What happened to one trooper sent east past the middle of the field while a geyser there erupted. */
    private record Crossing(boolean burned, float furthestWhileBurning, float endedAt, float radius) {}

    private static Crossing crossing(TileMap map) {
        SimConfig shipped = SimConfig.FILE.defaults();
        float radius = shipped.geyser().radiusMax();
        Simulation simulation = new Simulation(shipped, map);
        Entity geyser = simulation.spawnGeyser(MIDDLE, MIDDLE, radius);
        while (geyser.getComponent(Geyser.class).phase != Geyser.Phase.ERUPTING) {
            simulation.step();
        }
        simulation.spawnCommander(WEST_END, MIDDLE);
        simulation.spawnControlPoint(EAST_END, MIDDLE);
        Entity trooper = simulation.spawnTrooper(TROOPER_START, MIDDLE);

        boolean burned = false;
        float furthestWhileBurning = 0f;
        for (int tick = 0; tick < ticksIn(shipped.geyser().burnSeconds() + SECONDS_TO_GET_PAST); tick++) {
            simulation.step();
            burned |= !unhurt(trooper);
            if (count(simulation, Slick.class) > 0) {
                furthestWhileBurning = Math.max(furthestWhileBurning, trooper.getComponent(Position.class).x);
            }
        }
        return new Crossing(burned, furthestWhileBurning, trooper.getComponent(Position.class).x, radius);
    }

    private static Iterable<Entity> fires(Simulation simulation) {
        return simulation.engine().getEntitiesFor(Family.all(Slick.class).get());
    }

    /** Rock either side of a corridor two tiles wide along the middle rows: a geyser's fire all but fills it. */
    private static TileMap corridor() {
        return ground().rect(0, 0, ARENA_TILES, 9, Terrain.ROCK)
                .rect(0, 11, ARENA_TILES, ARENA_TILES - 11, Terrain.ROCK)
                .build();
    }
}
