package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BROOD;
import static pl.mzebrows.pwt.sim.AlphaWorlds.NEST_HATCH_KEEP_OFF;
import static pl.mzebrows.pwt.sim.AlphaWorlds.NEST_HATCH_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.NEST_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.NEST_PERIOD;
import static pl.mzebrows.pwt.sim.AlphaWorlds.NEST_ROUSE_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.NEST_WAKE_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.godMode;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A nest is a place that produces, which is the third way a level puts creatures on the field and
 * the only one that can be walked to and taken away. Ground you have cleared stays cleared only if
 * you dealt with what was filling it in.
 *
 * <p>Three rules are worth more than the rest. <strong>It sleeps until somebody comes near</strong>,
 * or a level four screens wide would hand the player an accumulation rather than a nest.
 * <strong>Waking latches</strong>, or a nest would be a thing you could wait out. And <strong>it is
 * at its worst the moment you kill it</strong>, which is what makes destroying one a decision about
 * when rather than a chore.
 */
class NestTest {

    private static final float X = 50f;
    private static final float Y = 50f;

    /** Comfortably outside the wake radius, and still inside the fixture's arena. */
    private static final float FAR = X + NEST_WAKE_RADIUS + 20f;

    /** A couple of ticks past a whole period, so a brood that was due has actually been sent. */
    private static final int ONE_PERIOD = ticksIn(NEST_PERIOD) + 2;

    @Test
    void sendsOutNothingAtAllUntilSomebodyComesNear() {
        Simulation simulation = simulation();
        simulation.spawnNest(UnitKind.GRUNT, X, Y);
        simulation.spawnCommander(FAR, Y);

        steps(simulation, ONE_PERIOD * 3);

        assertThat(creatures(simulation)).isZero();
    }

    @Test
    void startsWorkingOnceSomebodyComesNear() {
        Simulation simulation = woken();

        steps(simulation, ONE_PERIOD);

        assertThat(creatures(simulation)).isEqualTo(BROOD);
    }

    @Test
    void goesOnSendingThemOutPeriodAfterPeriod() {
        Simulation simulation = woken();

        steps(simulation, ONE_PERIOD * 2);

        assertThat(creatures(simulation)).isEqualTo(BROOD * 2);
    }

    /**
     * Brood sizes come out of a bag: drawn independently, a nest could send its smallest three times
     * running, which is a streak a player notices and blames on the level.
     */
    @Test
    void sendsEveryBroodSizeInItsRangeOnceBeforeRepeatingOne() {
        SimConfig config = config().withNest(config().nest().withBroodMin(1).withBroodMax(3));
        Simulation simulation = new Simulation(config);
        simulation.spawnNest(UnitKind.GRUNT, X, Y);
        simulation.spawnCommander(X + 2f, Y);
        godMode(simulation);
        List<Integer> broods = new ArrayList<>();
        int before = 0;

        while (broods.size() < 6) {
            simulation.step();
            int now = creatures(simulation);
            if (now > before) {
                broods.add(now - before);
                before = now;
            }
        }

        assertThat(broods.subList(0, 3)).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(broods.subList(3, 6)).containsExactlyInAnyOrder(1, 2, 3);
    }

    /**
     * Waking latches. A nest that went quiet the moment you backed off would be a nest you could
     * wait out from just past its own radius, which is not a problem the level wants the player to
     * be able to solve by standing still.
     */
    @Test
    void goesOnWorkingAfterWhoeverWokeItHasGone() {
        Simulation simulation = simulation();
        simulation.spawnNest(UnitKind.GRUNT, X, Y);
        Entity commander = simulation.spawnCommander(X + 2f, Y);
        simulation.step();
        commander.getComponent(pl.mzebrows.pwt.sim.component.Position.class).set(FAR, Y);

        steps(simulation, ONE_PERIOD);

        assertThat(creatures(simulation)).isEqualTo(BROOD);
    }

    /** It is worst at the moment you kill it, and that is the whole of what makes killing one a choice. */
    @Test
    void emptiesTheLastOfItselfWhenItIsDestroyed() {
        Simulation simulation = simulation();
        Entity nest = simulation.spawnNest(UnitKind.GRUNT, X, Y);

        nest.getComponent(Damage.class).land(NEST_HEALTH, Harm.KILL);
        simulation.step();

        assertThat(creatures(simulation)).isEqualTo(BROOD);
    }

    /**
     * The last brood comes out of the hole, not out of the man standing over it: nothing hatched lands
     * within the keep-off of anybody on our side, however close he was when it died.
     */
    @Test
    void putsItsLastBroodDownOffTheManWhoKilledIt() {
        for (long seed = 1; seed <= 20; seed++) {
            Simulation simulation = new Simulation(config().withSeed(seed));
            Entity nest = simulation.spawnNest(UnitKind.GRUNT, X, Y);
            Entity commander = simulation.spawnCommander(X + 4f, Y + 1f);
            Position him = commander.getComponent(Position.class);

            nest.getComponent(Damage.class).land(NEST_HEALTH, Harm.KILL);
            simulation.step();

            for (Position at : broodPositions(simulation)) {
                float gap = (float) Math.hypot(at.x - him.x, at.y - him.y);
                assertThat(gap).as("seed %d", seed).isGreaterThanOrEqualTo(NEST_HATCH_KEEP_OFF);
            }
        }
    }

