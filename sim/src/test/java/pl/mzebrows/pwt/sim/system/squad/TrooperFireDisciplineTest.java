package pl.mzebrows.pwt.sim.system.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.NEST_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A trooper fires past his mates, never through them (G0084): a mate on his feet whose edge his shot would cross
 * fouls his line, and he aims across his target for a line past the mate, takes another target, or holds his fire.
 * The commander's own shots are his own business.
 */
class TrooperFireDisciplineTest {

    private static final float LANE = 50f;
    private static final float CORNER = 5f;
    private static final float SHOOTER_X = 40f;
    private static final float TARGET_X = 60f;

    /** Long enough for an unfouled man to have put several rounds into a nest twenty off. */
    private static final int A_FEW_SECONDS = ticksIn(2f);

    @Test
    void holdsHisFireWithAMateStandingBetweenHimAndHisOnlyTarget() {
        Simulation simulation = harmless();
        simulation.spawnCommander((SHOOTER_X + TARGET_X) / 2f, LANE);
        Entity shooter = facingEast(simulation.spawnTrooper(SHOOTER_X, LANE));
        Entity nest = simulation.spawnNest(UnitKind.GRUNT, TARGET_X, LANE);

        steps(simulation, A_FEW_SECONDS);

        assertThat(shooter.getComponent(Trooper.class).mode).isEqualTo(Trooper.Mode.FIGHT);
        assertThat(nest.getComponent(Health.class).current).isEqualTo(NEST_HEALTH);
    }

    /** Standing a little off the line, the mate fouls its middle but not its edge: he aims past him and hits. */
    @Test
    void firesPastAMateStandingJustOffTheLineByAimingAcrossTheTarget() {
        Simulation simulation = harmless();
        simulation.spawnCommander(TARGET_X - 4f, LANE + 1.2f);
        facingEast(simulation.spawnTrooper(SHOOTER_X, LANE));
        Entity nest = simulation.spawnNest(UnitKind.GRUNT, TARGET_X, LANE);

        steps(simulation, A_FEW_SECONDS);

        assertThat(hurt(nest)).isTrue();
    }

    /** Fouled on the nearer target, he takes one further off that he has a clear line to. */
    @Test
    void takesAnotherTargetHeHasAClearLineToWhenTheNearerOneIsFouled() {
        Simulation simulation = harmless();
        simulation.spawnCommander((SHOOTER_X + TARGET_X) / 2f, LANE);
        Entity shooter = facingEast(simulation.spawnTrooper(SHOOTER_X, LANE));
        simulation.spawnNest(UnitKind.GRUNT, TARGET_X, LANE);
        simulation.spawnNest(UnitKind.GRUNT, TARGET_X + 2f, LANE + 10f);

        simulation.step();

        Trooper mind = shooter.getComponent(Trooper.class);
        assertThat(mind.targetY).isGreaterThan(LANE + 5f);
        assertThat(mind.fouled).isFalse();
    }

    /** A mate who is down fouls nothing: the shot goes over a man lying on the ground. */
    @Test
    void firesOverAMateWhoIsDown() {
        Simulation simulation = harmless();
        simulation.spawnCommander(CORNER, CORNER);
        Entity down = simulation.spawnTrooper((SHOOTER_X + TARGET_X) / 2f, LANE);
        down.getComponent(Damage.class).land(0, Harm.DOWN);
        facingEast(simulation.spawnTrooper(SHOOTER_X, LANE));
        Entity nest = simulation.spawnNest(UnitKind.GRUNT, TARGET_X, LANE);

        steps(simulation, A_FEW_SECONDS);

        assertThat(hurt(nest)).isTrue();
    }

    /** A mate beyond the target fouls nothing: the shot stops at what it was fired at. */
    @Test
    void firesAtATargetWithAMateStandingBeyondIt() {
        Simulation simulation = harmless();
        simulation.spawnCommander(TARGET_X + 6f, LANE);
        facingEast(simulation.spawnTrooper(SHOOTER_X, LANE));
        Entity nest = simulation.spawnNest(UnitKind.GRUNT, TARGET_X, LANE);

        steps(simulation, A_FEW_SECONDS);

        assertThat(hurt(nest)).isTrue();
    }

    /** The commander fires where he likes: his rounds pass a trooper standing in front of him as ever. */
    @Test
    void leavesTheCommandersOwnShotsAlone() {
        Simulation simulation = harmless();
        simulation.spawnCommander(SHOOTER_X, LANE);
        Entity inFront = facingWest(simulation.spawnTrooper((SHOOTER_X + TARGET_X) / 2f, LANE));
        Entity nest = simulation.spawnNest(UnitKind.GRUNT, TARGET_X, LANE);

        boolean heFought = false;
        for (int tick = 0; tick < A_FEW_SECONDS && !hurt(nest); tick++) {
            simulation.step(new CommanderInput(0f, 0f, TARGET_X, LANE, true, false));
            heFought |= inFront.getComponent(Trooper.class).mode == Trooper.Mode.FIGHT;
        }

        assertThat(heFought).as("the trooper in front never shot it himself").isFalse();
        assertThat(hurt(nest)).isTrue();
    }

    /** Hit at least once: below its full health, or already destroyed and gone. */
    private static boolean hurt(Entity nest) {
        Health health = nest.getComponent(Health.class);
        return health == null || health.current < NEST_HEALTH;
    }

    private static Entity facingEast(Entity trooper) {
        trooper.getComponent(Trooper.class).look(1f, 0f);
        return trooper;
    }

    private static Entity facingWest(Entity trooper) {
        trooper.getComponent(Trooper.class).look(-1f, 0f);
        return trooper;
    }
}
