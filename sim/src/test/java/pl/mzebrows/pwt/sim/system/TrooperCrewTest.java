package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BRUTE_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_SIGHT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TURRET_QUIET_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.willing;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A trooper deciding a gun is worth more than the walk it was on. It goes for one when there is
 * something it could shoot from it, shoots things far beyond anything it could see for itself, and
 * gets off again once there has been nothing to shoot at for a while - because a man sitting on a
 * quiet gun is a man missing from the squad.
 */
class TrooperCrewTest {

    /**
     * Straight behind the gun, so that what walks at him walks down its line of fire.
     *
     * <p>Nothing here is about him - he never fires - but where he stands decides where the aliens
     * walk, and a test about a gun hitting something should not also be a test about leading a
     * target across its front.
     */
    private static final float BEHIND = 20f;

    private static final float MIDDLE = 50f;

    /** Far enough that the trooper has to walk, near enough that it is worth going. */
    private static final float NEARBY = 8f;

    /** Well beyond anything a trooper can see for itself, and well inside what the gun can reach. */
    private static final float FAR = TROOPER_SIGHT + 12f;

    @Test
    void goesForAFreeGunWhenThereIsSomethingItCouldShootFromIt() {
        Simulation simulation = keen();
        Entity trooper = deploy(simulation);
        simulation.spawnTurret(MIDDLE + NEARBY, MIDDLE);
        simulation.spawnUnit(UnitKind.GRUNT, MIDDLE + FAR, MIDDLE);

        steps(simulation, 2);

        assertThat(trooper.getComponent(Trooper.class).mode).isEqualTo(Trooper.Mode.CREW);
    }

    @Test
    void getsOnItWhenItArrives() {
        Simulation simulation = keen();
        Entity trooper = deploy(simulation);
        Entity turret = simulation.spawnTurret(MIDDLE + NEARBY, MIDDLE);
        threat(simulation);

        steps(simulation, ticksIn(0.5f));

        assertThat(trooper.getComponent(Crew.class).aboard()).isTrue();
        assertThat(turret.getComponent(Turret.class).crewed).isTrue();
    }

    /**
     * 2b: the gun reaches far further than the man on it can see, and that is the point of it.
     *
     * <p>Asserted by the grunt being dead rather than by counting shots in the air: nothing else in
     * the world could have killed it. The trooper's own gun is silent while it is aboard, the
     * commander never fires, and the grunt was spawned well outside anything the trooper can see.
     */
    @Test
    void firesAtSomethingWellBeyondItsOwnSight() {
        Simulation simulation = keen();
        deploy(simulation);
        simulation.spawnTurret(MIDDLE + NEARBY, MIDDLE);
        simulation.spawnUnit(UnitKind.GRUNT, MIDDLE + FAR, MIDDLE);

        steps(simulation, ticksIn(3f));

        assertThat(countLiving(simulation, Enemy.class)).isZero();
    }

    /** 2c: a man on a gun nothing is coming at is a man missing from the squad. */
    @Test
    void getsOffAgainOnceThereHasBeenNothingToShootAtForAWhile() {
        Simulation simulation = keen();
        Entity trooper = deploy(simulation);
        simulation.spawnTurret(MIDDLE + NEARBY, MIDDLE);
        Entity brute = threat(simulation);

        steps(simulation, ticksIn(0.5f));
        boolean gotOn = trooper.getComponent(Crew.class).aboard();
        kill(brute);
        steps(simulation, ticksIn(TURRET_QUIET_SECONDS + 1f));

        assertThat(gotOn).isTrue();
        assertThat(trooper.getComponent(Crew.class).aboard()).isFalse();
    }

    @Test
    void staysOnItWhileSomethingIsStillInRange() {
        Simulation simulation = keen();
        Entity trooper = deploy(simulation);
        simulation.spawnTurret(MIDDLE + NEARBY, MIDDLE);
        threat(simulation);

        steps(simulation, ticksIn(TURRET_QUIET_SECONDS + 0.5f));

        assertThat(trooper.getComponent(Crew.class).aboard()).isTrue();
    }

    @Test
    void leavesAGunSomebodyElseIsAlreadyOnAlone() {
        Simulation simulation = keen();
        Entity first = deploy(simulation);
        Entity second = simulation.spawnTrooper(MIDDLE, MIDDLE + 2f);
        simulation.spawnTurret(MIDDLE + NEARBY, MIDDLE);
        threat(simulation);

        steps(simulation, ticksIn(1f));

        assertThat(aboard(first) ^ aboard(second)).isTrue();
    }

