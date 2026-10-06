package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BRUTE_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TICK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.leapConfig;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Sling;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.LeapDef;
import pl.mzebrows.pwt.sim.def.SlingDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The slinger (G0074): runners carried on its back, slung one at a time over the squad at whoever it hunts, landing
 * behind him.
 *
 * <p>The sling is told by the winding-up tint and aimed once as it leaves. The rider in the air is a leap nothing can
 * hurt, passing through bodies and stopped by scenery; it comes down harmless for a moment and is then an ordinary
 * runner. What is still aboard when the slinger dies spills round its body.
 */
class SlingerTest {

    private static final float MIDDLE = 50f;

    /** West of him, so it slings east and its riders come down east of him. */
    private static final float SLINGER_X = 30f;

    /** Inside its range, so it winds up on the first tick it sees him. */
    private static final float IN_RANGE = 20f;

    private static final int RIDERS = 3;
    private static final float MIN_RANGE = 8f;
    private static final float MAX_RANGE = 30f;
    private static final float WIND_UP = 0.5f;
    private static final float REST = 2f;
    private static final float SLING_SPEED = 30f;
    private static final float LAND_BEHIND = 5f;
    private static final float LANDING = 0.3f;
    private static final float SPILL_RADIUS = 6f;

    private static final int WIND_UP_TICKS = Math.round(WIND_UP / TICK);

    /** Long enough for the sling to leave and the rider to come down, with room to spare. */
    private static final int UNTIL_IT_HAS_LANDED = WIND_UP_TICKS + Math.round(2f / TICK);

    /** How far ahead of a rider in the air a shot is put to cross its path, and long enough after for it to have. */
    private static final float CROSSING = 3f;

    private static final int UNTIL_IT_HAS_CROSSED = 7;

    /** A blast round a rider in the air, small enough to reach nothing else. */
    private static final float BLAST = 2f;

    private static final int LANDING_TICKS = Math.round(LANDING / TICK);

    private static final int THROUGH_ITS_THROES = Math.round(DEATH_THROES / TICK) - 2;

    /** How near the place it was aimed at a rider must come down. */
    private static final float ON_THE_MARK = 1.5f;

    @Test
    void slingsARiderAtWhatItHuntsOnceItIsInRange() {
        Simulation simulation = new Simulation(slinging());
        simulation.spawnCommander(SLINGER_X + IN_RANGE, MIDDLE);
        Entity slinger = simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);

        steps(simulation, WIND_UP_TICKS + 2);

