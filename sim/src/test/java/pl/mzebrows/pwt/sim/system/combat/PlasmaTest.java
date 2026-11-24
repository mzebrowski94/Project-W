package pl.mzebrows.pwt.sim.system.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WorldCodec;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Orb;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The plasma cannon (G0128): one orb a pull, which flies straight beaming at up to three aliens in its reach - the
 * laser's kind of beam, sparing our men - and goes off in a small blast on what it touches, at its flight's end, or
 * once its beams have landed its power.
 */
class PlasmaTest {

    private static final float X = 40f;

    private static final float Y = 50f;

    /** How closely a beam shot's line must point at what it was fired at. */
    private static final float AIMED = 0.9999f;

    // ---- the gun ----

    @Test
    void throwsOneOrbAPullAndRunsItsHeatToTheStop() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X, Y);
        arm(simulation, commander);

        steps(simulation, ticksIn(0.5f), new CommanderInput(0f, 0f, X + 30f, Y, true, false));

        assertThat(count(simulation, Orb.class)).isOne();
        assertThat(commander.getComponent(Heat.class).locked).isTrue();
    }

    /**
     * Walking after his own throw on the tick he throws, he never touches the orb: it leaves clear of him by its gun's
     * muzzle clearance, past where the solver sweeps it from (G0128). At the shipped pace, his and the orb's.
     */
    @Test
    void throwsTheOrbClearOfHimWhileHeWalksAfterIt() {
        Simulation simulation = new Simulation(SimConfig.defaults(), ground().build());
        Entity commander = simulation.spawnCommander(X, Y);
        arm(simulation, commander);

        steps(simulation, ticksIn(0.3f), new CommanderInput(1f, 0f, X + 60f, Y, true, false));

        assertThat(count(simulation, Orb.class)).isOne();
        assertThat(count(simulation, Blast.class)).isZero();
        assertThat(unhurt(commander)).isTrue();
    }

    // ---- the beams ----

    @Test
    void beamsAtTheThreeNearestAliensInItsReachAndNoOthers() {
        Simulation simulation = simulation();
        Entity sixth = simulation.spawnEnemy(X, Y + 6f);
        Entity eighth = simulation.spawnEnemy(X, Y - 8f);
        Entity tenth = simulation.spawnEnemy(X, Y + 10f);
        Entity twelfth = simulation.spawnEnemy(X, Y - 12f);
        Entity outOfReach = simulation.spawnEnemy(X, Y + 20f);
        Entity orb = orb(simulation, 1f, 0f);

        simulation.step();

        List<Entity> beams = beamShots(simulation);
        assertThat(beams).hasSize(3);
        assertThat(aimedAt(orb, beams, sixth)).isTrue();
        assertThat(aimedAt(orb, beams, eighth)).isTrue();
        assertThat(aimedAt(orb, beams, tenth)).isTrue();
        assertThat(aimedAt(orb, beams, twelfth)).isFalse();
        assertThat(aimedAt(orb, beams, outOfReach)).isFalse();
    }

    @Test
    void neverBeamsAtAMan() {
        Simulation simulation = simulation();
        simulation.spawnCommander(X, Y + 40f);
        simulation.spawnTrooper(X, Y + 6f);
        orb(simulation, 1f, 0f);

        steps(simulation, ticksIn(0.3f));

        assertThat(beamShots(simulation)).isEmpty();
    }

    @Test
    void stopsABeamOnAnAlienInTheWayOfTheOneItIsAimedAt() {
        Simulation simulation = simulation();
        Entity inTheWay = simulation.spawnEnemy(X + 6f, Y);
        Entity behind = simulation.spawnEnemy(X + 12f, Y);
        orb(simulation, -1f, 0f);

        steps(simulation, ticksIn(0.12f));

        assertThat(hurt(inTheWay)).isTrue();
        assertThat(hurt(behind)).isFalse();
    }

    @Test
    void stopsABeamOnSceneryInTheWay() {
        Simulation simulation = simulation();
        simulation.spawnObstacle(X + 6f, Y);
        Entity behind = simulation.spawnEnemy(X + 12f, Y);
        orb(simulation, -1f, 0f);

        steps(simulation, ticksIn(0.12f));

        assertThat(hurt(behind)).isFalse();
    }

    @Test
    void passesAManInTheLineOfItsBeamHarmlessly() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X + 6f, Y);
        Entity alien = simulation.spawnEnemy(X + 12f, Y);
        orb(simulation, -1f, 0f);

        steps(simulation, ticksIn(0.12f));

        assertThat(unhurt(commander)).isTrue();
        assertThat(hurt(alien)).isTrue();
    }

    // ---- going off ----

    @Test
    void goesOffOnTouchingAnAlienInABlastOfItsOwnDamage() {
        Simulation simulation = simulation();
        simulation.spawnEnemy(X + 6f, Y);
        orb(simulation, 1f, 0f);

        stepUntilABlast(simulation, ticksIn(0.4f));

        assertThat(count(simulation, Orb.class)).isZero();
        assertThat(blast(simulation).damage)
                .isEqualTo(simulation.config().weapon(WeaponKind.PLASMA).blastDamage());
    }

    @Test
    void goesOffOnTouchingAManOfOursAndItsBlastWoundsHim() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X + 6f, Y);
        orb(simulation, 1f, 0f);

        steps(simulation, ticksIn(0.4f));

        assertThat(count(simulation, Orb.class)).isZero();
        assertThat(commander.getComponent(Vitality.class).wounds).isEqualTo(1);
        assertThat(commander.getComponent(Vitality.class).begun).isFalse();
    }

    @Test
    void goesOffAtTheEndOfItsFlightWithNothingInTheWay() {
        Simulation simulation = simulation();
        WeaponDef gun = simulation.config().weapon(WeaponKind.PLASMA);
        // From the west edge, so its whole flight lies inside the arena rather than off its far side.
        float from = simulation.map().worldWidth() - gun.projectileRange() - gun.projectileRadius() * 4f;
        simulation.spawnProjectile(from, Y, 1f, 0f, WeaponKind.PLASMA, Side.PLAYER);
        float flight = gun.projectileRange() / gun.projectileSpeed();

        steps(simulation, ticksIn(flight * 0.9f));
        assertThat(count(simulation, Orb.class)).isOne();
        steps(simulation, ticksIn(flight * 0.1f) + 3);

        assertThat(count(simulation, Orb.class)).isZero();
        assertThat(count(simulation, Blast.class)).isOne();
    }

    /** Two beam shots' worth of power: spent on the alien beside its path, it goes off long before its flight ends. */
    @Test
    void goesOffOnceItsBeamsHaveLandedItsPower() {
        SimConfig config =
                withPower(simulation().config().weapon(WeaponKind.PLASMA_BEAM).damage() * 2);
        Simulation simulation = new Simulation(config);
        simulation.spawnEnemy(X, Y + 8f);
        orb(simulation, 1f, 0f);
        WeaponDef gun = simulation.config().weapon(WeaponKind.PLASMA);
        float flight = gun.projectileRange() / gun.projectileSpeed();

        stepUntilABlast(simulation, ticksIn(flight * 0.5f));

        assertThat(count(simulation, Orb.class)).isZero();
        assertThat(count(simulation, Blast.class)).isOne();
    }

    @Test
    void spendsItsPowerByWhatItsBeamsLandOnTheLivingAlone() {
        Simulation simulation = simulation();
        simulation.spawnEnemy(X, Y + 8f);
        Entity orb = orb(simulation, 1f, 0f);
        int shot = simulation.config().weapon(WeaponKind.PLASMA_BEAM).damage();
        Orb thrown = orb.getComponent(Orb.class);

        // The first beam's flight, and short of the second's.
        steps(simulation, ticksIn(0.09f));

        assertThat(thrown.powerAtTheThrow - thrown.power).isEqualTo(shot);
    }

    @Test
    void carriesWhatIsLeftOfItsPowerThroughASnapshot() {
        Simulation simulation = simulation();
        simulation.spawnEnemy(X, Y + 8f);
        orb(simulation, 1f, 0f);
        steps(simulation, ticksIn(0.09f));
        byte[] before = WorldCodec.snapshot(simulation);
        Orb thrown = simulation
                .engine()
                .getEntitiesFor(Family.all(Orb.class).get())
                .first()
                .getComponent(Orb.class);

        thrown.power--;

        assertThat(WorldCodec.differences(before, WorldCodec.snapshot(simulation), 10))
                .anySatisfy(line -> assertThat(line).contains("Orb.power"));
    }

    // ---- helpers ----

    private static void arm(Simulation simulation, Entity commander) {
        commander
                .getComponent(Weapon.class)
                .set(
                        WeaponKind.PLASMA,
                        simulation.config().weapon(WeaponKind.PLASMA),
                        simulation.config().tickSeconds());
    }

    /** An orb thrown from where it stands, along this way, on the players' side. */
    private static Entity orb(Simulation simulation, float directionX, float directionY) {
        return simulation.spawnProjectile(X, Y, directionX, directionY, WeaponKind.PLASMA, Side.PLAYER);
    }

    private static SimConfig withPower(int power) {
        SimConfig config = config();
        return config.withWeapons(config.weapons()
                .map(WeaponKind.PLASMA, gun -> gun.withBeam(gun.beam().withPower(power))));
    }

    private static List<Entity> beamShots(Simulation simulation) {
        ImmutableArray<Entity> shots =
                simulation.engine().getEntitiesFor(Family.all(Projectile.class).get());
        List<Entity> beams = new ArrayList<>();
        for (Entity shot : shots) {
            if (shot.getComponent(Projectile.class).beamedFrom != Handle.NOBODY) {
                beams.add(shot);
            }
        }
        return beams;
    }

    /** Whether one of these shots, fired from the orb's middle, flies straight at this alien. */
    private static boolean aimedAt(Entity orb, List<Entity> beams, Entity alien) {
        Position from = orb.getComponent(Position.class);
        Position to = alien.getComponent(Position.class);
        float x = to.x - from.x;
        float y = to.y - from.y;
        float length = (float) Math.sqrt(x * x + y * y);
        for (Entity shot : beams) {
            Velocity flight = shot.getComponent(Velocity.class);
            float speed = (float) Math.sqrt(flight.x * flight.x + flight.y * flight.y);
            if ((flight.x * x + flight.y * y) / (speed * length) > AIMED) {
                return true;
            }
        }
        return false;
    }

    /** Steps until a blast stands on the field, at most this many ticks. */
    private static void stepUntilABlast(Simulation simulation, int most) {
        for (int tick = 0; tick < most && count(simulation, Blast.class) == 0; tick++) {
            simulation.step();
        }
    }

    private static boolean hurt(Entity alien) {
        Health health = alien.getComponent(Health.class);
        return health.current < health.max;
    }

    private static Blast blast(Simulation simulation) {
        ImmutableArray<Entity> blasts =
                simulation.engine().getEntitiesFor(Family.all(Blast.class).get());
        assertThat(blasts.size()).isOne();
        return blasts.first().getComponent(Blast.class);
    }
}
