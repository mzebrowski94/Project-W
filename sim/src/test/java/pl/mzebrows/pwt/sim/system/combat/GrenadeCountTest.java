package pl.mzebrows.pwt.sim.system.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GRENADE_COOLDOWN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GRENADE_MOST;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GRENADE_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Thrown;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * The commander carries his grenades (G0077): a count, at most his most, spent one a throw, and with
 * none left the key throws nothing.
 */
class GrenadeCountTest {

    private static final float AT_X = 30f;

    private static final float AT_Y = 52f;

    /** Long enough for every grenade he carries to have been thrown, and one more after that. */
    private static final int MORE_THAN_HE_CARRIES = ticksIn(GRENADE_COOLDOWN) * (GRENADE_MOST + 2);

    @Test
    void startsTheLevelCarryingHisMost() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(AT_X, AT_Y);

        assertThat(simulation.grenades(commander)).isEqualTo(GRENADE_MOST);
        assertThat(simulation.grenadeMost(commander)).isEqualTo(GRENADE_MOST);
    }

    @Test
    void spendsOneAThrow() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(AT_X, AT_Y);

        simulation.step(throwing());

        assertThat(simulation.grenades(commander)).isEqualTo(GRENADE_MOST - 1);
    }

    @Test
    void throwsNothingOnceHeHasNoneLeft() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(AT_X, AT_Y);

        int thrown = throwsWhileHeHolds(simulation, MORE_THAN_HE_CARRIES);

        assertThat(thrown).isEqualTo(GRENADE_MOST);
        assertThat(simulation.grenades(commander)).isZero();
    }

    /** A return is a new man from the reserve (G0055), and he comes onto the field carrying his most. */
    @Test
    void comesBackFullAfterHeIsKilled() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(AT_X, AT_Y);
        throwsWhileHeHolds(simulation, MORE_THAN_HE_CARRIES);

        kill(commander);
        steps(simulation, ticksIn(DEATH_THROES + RESPAWN_DELAY) + 4);

        assertThat(simulation.grenades(commander(simulation))).isEqualTo(GRENADE_MOST);
    }

    /** Down is not dead: what he had left is still his when he is back on his feet. */
    @Test
    void keepsWhatHeHasLeftWhileHeIsDown() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(AT_X, AT_Y);
        simulation.step(throwing());

        commander.getComponent(Damage.class).land(0, Harm.DOWN);
        commander.getComponent(Invulnerable.class).remainingTicks = 0;
        steps(simulation, 2);

        assertThat(simulation.grenades(commander)).isEqualTo(GRENADE_MOST - 1);
    }

    /**
     * How many grenades leave his hand over this many ticks of holding the throw key. A grenade is flown before
     * anything is thrown in a tick, so one thrown this tick has flown none of its range yet - and an entity is
     * pooled, so it is told apart by that rather than by who it is.
     */
    private static int throwsWhileHeHolds(Simulation simulation, int ticks) {
        int thrown = 0;
        for (int i = 0; i < ticks; i++) {
            simulation.step(throwing());
            for (Entity grenade :
                    simulation.engine().getEntitiesFor(Family.all(Thrown.class).get())) {
                if (grenade.getComponent(Thrown.class).remainingDistance >= GRENADE_RANGE) {
                    thrown++;
                }
            }
        }
        return thrown;
    }

    private static void kill(Entity entity) {
        entity.getComponent(Damage.class).land(0, Harm.KILL);
        entity.getComponent(Invulnerable.class).remainingTicks = 0;
    }

    private static Entity commander(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Commander.class).get())
                .first();
    }

    /** One tick of input asking for a throw along the x axis. */
    private static CommanderInput throwing() {
        return new CommanderInput(0f, 0f, AT_X + 10f, AT_Y, false, false, true, false, false, false);
    }
}
