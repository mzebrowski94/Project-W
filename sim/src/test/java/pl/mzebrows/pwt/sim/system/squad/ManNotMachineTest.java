package pl.mzebrows.pwt.sim.system.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.function.UnaryOperator;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.PerceptionDef;

/**
 * A trooper is a man, not a machine (G0086): he turns at a speed and fires only once he is on his aim, his own shots
 * err, and something behind him past his all-round range he notices only on a chance keyed to him and it.
 *
 * <p>The base fixture switches all of it off, so these tests switch on only what each is about.
 */
class ManNotMachineTest {

    private static final float MIDDLE = 50f;

    private static final float CORNER = 95f;

    private static final float TURN_DEGREES_PER_SECOND = 240f;

    private static final float FIRE_ARC_DEGREES = 20f;

    private static final float AIM_ERROR_DEGREES = 6f;

    /** Behind him and inside his all-round range, so he notices it whichever way he faces. */
    private static final float RIGHT_BEHIND = 6f;

    /** Behind him, past his all-round range and inside his sight. */
    private static final float WELL_BEHIND = 25f;

    /** In front of him, where a shot of his reaches before anything else happens. */
    private static final float IN_FRONT = 15f;

    private static final int CERTAIN_PERCENT = 100;

    private static final Offset<Float> CLOSE = Offset.offset(1e-3f);

    /** Further than any shot flies in one tick: one nearer where it left than this was fired this tick. */
    private static final float FRESH = 2f;

    /** Enough to stand every shot of a test. */
    private static final int TOUGH = 100_000;

    @Test
    void turnsOntoWhatHeNoticesBehindHimAtHisTurnSpeedRatherThanAtOnce() {
        Simulation simulation = world(trooper -> trooper.withTurnDegreesPerSecond(TURN_DEGREES_PER_SECOND));
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE - RIGHT_BEHIND, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);

        simulation.step();