    /**
     * And it comes out beside the hole: the nearest ground first, never the far corner of the square
     * of tiles round it - so within the hatch radius, give or take a tile's jitter.
     */
    @Test
    void putsItsBroodDownHardAgainstTheHole() {
        for (long seed = 1; seed <= 20; seed++) {
            Simulation simulation = new Simulation(config().withSeed(seed));
            Entity nest = simulation.spawnNest(UnitKind.GRUNT, X, Y);

            nest.getComponent(Damage.class).land(NEST_HEALTH, Harm.KILL);
            simulation.step();

            for (Position at : broodPositions(simulation)) {
                float gap = (float) Math.hypot(at.x - X, at.y - Y);
                assertThat(gap).as("seed %d", seed).isLessThanOrEqualTo(NEST_HATCH_RADIUS + TILE);
            }
        }
    }

    /** What comes out of it is the whole of what makes one nest different from another. */
    @Test
    void sendsOutWhateverStrainItWasBuiltFor() {
        Simulation simulation = simulation();
        Entity nest = simulation.spawnNest(UnitKind.SPITTER, X, Y);

        nest.getComponent(Damage.class).land(NEST_HEALTH, Harm.KILL);
        simulation.step();

        assertThat(strains(simulation)).containsOnly(UnitKind.SPITTER);
    }

    /**
     * Hitting one brings the neighbourhood, exactly as starting on a control point does - through
     * the one place that answers "there is trouble over there" (G0016). Taking a nest away is meant
     * to cost something rather than be done quietly from the edge of its range.
     */
    @Test
    void sendsEverythingWithinEarshotToSeeWhoIsHittingIt() {
        Simulation simulation = simulation();
        Entity nest = simulation.spawnNest(UnitKind.GRUNT, X, Y);
        Entity neighbour = simulation.spawnUnit(UnitKind.GRUNT, X + NEST_ROUSE_RADIUS - 5f, Y);

        nest.getComponent(Damage.class).land(1, Harm.KILL);
        simulation.step();

        assertThat(neighbour.getComponent(Perception.class).provoked).isTrue();
    }

    @Test
    void sendsNobodyAnywhereWhileNothingHasTouchedIt() {
        Simulation simulation = simulation();
        simulation.spawnNest(UnitKind.GRUNT, X, Y);
        Entity neighbour = simulation.spawnUnit(UnitKind.GRUNT, X + NEST_ROUSE_RADIUS - 5f, Y);

        steps(simulation, ONE_PERIOD);

        assertThat(neighbour.getComponent(Perception.class).provoked).isFalse();
    }

    @Test
    void leavesAlonesomeNeighbourWellOutOfEarshotWhereItWas() {
        Simulation simulation = simulation();
        Entity nest = simulation.spawnNest(UnitKind.GRUNT, X, Y);
        Entity distant = simulation.spawnUnit(UnitKind.GRUNT, X + NEST_ROUSE_RADIUS + 10f, Y);

        nest.getComponent(Damage.class).land(1, Harm.KILL);
        simulation.step();

        assertThat(distant.getComponent(Perception.class).provoked).isFalse();
    }

    /**
     * A level is not clear while something on it is still producing.
     *
     * <p>It falls out of a nest carrying the same marker every other hostile thing carries, rather
     * than out of a rule written anywhere - but it is worth pinning, because the alternative is an
     * objective that reads as met and then unmeets itself every thirty seconds.
     */
    @Test
    void leavesALevelUnclearedWhileItIsStillStanding() {
        Simulation simulation = simulation();
        simulation.spawnNest(UnitKind.GRUNT, X, Y);
        simulation.level().require(Objective.CLEAR_ENEMIES);

        simulation.step();

        assertThat(simulation.level().isMet(Objective.CLEAR_ENEMIES)).isFalse();
    }

    /** A world with a nest standing in it and somebody already inside its wake radius. */
    private static Simulation woken() {
        Simulation simulation = simulation();
        simulation.spawnNest(UnitKind.GRUNT, X, Y);
        simulation.spawnCommander(X + 2f, Y);
        return simulation;
    }

    /** Everything alive that came out of a nest. The nest itself carries no strain, so it is not one. */
    private static int creatures(Simulation simulation) {
        return countLiving(simulation, Strain.class);
    }

    private static List<Position> broodPositions(Simulation simulation) {
        List<Position> found = new ArrayList<>();
        for (Entity entity : simulation
                .engine()
                .getEntitiesFor(com.badlogic.ashley.core.Family.all(Enemy.class, Strain.class)
                        .get())) {
            found.add(entity.getComponent(Position.class));
        }
        assertThat(found).isNotEmpty();
        return found;
    }

    private static java.util.List<UnitKind> strains(Simulation simulation) {
        java.util.List<UnitKind> found = new java.util.ArrayList<>();
        for (Entity entity : simulation
                .engine()
                .getEntitiesFor(com.badlogic.ashley.core.Family.all(Enemy.class, Strain.class)
                        .get())) {
            found.add(entity.getComponent(Strain.class).kind);
        }
        return found;
    }
}