    /**
     * The one somebody else got to first goes back to what it was doing.
     *
     * <p>Both men set off for the same free gun, because both saw it free. The moment one is on it the
     * other has nowhere to be - and what it was walking towards is a place it decided on for a reason
     * that has stopped being true. Left alone it walks the rest of the way and stands over an occupied
     * gun for the rest of the level, which is what this caught.
     */
    @Test
    void goesBackToWhatItWasDoingWhenSomebodyElseGetsTheGunFirst() {
        Simulation simulation = keen();
        Entity first = deploy(simulation);
        Entity second = simulation.spawnTrooper(MIDDLE, MIDDLE + 2f);
        Entity turret = simulation.spawnTurret(MIDDLE + NEARBY, MIDDLE);
        threat(simulation);

        stepUntilSomebodyIsOn(simulation, first, second);
        simulation.step();

        Entity beaten = aboard(first) ? second : first;
        Trooper mind = beaten.getComponent(Trooper.class);
        assertThat(turret.getComponent(Turret.class).crewed).isTrue();
        assertThat(mind.mode).isNotEqualTo(Trooper.Mode.CREW);
        assertThat(headingForTheGun(mind, turret)).isFalse();
    }

    /** Nothing occasional happens unless the dice say so, and that includes this. */
    @Test
    void neverGoesForOneWhenItIsSwitchedOff() {
        Simulation simulation = simulation();
        Entity trooper = deploy(simulation);
        simulation.spawnTurret(MIDDLE + NEARBY, MIDDLE);
        simulation.spawnUnit(UnitKind.GRUNT, MIDDLE + FAR, MIDDLE);

        steps(simulation, ticksIn(3f));

        assertThat(trooper.getComponent(Crew.class).aboard()).isFalse();
    }

    @Test
    void willNotGoForOneWithNothingToShootAtFromIt() {
        Simulation simulation = keen();
        Entity trooper = deploy(simulation);
        simulation.spawnTurret(MIDDLE + NEARBY, MIDDLE);

        steps(simulation, ticksIn(2f));

        assertThat(trooper.getComponent(Crew.class).aboard()).isFalse();
    }

    private static Simulation keen() {
        return new Simulation(willing(AiChance.MAN_A_TURRET));
    }

    /**
     * Something worth manning a gun for, far enough out that only the gun can reach it.
     *
     * <p>A brute rather than a grunt wherever a test needs the threat to outlive the walk to the
     * gun: the turret kills a grunt in four shots, and a test about a man sitting on a gun should
     * not quietly become a test about how fast it empties.
     */
    private static Entity threat(Simulation simulation) {
        return simulation.spawnUnit(UnitKind.BRUTE, MIDDLE + FAR, MIDDLE);
    }

    /** A commander straight behind the gun, and one trooper standing where it is worth a walk. */
    private static Entity deploy(Simulation simulation) {
        simulation.spawnCommander(BEHIND, MIDDLE);
        return simulation.spawnTrooper(MIDDLE, MIDDLE);
    }

    /**
     * Runs until one of them is on the gun, so the tick after is the one under test.
     *
     * <p>Waiting a fixed second instead would let the loser walk all the way over, arrive, and pick a
     * new place to be on its own - which hides the bug rather than testing it.
     */
    private static void stepUntilSomebodyIsOn(Simulation simulation, Entity first, Entity second) {
        for (int tick = 0; tick < ticksIn(3f); tick++) {
            simulation.step();
            if (aboard(first) || aboard(second)) {
                return;
            }
        }
    }

    /** Whether this man is still walking at that gun, whoever is on it. */
    private static boolean headingForTheGun(Trooper mind, Entity turret) {
        if (!mind.hasTarget) {
            return false;
        }
        Position where = turret.getComponent(Position.class);
        float dx = mind.targetX - where.x;
        float dy = mind.targetY - where.y;
        return dx * dx + dy * dy < 1f;
    }

    private static boolean aboard(Entity trooper) {
        return trooper.getComponent(Crew.class).aboard();
    }

    private static void kill(Entity entity) {
        entity.getComponent(Damage.class).land(BRUTE_HEALTH, Harm.KILL);
    }
}
