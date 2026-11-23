package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ACTION_REACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RELIC_HATCH_MAX;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RELIC_HATCH_MIN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RELIC_HATCH_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RELIC_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RELIC_ROOM;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RELIC_WARDEN_OFFSET;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RELIC_WORK_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WARDEN_PERIOD;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Emplacement;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Site;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.WorkedSite;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.EmplacementKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;

/**
 * The desert's relic (G0114): extracted as a mainframe is downloaded, under four wardens on its diagonals that wake on
 * its first attempt and fire north, east, south and west every period until it is taken - which leaves the plinth bare
 * and hatches snakes outside the ring, off the men.
 */
class RelicTest {

    private static final float MIDDLE = 50f;

    /** How far off the relic's middle each warden's lanes run: the diagonal offset's share of north and of east. */
    private static final float LANE = RELIC_WARDEN_OFFSET * 0.70710677f;

    /**
     * Square on the relic's east compass point, just outside the ring: clear of the lanes by a shot's width and still
     * within arm's length of the plinth - the one place to extract from untouched.
     */
    private static final float OUT_OF_THE_LANES = MIDDLE + LANE + COMMANDER_RADIUS + 0.4f;

    /** Between the plinth and the wardens, inside the ring. */
    private static final float INSIDE_THE_RING = MIDDLE + RELIC_RADIUS + COMMANDER_RADIUS + 0.3f;

    private static final CommanderInput EXTRACTING = CommanderInput.IDLE.acting(true);

    @Test
    void standsFourWardensOnItsDiagonalsGuardingIt() {
        Simulation simulation = harmless();
        Entity relic = simulation.spawnWorkedSite(WorkedSiteKind.RELIC, MIDDLE, MIDDLE);

        List<Entity> wardens = wardens(simulation);

        assertThat(wardens).hasSize(4);
        for (Entity warden : wardens) {
            Position at = warden.getComponent(Position.class);
            assertThat(Math.abs(at.x - MIDDLE)).isCloseTo(LANE, org.assertj.core.data.Offset.offset(0.001f));
            assertThat(Math.abs(at.y - MIDDLE)).isCloseTo(LANE, org.assertj.core.data.Offset.offset(0.001f));
            assertThat(warden.getComponent(Emplacement.class).guards).isEqualTo(pl.mzebrows.pwt.sim.Handles.of(relic));
        }
    }