        assertThat(riders(simulation)).hasSize(1);
        assertThat(slinger.getComponent(Sling.class).riders).isEqualTo(RIDERS - 1);
    }

    /** The tint is the tell: it stands for it, and nothing leaves until it is over. */
    @Test
    void showsTheSlingAndStandsForItBeforeTheRiderLeaves() {
        Simulation simulation = new Simulation(slinging());
        simulation.spawnCommander(SLINGER_X + IN_RANGE, MIDDLE);
        Entity slinger = simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);
        steps(simulation, 2);
        float stoodAt = at(slinger).x;

        steps(simulation, WIND_UP_TICKS - 4);

        assertThat(slinger.getComponent(Appearance.class).state).isEqualTo(Appearance.State.WINDING_UP);
        assertThat(at(slinger).x).isEqualTo(stoodAt, within(0.01f));
        assertThat(riders(simulation)).isEmpty();
    }

    /** Through him, untouched, and down on the far side of him along the line it was slung. */
    @Test
    void landsTheRiderBehindWhatItWasSlungAtPassingThroughHim() {
        Simulation simulation = new Simulation(slinging());
        Entity commander = simulation.spawnCommander(SLINGER_X + IN_RANGE, MIDDLE);
        simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);

        Entity rider = untilItHasLanded(simulation);

        assertThat(at(rider).x).isEqualTo(SLINGER_X + IN_RANGE + LAND_BEHIND, within(ON_THE_MARK));
        assertThat(at(rider).y).isEqualTo(MIDDLE, within(ON_THE_MARK));
        assertThat(unhurt(commander)).isTrue();
    }

    @Test
    void slingsNothingAtSomethingNearerThanItsShortestRange() {
        Simulation simulation = new Simulation(slinging());
        simulation.spawnCommander(SLINGER_X + MIN_RANGE * 0.5f, MIDDLE);
        simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);

        steps(simulation, UNTIL_IT_HAS_LANDED);

        assertThat(riders(simulation)).isEmpty();
    }

    @Test
    void cannotBeHurtWhileItIsInTheAir() {
        Simulation simulation = new Simulation(slinging());
        simulation.spawnCommander(SLINGER_X + IN_RANGE, MIDDLE);
        simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);
        steps(simulation, WIND_UP_TICKS + 4);
        Entity rider = riders(simulation).get(0);

        // A blast is decisive on everything standing in it but this (G0074): it lands on the tick after.
        simulation.spawnBlast(at(rider).x, at(rider).y, BLAST);
        steps(simulation, 2);

        assertThat(rider.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
    }

    /** A shot crosses its flight as it crosses nothing: it is neither hit nor used up. */
    @Test
    void letsAShotPassThroughItInTheAir() {
        Simulation simulation = new Simulation(slinging());
        simulation.spawnCommander(SLINGER_X + IN_RANGE, MIDDLE);
        simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);
        steps(simulation, WIND_UP_TICKS + 8);
        Entity rider = riders(simulation).get(0);

        // Across its path, meeting it a few ticks on - and gone well before its own range runs out.
        simulation.spawnProjectile(at(rider).x + CROSSING, MIDDLE - CROSSING * 2f, 0f, 1f);
        steps(simulation, UNTIL_IT_HAS_CROSSED);

        assertThat(rider.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
        assertThat(count(simulation, Projectile.class)).isOne();
    }

    /** Down, it is on the field and killable, with its claws sheathed for a moment - and then a runner like any. */
    @Test
    void isHarmlessForAMomentAfterItComesDownAndBitesAfter() {
        Simulation simulation = new Simulation(slinging().withUnit(UnitKind.RUNNER, runner -> runner.withSpeed(0f)));
        Entity commander = simulation.spawnCommander(SLINGER_X + IN_RANGE, MIDDLE);
        simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);
        Entity rider = untilItHasLanded(simulation);
        // Stood on him as it comes down, so only its sheathed claws keep him on his feet.
        at(rider).set(at(commander).x + 1f, MIDDLE);

        steps(simulation, LANDING_TICKS - 2);

        assertThat(unhurt(commander)).isTrue();

        rider.getComponent(Damage.class).land(1, Harm.NONE);
        steps(simulation, 4);

        assertThat(rider.getComponent(Health.class).current).isLessThan(ENEMY_HEALTH);
        assertThat(unhurt(commander)).isFalse();
    }

    /** Scenery stops it, and it comes down where it stopped rather than finishing its flight. */
    @Test
    void comesDownWhereSceneryStopsIt() {
        Simulation simulation = new Simulation(slinging());
        simulation.spawnCommander(SLINGER_X + IN_RANGE, MIDDLE);
        simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);
        float obstacleX = SLINGER_X + IN_RANGE * 0.5f;
        simulation.spawnObstacle(obstacleX, MIDDLE);

        Entity rider = untilItHasLanded(simulation);

        assertThat(at(rider).x).isLessThan(obstacleX);
    }

    @Test
    void breaksTheSlingOffWhenADeclaredStunLandsAndKeepsTheRider() {
        Simulation simulation = new Simulation(slinging());
        simulation.spawnCommander(SLINGER_X + IN_RANGE, MIDDLE);
        Entity slinger = simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);
        steps(simulation, WIND_UP_TICKS - 4);

        slinger.getComponent(Stunned.class).daze(WIND_UP_TICKS);
        steps(simulation, WIND_UP_TICKS);

        assertThat(riders(simulation)).isEmpty();
        assertThat(slinger.getComponent(Sling.class).riders).isEqualTo(RIDERS);
    }

    @Test
    void spillsTheRidersStillAboardRoundItsBodyWhenItDies() {
        Simulation simulation = new Simulation(slinging());
        Entity slinger = simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);

        slinger.getComponent(Damage.class).land(BRUTE_HEALTH, Harm.KILL);
        // Through its throes, which must not spill it twice.
        steps(simulation, THROUGH_ITS_THROES);

        assertThat(riders(simulation)).hasSize(RIDERS);
        for (Entity rider : riders(simulation)) {
            float toX = at(rider).x - SLINGER_X;
            float toY = at(rider).y - MIDDLE;
            assertThat((float) Math.sqrt(toX * toX + toY * toY)).isLessThanOrEqualTo(SPILL_RADIUS + 5f);
        }
    }

    /** Hands full: a slinger with riders aboard slings them, and leaps only once there are none left. */
    @Test
    void leapsOnlyOnceItHasNothingLeftToSling() {
        Simulation simulation = new Simulation(slinging(leapConfig().withTriggerRange(MAX_RANGE)));
        simulation.spawnCommander(SLINGER_X + IN_RANGE, MIDDLE);
        Entity loaded = simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);

        steps(simulation, WIND_UP_TICKS / 2);

        assertThat(loaded.getComponent(Leap.class).committed()).isFalse();

        loaded.getComponent(Sling.class).riders = 0;
        steps(simulation, Math.round(REST / TICK) + WIND_UP_TICKS);

        assertThat(loaded.getComponent(Leap.class).committed() || loaded.getComponent(Leap.class).cooldownRemaining > 0)
                .isTrue();
    }

    /** Pillar 5: what it still carries is on its back, one fewer for every sling. */
    @Test
    void showsHowManyRidersItHasLeft() {
        Simulation simulation = new Simulation(slinging());
        simulation.spawnCommander(SLINGER_X + IN_RANGE, MIDDLE);
        Entity slinger = simulation.spawnUnit(UnitKind.SLINGER, SLINGER_X, MIDDLE);
        simulation.step();

        assertThat(slinger.getComponent(Appearance.class).riders).isEqualTo(RIDERS);

        steps(simulation, WIND_UP_TICKS + 2);

        assertThat(slinger.getComponent(Appearance.class).riders).isEqualTo(RIDERS - 1);
    }

    /** No claws, so the tests are about the sling and nothing it does up close. */
    private static SimConfig slinging() {
        return config().withUnit(
                        UnitKind.SLINGER,
                        slinger -> slinger.withContactDamage(0)
                                .withSling(new SlingDef(
                                        RIDERS,
                                        UnitKind.RUNNER,
                                        MIN_RANGE,
                                        MAX_RANGE,
                                        WIND_UP,
                                        REST,
                                        SLING_SPEED,
                                        LAND_BEHIND,
                                        LANDING,
                                        SPILL_RADIUS)));
    }

    private static SimConfig slinging(LeapDef leap) {
        return slinging().withUnit(UnitKind.SLINGER, slinger -> slinger.withLeap(leap));
    }

    /** Steps until the first rider slung has come down, and returns it. */
    private static Entity untilItHasLanded(Simulation simulation) {
        for (int tick = 0; tick < UNTIL_IT_HAS_LANDED; tick++) {
            simulation.step();
            List<Entity> riders = riders(simulation);
            if (!riders.isEmpty() && !riders.get(0).getComponent(Leap.class).untouchable) {
                return riders.get(0);
            }
        }
        throw new AssertionError("no rider came down");
    }

    private static List<Entity> riders(Simulation simulation) {
        ImmutableArray<Entity> strains =
                simulation.engine().getEntitiesFor(Family.all(Strain.class).get());
        List<Entity> riders = new ArrayList<>();
        for (Entity entity : strains) {
            if (entity.getComponent(Strain.class).kind == UnitKind.RUNNER) {
                riders.add(entity);
            }
        }
        return riders;
    }

    private static Position at(Entity entity) {
        return entity.getComponent(Position.class);
    }
}
