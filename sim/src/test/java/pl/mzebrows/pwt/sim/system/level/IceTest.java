package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY_MIDDLE_X;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_BOTTOM;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.godMode;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.leapConfig;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.walledAtADoorway;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.query.Reach;

/**
 * Ice is solid ground until broken (ADR 0025): a door nobody opens, standing across a way - so it holds and stops shots
 * as a shut door does, every shot from the squad's side chips it, a blast breaks it outright and a leap lands on it as
 * on a door, the swarm's own shots do nothing, and a hunter whose way runs through it breaks it rather than going round
 * (G0116). Broken, it is gone.
 */
class IceTest {

    /** Below the wall, where the shooter and the creature stand. */
    private static final float OUTSIDE = 25f;

    /** Beyond it. */
    private static final float INSIDE = 75f;

    @Test
    void holdsAManAndStopsAShotAsAShutDoorDoes() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnIce(DOORWAY);
        Entity commander = simulation.spawnCommander(DOORWAY_MIDDLE_X, 40f);
        simulation.spawnProjectile(DOORWAY_MIDDLE_X, 45f, 0f, 1f, Side.ALIEN);

        steps(simulation, ticksIn(1f), new CommanderInput(0f, 1f, 0f, 0f, false, false));

        assertThat(commander.getComponent(Position.class).y)
                .as("nobody past it")
                .isLessThan(WALL_BOTTOM);
        assertThat(count(simulation, Projectile.class))
                .as("the shot stopped at its face")
                .isZero();
        assertThat(simulation.solid().passableAtTile(DOORWAY.col(), DOORWAY.row()))
                .isFalse();
    }

    @Test
    void isChippedToNothingByTheSquadsShotsAndThenIsOpenGround() {
        Simulation simulation = on(walledAtADoorway());
        Entity ice = simulation.spawnIce(DOORWAY);
        int health = simulation.config().ice().health();
        int damage = simulation.config().weapon(WeaponKind.SIDEARM).damage();
        int shots = (health + damage - 1) / damage;

        for (int shot = 1; shot < shots; shot++) {
            fireAt(simulation, Side.PLAYER);
        }
        assertThat(ice.getComponent(Health.class).current)
                .as("one shot short")
                .isEqualTo(health - (shots - 1) * damage);
        assertThat(simulation.solid().passableAtTile(DOORWAY.col(), DOORWAY.row()))
                .isFalse();
        fireAt(simulation, Side.PLAYER);
        steps(simulation, 2);

        assertThat(count(simulation, Door.class)).as("broken, gone").isZero();
        assertThat(simulation.solid().passableAtTile(DOORWAY.col(), DOORWAY.row()))
                .isTrue();
    }

    @Test
    void takesNothingFromTheSwarmsShots() {
        Simulation simulation = on(walledAtADoorway());
        Entity ice = simulation.spawnIce(DOORWAY);

        for (int shot = 0; shot < 12; shot++) {
            fireAt(simulation, Side.ALIEN);
        }

        assertThat(ice.getComponent(Health.class).current)
                .isEqualTo(simulation.config().ice().health());
    }

    @Test
    void breaksOutrightUnderAGrenadesBlast() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnIce(DOORWAY);
        simulation.spawnGrenade(DOORWAY_MIDDLE_X, 30f, 0f, 1f);

        steps(simulation, ticksIn(3f));

        assertThat(count(simulation, Door.class)).isZero();
    }

    @Test
    void isBrokenByALeapingAlienAsADoorIs() {
        SimConfig leaping =
                everyStrain(config(), strain -> strain.withContactDamage(0).withLeap(leapConfig()));
        Simulation simulation = on(leaping, walledAtADoorway());
        simulation.spawnCommander(DOORWAY_MIDDLE_X, INSIDE);
        godMode(simulation);
        Entity ice = simulation.spawnIce(DOORWAY);
        simulation.spawnEnemy(DOORWAY_MIDDLE_X, OUTSIDE);
        int full = ice.getComponent(Health.class).current;

        int tick = 0;
        while (ice.getComponent(Health.class).current == full && tick++ < ticksIn(5f)) {
            simulation.step();
        }
        assertThat(ice.getComponent(Health.class).current)
                .as("a door's leap off it")
                .isEqualTo(full - simulation.config().door().leapDamage());
        steps(simulation, ticksIn(30f));

        assertThat(count(simulation, Door.class)).as("broken in the end").isZero();
    }

    @Test
    void isOpenedByNobody() {
        Simulation simulation = on(walledAtADoorway());
        Entity ice = simulation.spawnIce(DOORWAY);
        simulation.spawnCommander(DOORWAY_MIDDLE_X, WALL_BOTTOM - 3f);

        steps(
                simulation,
                ticksIn(simulation.config().door().openSeconds() * 2f),
                new CommanderInput(0f, 0f, 0f, 1f, false, false, false, true, false, false));

        assertThat(Reach.offers(ice, ActionKind.OPEN_DOOR)).isFalse();
        assertThat(ice.getComponent(Door.class).open).isFalse();
        assertThat(count(simulation, Door.class)).isEqualTo(1);
    }

    @Test
    void showsHowNearItIsToBreaking() {
        Simulation simulation = on(walledAtADoorway());
        Entity ice = simulation.spawnIce(DOORWAY);
        steps(simulation, 1);
        assertThat(ice.getComponent(Appearance.class).wear).as("whole").isZero();

        fireAt(simulation, Side.PLAYER);
        float once = ice.getComponent(Appearance.class).wear;
        fireAt(simulation, Side.PLAYER);

        assertThat(once).as("chipped").isPositive();
        assertThat(ice.getComponent(Appearance.class).wear).as("chipped again").isGreaterThan(once);
    }

    /**
     * Ice is ground to a hunter's route (G0116): its shortest way runs through the ice, so it walks up to it and leaps at
     * it - even with a way round the wall open - and never takes the way round while the ice stands.
     */
    @Test
    void aHunterBreaksIceInItsWayEvenWhenThereIsAWayRound() {
        SimConfig leaping =
                everyStrain(config(), strain -> strain.withContactDamage(0).withLeap(leapConfig()));
        Simulation simulation = on(leaping, walledWithAWayRound());
        Entity ice = simulation.spawnIce(DOORWAY);
        simulation.spawnCommander(DOORWAY_MIDDLE_X, INSIDE);
        godMode(simulation);
        Entity creature = simulation.spawnEnemy(DOORWAY_MIDDLE_X, OUTSIDE);
        int full = ice.getComponent(Health.class).current;

        int tick = 0;
        while (ice.getComponent(Health.class).current == full && tick++ < ticksIn(10f)) {
            simulation.step();
            assertThat(creature.getComponent(Position.class).y)
                    .as("never round the wall while the ice stands")
                    .isLessThan(WALL_BOTTOM);
        }

        assertThat(ice.getComponent(Health.class).current)
                .as("leapt at, in its way")
                .isEqualTo(full - simulation.config().door().leapDamage());
    }

    /** A shut door is not ice (G0075 holds): with a way round, a hunter takes it and leaves the door alone. */
    @Test
    void aHunterStillGoesRoundAShutDoorWhenThereIsAWayRound() {
        SimConfig leaping =
                everyStrain(config(), strain -> strain.withContactDamage(0).withLeap(leapConfig()));
        Simulation simulation = on(leaping, walledWithAWayRound());
        Entity door = simulation.spawnDoor(DOORWAY);
        simulation.spawnCommander(DOORWAY_MIDDLE_X, INSIDE);
        godMode(simulation);
        Entity creature = simulation.spawnEnemy(DOORWAY_MIDDLE_X, OUTSIDE);
        int full = door.getComponent(Health.class).current;

        steps(simulation, ticksIn(20f));

        assertThat(creature.getComponent(Position.class).y)
                .as("past the wall, by the way round")
                .isGreaterThan(WALL_BOTTOM + TILE);
        assertThat(door.getComponent(Health.class).current)
                .as("the door untouched")
                .isEqualTo(full);
    }

    /** A shot from {@code side} flown into the ice from below, and the ticks until it has landed. */
    private static void fireAt(Simulation simulation, Side side) {
        Entity shot = simulation.spawnProjectile(DOORWAY_MIDDLE_X, WALL_BOTTOM - 4f, 0f, 1f, WeaponKind.SIDEARM, side);
        for (int tick = 0;
                tick < ticksIn(1f) && !shot.isScheduledForRemoval() && shot.getComponent(Projectile.class) != null;
                tick++) {
            simulation.step();
        }
        steps(simulation, 1);
    }

    /** {@link pl.mzebrows.pwt.sim.AlphaWorlds#walledAtADoorway()}'s wall, with a second gap at its far end. */
    private static TileMap walledWithAWayRound() {
        TileRect round = new TileRect(17, DOORWAY.row(), 3, 1);
        return ground().rect(0, DOORWAY.row(), DOORWAY.col(), 1, Terrain.ROCK)
                .rect(DOORWAY.endCol(), DOORWAY.row(), round.col() - DOORWAY.endCol(), 1, Terrain.ROCK)
                .build();
    }
}