        float turned = (float) StrictMath.toRadians(
                TURN_DEGREES_PER_SECOND * simulation.config().tickSeconds());
        Trooper man = trooper.getComponent(Trooper.class);
        assertThat(man.mode).isEqualTo(Trooper.Mode.FIGHT);
        assertThat(man.lookX).isCloseTo((float) StrictMath.cos(turned), CLOSE);
        assertThat(Math.abs(man.lookY)).isCloseTo((float) StrictMath.sin(turned), CLOSE);
    }

    /** Pillar 5: the facing drawn is the one he has turned to, not the one he is turning towards. */
    @Test
    void isDrawnFacingTheWayHeHasTurnedSoFar() {
        Simulation simulation = world(trooper -> trooper.withTurnDegreesPerSecond(TURN_DEGREES_PER_SECOND));
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE - RIGHT_BEHIND, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);

        steps(simulation, 5);

        Trooper man = trooper.getComponent(Trooper.class);
        Facing facing = trooper.getComponent(Facing.class);
        assertThat(facing.x).isCloseTo(man.lookX, CLOSE);
        assertThat(facing.y).isCloseTo(man.lookY, CLOSE);
        assertThat(facing.x).isGreaterThan(0f);
    }

    @Test
    void holdsHisFireUntilHeHasTurnedOntoWhatHeAimsAt() {
        Simulation simulation = world(trooper ->
                trooper.withTurnDegreesPerSecond(TURN_DEGREES_PER_SECOND).withFireArcDegrees(FIRE_ARC_DEGREES));
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE - RIGHT_BEHIND, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);
        // Half round at his speed, short of his fire arc.
        int halfTurn = ticksIn((180f - FIRE_ARC_DEGREES) / TURN_DEGREES_PER_SECOND);

        boolean firedTurning = firesWithin(simulation, halfTurn - 2);
        boolean firedOnIt = firesWithin(simulation, ticksIn(0.5f));

        assertThat(firedTurning).isFalse();
        assertThat(firedOnIt).isTrue();
    }

    @Test
    void firesAtOnceAtWhatIsAlreadyInFrontOfHim() {
        Simulation simulation = world(trooper ->
                trooper.withTurnDegreesPerSecond(TURN_DEGREES_PER_SECOND).withFireArcDegrees(FIRE_ARC_DEGREES));
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE + IN_FRONT, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);

        steps(simulation, 2);

        assertThat(count(simulation, Projectile.class)).isPositive();
    }

    @Test
    void sendsEachShotOfHisOwnUpToHisAimErrorEitherSideOfWhereHeAims() {
        Simulation simulation = world(trooper -> trooper.withAimErrorDegrees(AIM_ERROR_DEGREES));
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        trooper.getComponent(Weapon.class).halfSpreadRadians = 0f;
        simulation.spawnEnemy(MIDDLE + IN_FRONT, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);
        float widest = 0f;

        for (int tick = 0; tick < ticksIn(6f); tick++) {
            simulation.step();
            widest = Math.max(widest, widestOffHisAim(simulation, trooper));
        }

        float bound = (float) StrictMath.sin(StrictMath.toRadians(AIM_ERROR_DEGREES));
        assertThat(widest).isPositive().isLessThanOrEqualTo(bound + 1e-4f);
    }

    /** On the man, not the gun: the commander carries the same sidearm and it stays true. */
    @Test
    void leavesTheCommandersShotsTrueWhateverATroopersAimError() {
        Simulation simulation = world(trooper -> trooper.withAimErrorDegrees(AIM_ERROR_DEGREES));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        commander.getComponent(Weapon.class).halfSpreadRadians = 0f;
        float widest = 0f;

        for (int tick = 0; tick < ticksIn(3f); tick++) {
            simulation.step(new CommanderInput(0f, 0f, MIDDLE + IN_FRONT, MIDDLE, true, false));
            widest = Math.max(widest, widestOff(simulation));
        }

        assertThat(count(simulation, Projectile.class)).isPositive();
        assertThat(widest).isZero();
    }

    @Test
    void noticesAnAlienWellBehindHimOnAYesForTheTwoOfThem() {
        Simulation simulation = world(trooper -> trooper.withNoticeBehindPercent(CERTAIN_PERCENT));
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE - WELL_BEHIND, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);

        simulation.step();

        assertThat(trooper.getComponent(Trooper.class).mode).isEqualTo(Trooper.Mode.FIGHT);
    }

    @Test
    void stillWillNotSeeThroughRockWhatHeWouldNoticeBehindHim() {
        Simulation simulation = on(
                world(trooper -> trooper.withNoticeBehindPercent(CERTAIN_PERCENT))
                        .config(),
                ground().rect(10, 0, 1, 20, Terrain.ROCK).build());
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(56f, MIDDLE);
        simulation.spawnEnemy(56f - WELL_BEHIND, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);

        simulation.step();

        assertThat(trooper.getComponent(Trooper.class).mode).isNotEqualTo(Trooper.Mode.FIGHT);
    }

    /**
     * Aliens that notice nobody and stand where they were put, and outlast the test: the trooper does everything.
     */
    private static Simulation world(UnaryOperator<SimConfig.TrooperConfig> change) {
        SimConfig base = config();
        return new Simulation(everyStrain(
                base.withTrooper(change.apply(base.trooper())),
                strain -> strain.withContactDamage(0)
                        .withHealth(TOUGH)
                        .withPerception(PerceptionDef.allRound(false, true, 0f, 0f, 0f))));
    }

    /** Whether a shot of anybody's is in the air on any of the next so many ticks. */
    private static boolean firesWithin(Simulation simulation, int ticks) {
        boolean fired = false;
        for (int tick = 0; tick < ticks; tick++) {
            simulation.step();
            fired |= count(simulation, Projectile.class) > 0;
        }
        return fired;
    }

    /**
     * How far off the line from him to where he is aiming the widest shot fired this tick is going, as the sine of the
     * angle between them. Only this tick's: an older shot was aimed at where the target stood then.
     */
    private static float widestOffHisAim(Simulation simulation, Entity trooper) {
        Trooper man = trooper.getComponent(Trooper.class);
        Position from = trooper.getComponent(Position.class);
        ImmutableArray<Entity> shots = simulation
                .engine()
                .getEntitiesFor(Family.all(Projectile.class, Velocity.class, Position.class)
                        .get());
        float widest = 0f;
        for (int i = 0; i < shots.size(); i++) {
            Entity shot = shots.get(i);
            Projectile projectile = shot.getComponent(Projectile.class);
            Velocity velocity = shot.getComponent(Velocity.class);
            Position at = shot.getComponent(Position.class);
            float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
            float flownX = at.x - projectile.originX;
            float flownY = at.y - projectile.originY;
            if (speed <= 0f || flownX * flownX + flownY * flownY > FRESH * FRESH) {
                continue;
            }
            float aimX = man.targetX - from.x;
            float aimY = man.targetY - from.y;
            float aim = (float) Math.sqrt(aimX * aimX + aimY * aimY);
            widest = Math.max(widest, Math.abs(velocity.x * aimY - velocity.y * aimX) / (speed * aim));
        }
        return widest;
    }

    /** How far off the line along x the widest shot in the air is going, as the sine of its angle. */
    private static float widestOff(Simulation simulation) {
        ImmutableArray<Entity> shots = simulation
                .engine()
                .getEntitiesFor(Family.all(Projectile.class, Velocity.class).get());
        float widest = 0f;
        for (int i = 0; i < shots.size(); i++) {
            Velocity velocity = shots.get(i).getComponent(Velocity.class);
            float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
            if (speed > 0f) {
                widest = Math.max(widest, Math.abs(velocity.y) / speed);
            }
        }
        return widest;
    }
}
