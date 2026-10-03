package pl.mzebrows.pwt.sim.system.commander;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TURRET_COOLDOWN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TURRET_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TURRET_MUZZLE_OFFSET;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Drag;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * A gun you get on rather than pick up: it is offered to whoever is standing next to it on the one
 * action key, it fires from two muzzles in turn, and it has to be brought to bear rather than
 * pointed. What is under test is the getting on and off, and what the gun does that a carried one
 * does not.
 */
class TurretTest {

    private static final float MIDDLE = 50f;

    /** Comfortably inside arm's length once both bodies are counted. */
    private static final float BESIDE = 4f;

    private static final float FAR = 30f;

    /** Ten units is plenty: aim is a direction, so how far the point is cannot matter. */
    private static final float AHEAD = 10f;

    private static final Offset<Float> CLOSE = Offset.offset(1e-3f);

    private static final CommanderInput STILL = CommanderInput.IDLE;

    private static final CommanderInput PRESSING = STILL.acting(true);

    @Test
    void offersTheTurretToAManStandingBesideIt() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(STILL);

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.MAN_TURRET);
    }

    @Test
    void offersNothingForATurretOutOfReach() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTurret(MIDDLE + FAR, MIDDLE);

        simulation.step(STILL);

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.NONE);
    }

    @Test
    void putsHimOnItWhenHePresses() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);

        assertThat(commander.getComponent(Crew.class).aboard()).isTrue();
        assertThat(turret.getComponent(Turret.class).crewed).isTrue();
    }

    /** One key, and what it means depends on where he is: on the gun, it is the way off it (G0021). */
    @Test
    void takesHimOffWhenHePressesAgain() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        simulation.step(STILL);
        simulation.step(PRESSING);

        assertThat(commander.getComponent(Crew.class).aboard()).isFalse();
        assertThat(turret.getComponent(Turret.class).crewed).isFalse();
    }

    @Test
    void keepsHimOnItWhileTheKeyStaysHeld() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        steps(simulation, ticksIn(1f), PRESSING);

        assertThat(commander.getComponent(Crew.class).aboard()).isTrue();
    }

    @Test
    void firesTheTurretsGunRatherThanTheOneInHisHands() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        steps(simulation, ticksIn(TURRET_COOLDOWN), firingAhead());

        assertThat(count(simulation, Projectile.class)).isPositive();
        assertThat(projectiles(simulation).first().getComponent(Appearance.class).look)
                .isEqualTo(Look.TURRET_SHOT);
    }

    @Test
    void alternatesBetweenItsTwoMuzzles() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        steps(simulation, ticksIn(TURRET_COOLDOWN) + 2, firingAhead());

        ImmutableArray<Entity> shots = projectiles(simulation);
        assertThat(shots.size()).isEqualTo(2);
        assertThat(shots.get(0).getComponent(Position.class).y).isCloseTo(MIDDLE - TURRET_MUZZLE_OFFSET, CLOSE);
        assertThat(shots.get(1).getComponent(Position.class).y).isCloseTo(MIDDLE + TURRET_MUZZLE_OFFSET, CLOSE);
    }

    /** A gun on a mount is heavy: it comes round at a rate, which is what makes flanking one work. */
    @Test
    void swingsOntoANewBearingRatherThanSnappingToIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        simulation.step(aimingAt(MIDDLE + BESIDE, MIDDLE + AHEAD, false));

        Turret gun = turret.getComponent(Turret.class);
        assertThat(gun.aimY).isPositive();
        assertThat(gun.aimX).isGreaterThan(0.9f);
    }

    @Test
    void comesAllTheWayRoundEventually() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        steps(simulation, ticksIn(6f), aimingAt(MIDDLE + BESIDE, MIDDLE + AHEAD, false));

        assertThat(turret.getComponent(Turret.class).aimY).isGreaterThan(0.99f);
    }

    @Test
    void turnsSlowerWhileItIsFiring() {
        Simulation firing = simulation();
        firing.spawnCommander(MIDDLE, MIDDLE);
        Entity firingGun = firing.spawnTurret(MIDDLE + BESIDE, MIDDLE);
        Simulation holding = simulation();
        holding.spawnCommander(MIDDLE, MIDDLE);
        Entity holdingGun = holding.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        firing.step(PRESSING);
        steps(firing, ticksIn(1f), aimingAt(MIDDLE + BESIDE, MIDDLE + AHEAD, true));
        holding.step(PRESSING);
        steps(holding, ticksIn(1f), aimingAt(MIDDLE + BESIDE, MIDDLE + AHEAD, false));

        assertThat(firingGun.getComponent(Turret.class).aimY).isLessThan(holdingGun.getComponent(Turret.class).aimY);
    }

    /** Nothing drives a man who is on one, the player included. He is at the gun, not beside it. */
    @Test
    void drivesNobodyWhoIsOnATurret() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        steps(simulation, ticksIn(1f), walkingAway());

        Position at = commander.getComponent(Position.class);
        assertThat(at.x).isCloseTo(MIDDLE + BESIDE, CLOSE);
        assertThat(at.y).isCloseTo(MIDDLE, CLOSE);
    }

    @Test
    void putsHimOffItWhenItIsDestroyed() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        shoot(turret);
        steps(simulation, 3, STILL);

        assertThat(commander.getComponent(Crew.class).aboard()).isFalse();
    }

    /** He is standing in the middle of it when it goes up, and a blast is on nobody's side. */
    @Test
    void catchesTheCrewInTheBlastWhenItGoesUp() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        shoot(turret);
        steps(simulation, 4, STILL);

        assertThat(commander.getComponent(Vitality.class).begun).isTrue();
    }

    @Test
    void haulsAnEmptyTurretAsReadilyAsABarrel() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(STILL.dragging(true));

        assertThat(commander.getComponent(Drag.class).active()).isTrue();
    }

    @Test
    void willNotTakeHoldOfATurretSomebodyIsOnIt() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        turret.getComponent(Turret.class).crewed = true;
        simulation.step(STILL.dragging(true));

        assertThat(commander.getComponent(Drag.class).active()).isFalse();
    }

    @Test
    void showsAManOnATurretAsAboardItAndTheTurretAsCrewed() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);

        assertThat(commander.getComponent(Appearance.class).state).isEqualTo(Appearance.State.ABOARD);
        assertThat(turret.getComponent(Appearance.class).state).isEqualTo(Appearance.State.CREWED);
    }

    private static CommanderInput aimingAt(float x, float y, boolean firing) {
        return new CommanderInput(0f, 0f, x, y, firing, false);
    }

    private static CommanderInput firingAhead() {
        return aimingAt(MIDDLE + BESIDE + AHEAD, MIDDLE, true);
    }

    private static CommanderInput walkingAway() {
        return new CommanderInput(1f, 0f, MIDDLE + BESIDE + AHEAD, MIDDLE, false, false);
    }

    private static ImmutableArray<Entity> projectiles(Simulation simulation) {
        return simulation.engine().getEntitiesFor(Family.all(Projectile.class).get());
    }

    private static void shoot(Entity entity) {
        entity.getComponent(Damage.class).land(TURRET_HEALTH, Harm.DOWN);
    }
}