    @Test
    void isTakenOnTheActionKeyHeldFromOutOfTheLanesUntouched() {
        Simulation simulation = harmless();
        Entity relic = simulation.spawnWorkedSite(WorkedSiteKind.RELIC, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(OUT_OF_THE_LANES, MIDDLE);

        simulation.step(EXTRACTING);
        ActionKind offered = commander.getComponent(Action.class).available;
        steps(simulation, ticksIn(RELIC_WORK_SECONDS), EXTRACTING);

        assertThat(OUT_OF_THE_LANES - MIDDLE - RELIC_RADIUS - COMMANDER_RADIUS).isLessThanOrEqualTo(ACTION_REACH);
        assertThat(offered).isEqualTo(ActionKind.EXTRACT);
        assertThat(relic.getComponent(Site.class).taken).isTrue();
        assertThat(unhurt(commander)).as("out of every lane").isTrue();
    }

    @Test
    void offersItsExtractionFromInsideTheRing() {
        Simulation simulation = harmless();
        simulation.spawnWorkedSite(WorkedSiteKind.RELIC, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(INSIDE_THE_RING, MIDDLE);

        simulation.step();

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.EXTRACT);
        assertThat(commander.getComponent(Position.class).x)
                .as("he fits between the plinth and the wardens")
                .isCloseTo(INSIDE_THE_RING, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    void keepsItsWardensSilentUntilItsFirstAttempt() {
        Simulation simulation = harmless();
        simulation.spawnWorkedSite(WorkedSiteKind.RELIC, MIDDLE, MIDDLE);
        simulation.spawnCommander(OUT_OF_THE_LANES, MIDDLE);

        steps(simulation, ticksIn(WARDEN_PERIOD) * 4);

        assertThat(shots(simulation)).as("a man by it wakes nothing").isEmpty();
        assertThat(wardens(simulation))
                .allSatisfy(warden ->
                        assertThat(warden.getComponent(Emplacement.class).awake).isFalse());
    }

    @Test
    void wakesAllFourOnItsFirstAttemptToFireNorthEastSouthAndWestEveryPeriodWhileNobodyWorksIt() {
        Simulation simulation = harmless();
        simulation.spawnWorkedSite(WorkedSiteKind.RELIC, MIDDLE, MIDDLE);
        simulation.spawnCommander(OUT_OF_THE_LANES, MIDDLE);
        int period = ticksIn(WARDEN_PERIOD);

        simulation.step(EXTRACTING);
        steps(simulation, period - 2);
        int early = shots(simulation).size();
        simulation.step();
        List<Entity> first = shots(simulation);
        steps(simulation, period * 3 - 1);
        int beforeTheFourth = shots(simulation).size();
        simulation.step();
        int fourth = shots(simulation).size() - beforeTheFourth;

        assertThat(early).as("a tick short of a period").isZero();
        assertThat(first).hasSize(16);
        assertThat(first)
                .extracting(RelicTest::heading)
                .filteredOn(heading -> heading[0] == 0f && heading[1] == 1f)
                .hasSize(4);
        assertThat(first)
                .extracting(RelicTest::heading)
                .filteredOn(heading -> heading[0] == -1f && heading[1] == 0f)
                .hasSize(4);
        assertThat(fourth).as("nobody has worked it since its first tick").isEqualTo(16);
    }

    @Test
    void silencesItsWardensForGoodOnceItIsTaken() {
        Simulation simulation = harmless();
        simulation.spawnWorkedSite(WorkedSiteKind.RELIC, MIDDLE, MIDDLE);
        simulation.spawnCommander(OUT_OF_THE_LANES, MIDDLE);

        steps(simulation, ticksIn(RELIC_WORK_SECONDS) + 1, EXTRACTING);
        steps(simulation, ticksIn(WARDEN_PERIOD) * 3);

        assertThat(wardens(simulation))
                .allSatisfy(warden ->
                        assertThat(warden.getComponent(Emplacement.class).awake).isFalse());
        assertThat(shots(simulation))
                .as("what was in the air has run its range")
                .isEmpty();
    }

    @Test
    void woundsACommanderInALaneAndPassesAnAlien() {
        SimConfig config =
                everyStrain(config(), strain -> strain.withContactDamage(0).withSpeed(0f));
        Simulation simulation = new Simulation(config);
        simulation.spawnWorkedSite(WorkedSiteKind.RELIC, MIDDLE, MIDDLE);
        // North of the north-east warden, in its northward lane: an alien, and a man past it.
        Entity alien = simulation.spawnEnemy(MIDDLE + LANE, MIDDLE + LANE + 5f);
        Entity commander = simulation.spawnCommander(MIDDLE + LANE, MIDDLE + LANE + 11f);
        for (Entity warden : wardens(simulation)) {
            warden.getComponent(Emplacement.class).awake = true;
        }

        steps(simulation, ticksIn(WARDEN_PERIOD) + ticksIn(1f));

        assertThat(alien.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
        assertThat(unhurt(commander)).isFalse();
    }

    @Test
    void leavesThePlinthStandingAndHatchesItsSnakesOutsideTheRingOffTheMen() {
        Simulation simulation = harmless();
        Entity relic = simulation.spawnWorkedSite(WorkedSiteKind.RELIC, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(OUT_OF_THE_LANES, MIDDLE);

        steps(simulation, ticksIn(RELIC_WORK_SECONDS) + 1, EXTRACTING);

        List<Entity> snakes = snakes(simulation);
        assertThat(relic.isScheduledForRemoval()).isFalse();
        assertThat(simulation.engine().getEntities()).contains(relic);
        assertThat(relic.getComponent(Obstacle.class))
                .as("the plinth stays solid")
                .isNotNull();
        assertThat(snakes).hasSizeBetween(RELIC_HATCH_MIN, RELIC_HATCH_MAX);
        Position man = commander.getComponent(Position.class);
        float keepOff = simulation.config().nest().hatchKeepOff();
        for (Entity snake : snakes) {
            Position at = snake.getComponent(Position.class);
            float radius = snake.getComponent(Body.class).radius;
            float fromRelic = distance(at.x, at.y, MIDDLE, MIDDLE);
            assertThat(fromRelic).as("outside the ring").isGreaterThanOrEqualTo(RELIC_ROOM + radius);
            assertThat(fromRelic)
                    .as("near it")
                    .isLessThanOrEqualTo(RELIC_HATCH_RADIUS + simulation.map().tileSize());
            assertThat(distance(at.x, at.y, man.x, man.y)).as("off the men").isGreaterThanOrEqualTo(keepOff);
        }
    }

    @Test
    void isLeftToTheCommandersByTheSquad() {
        Simulation simulation = harmless();
        Entity relic = simulation.spawnWorkedSite(WorkedSiteKind.RELIC, MIDDLE, MIDDLE);
        simulation.spawnTrooper(OUT_OF_THE_LANES, MIDDLE);
        simulation.spawnTrooper(MIDDLE - LANE - 3f, MIDDLE);

        steps(simulation, ticksIn(RELIC_WORK_SECONDS) * 4);

        assertThat(relic.getComponent(Site.class).taken).isFalse();
        assertThat(relic.getComponent(WorkedSite.class).trooper).isEqualTo(Handle.NOBODY);
        assertThat(wardens(simulation))
                .allSatisfy(warden ->
                        assertThat(warden.getComponent(Emplacement.class).awake).isFalse());
    }

    @Test
    void rousesTwiceAsFarAsAControlPointOnTheShippedNumbers() {
        SimConfig shipped = SimConfig.defaults();

        assertThat(shipped.workedSite(WorkedSiteKind.RELIC).rouseRadius())
                .isEqualTo(shipped.controlPoint().aggroRadius() * 2f);
    }

    private static List<Entity> wardens(Simulation simulation) {
        List<Entity> wardens = new ArrayList<>();
        for (Entity entity :
                simulation.engine().getEntitiesFor(Family.all(Emplacement.class).get())) {
            if (entity.getComponent(Emplacement.class).kind == EmplacementKind.WARDEN) {
                wardens.add(entity);
            }
        }
        return wardens;
    }

    private static List<Entity> snakes(Simulation simulation) {
        List<Entity> snakes = new ArrayList<>();
        for (Entity entity :
                simulation.engine().getEntitiesFor(Family.all(Strain.class).get())) {
            if (entity.getComponent(Strain.class).kind == UnitKind.SNAKE) {
                snakes.add(entity);
            }
        }
        return snakes;
    }

    private static List<Entity> shots(Simulation simulation) {
        List<Entity> shots = new ArrayList<>();
        for (Entity shot :
                simulation.engine().getEntitiesFor(Family.all(Projectile.class).get())) {
            shots.add(shot);
        }
        return shots;
    }

    private static float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Which way a shot flies, rounded to the nearest thousandth of a unit vector so a compass point compares. */
    private static float[] heading(Entity shot) {
        Velocity velocity = shot.getComponent(Velocity.class);
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        return new float[] {round(velocity.x / speed), round(velocity.y / speed)};
    }

    private static float round(float value) {
        return Math.round(value * 1000f) / 1000f + 0f;
    }
}
