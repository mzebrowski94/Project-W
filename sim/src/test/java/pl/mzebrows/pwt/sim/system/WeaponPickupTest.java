package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RAILGUN_DAMAGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * A weapon pickup replaces what the commander is carrying and nothing else - there is no slot, no
 * inventory and nothing kept between lives. That answers the open question in
 * {@code docs/design/README.md}, so these tests are what stops the answer drifting.
 */
class WeaponPickupTest {

    private static final float X = 50f;
    private static final float Y = 50f;
    private static final Offset<Float> TOLERANCE = Offset.offset(1e-5f);

    @Test
    void putsANewGunInTheCommandersHands() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.ARMOURY, X, Y);

        simulation.step();

        assertThat(commander.getComponent(Weapon.class).kind).isEqualTo(WeaponKind.RAILGUN);
    }

    @Test
    void replacesEveryNumberOfTheGunItReplaces() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.ARMOURY, X, Y);

        simulation.step();

        assertThat(commander.getComponent(Weapon.class).damage).isCloseTo(RAILGUN_DAMAGE, TOLERANCE);
    }

    /** A gun the player cannot tell he is holding is a gun he is guessing about (pillar 5). */
    @Test
    void showsWhichGunHeIsHoldingInWhatItFires() {
        Simulation simulation = simulation();
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.ARMOURY, X, Y);

        simulation.step();
        simulation.step(new CommanderInput(0f, 0f, X + 10f, Y, true, false));

        assertThat(projectile(simulation).getComponent(Appearance.class).look).isEqualTo(Look.SLUG);
    }

    /** A commander who has just walked to a cache should not then be told to wait for it. */
    @Test
    void handsHimTheNewGunReadyToFire() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X, Y);
        commander.getComponent(Weapon.class).remainingSeconds = 10f;
        simulation.spawnPickup(PickupKind.ARMOURY, X, Y);

        simulation.step();

        assertThat(commander.getComponent(Weapon.class).ready()).isTrue();
    }

    /** Nothing is owned. A gun is found during a life and lost with it (pillar 3). */
    @Test
    void givesHimBackHisSidearmWhenHeComesBack() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.ARMOURY, X, Y);
        simulation.step();

        kill(commander);
        steps(simulation, ticksIn(DEATH_THROES + RESPAWN_DELAY) + 4);

        assertThat(commander(simulation).getComponent(Weapon.class).kind).isEqualTo(WeaponKind.SIDEARM);
    }

    /** The contest: a cache the commander does not reach first is a meal rather than a gun. */
    @Test
    void feedsAnEnemyThatReachesTheCacheFirst() {
        Simulation simulation = simulation();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        simulation.spawnPickup(PickupKind.ARMOURY, X, Y);

        simulation.step();

        assertThat(grunt.getComponent(Feed.class).count).isEqualTo(1);
        assertThat(grunt.getComponent(Weapon.class).kind).isNotEqualTo(WeaponKind.RAILGUN);
    }

    private static void kill(Entity entity) {
        entity.getComponent(Damage.class).add(COMMANDER_HEALTH, 0f);
        entity.getComponent(Invulnerable.class).remainingSeconds = 0f;
    }

    private static Entity commander(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Commander.class).get())
                .first();
    }

    private static Entity projectile(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Projectile.class).get())
                .first();
    }
}
