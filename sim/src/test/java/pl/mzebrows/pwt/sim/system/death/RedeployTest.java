package pl.mzebrows.pwt.sim.system.death;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CASUALTY_BLEED_OUT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CASUALTY_STABILISE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_BLEED_OUT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_REDEPLOY_AFTER;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESERVE_START;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * A commander who is down may give up the rest of his bleed-out once enough of it has run - redeploy (G0109) - and he
 * then goes exactly as running out would: the same death, the same throes and wait, the same man from the reserve.
 * Before that point his key does nothing, and his friends have the whole of his bleed-out to reach him.
 */
class RedeployTest {

    private static final float MIDDLE = 50f;

    /** Comfortably inside arm's length once both bodies are counted. */
    private static final float BESIDE = 2f;

    private static final CommanderInput PRESSING = CommanderInput.IDLE.redeploying(true);

    /** From the killing blow to being back on his feet: the body lingers, and then he is away. */
    private static final int RETURN = ticksIn(DEATH_THROES + RESPAWN_DELAY) + 3;

    @Test
    void doesNothingOnAPressBeforeHisWindowIsOpen() {
        Simulation simulation = new Simulation(config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);

        putDown(commander);
        steps(simulation, ticksIn(COMMANDER_REDEPLOY_AFTER) - 5);
        simulation.step(PRESSING);
        simulation.step();

        assertThat(Life.casualty(commander)).isTrue();
    }

    /** Only a press counts: a key still held from before his window opened has to be let go and pressed again. */
    @Test
    void doesNothingForAKeyHeldFromBeforeHisWindowOpened() {
        Simulation simulation = new Simulation(config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);

        putDown(commander);
        steps(simulation, ticksIn(COMMANDER_REDEPLOY_AFTER) + 5, PRESSING);

        assertThat(Life.casualty(commander)).isTrue();

        simulation.step();
        simulation.step(PRESSING);

        assertThat(Life.dying(commander)).isTrue();
    }

    @Test
    void endsHisBleedOutOnAPressOnceHisWindowIsOpen() {
        Simulation simulation = new Simulation(config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);

        putDown(commander);
        steps(simulation, ticksIn(COMMANDER_REDEPLOY_AFTER) + 2);
        simulation.step(PRESSING);

        assertThat(commander.getComponent(Dying.class).begun).isTrue();
        assertThat(commander.getComponent(Vitality.class).remaining).isPositive();
    }

    /** The ordinary death path: the throes, the respawn delay, and a man from the reserve, as running out costs. */
    @Test
    void bringsHimBackAfterTheUsualThroesAndDelayAtTheReservesCost() {
        Simulation simulation = new Simulation(config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);

        putDown(commander);
        steps(simulation, ticksIn(COMMANDER_REDEPLOY_AFTER) + 2);
        simulation.step(PRESSING);
        steps(simulation, ticksIn(DEATH_THROES + RESPAWN_DELAY) - 3);

        assertThat(count(simulation, Commander.class)).isZero();

        steps(simulation, RETURN);

        assertThat(count(simulation, Commander.class)).isEqualTo(1);
        assertThat(simulation.level().reserve).isEqualTo(RESERVE_START - 1);
    }

    /** Past the redeploy point he is still a man on the ground: a friend who reaches him in time puts him up. */
    @Test
    void letsAFriendStabiliseHimAfterHisWindowHasOpened() {
        SimConfig config = config();
        Simulation simulation = new Simulation(config.withCommander(config.commander()
                .withBleedOutSeconds(CASUALTY_STABILISE * 4f)
                .withRedeployAfterSeconds(CASUALTY_STABILISE)));
        Entity down = simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        simulation.spawnCommander(MIDDLE + BESIDE, MIDDLE, 1);

        putDown(down);
        steps(simulation, ticksIn(CASUALTY_STABILISE * 2f));
        assertThat(down.getComponent(Vitality.class).redeployOpen()).isTrue();
        for (int i = 0; i < ticksIn(CASUALTY_STABILISE) + 2; i++) {
            simulation.step(new PlayerInputs().set(1, CommanderInput.IDLE.acting(true)));
        }

        assertThat(down.getComponent(Vitality.class).begun).isFalse();
        assertThat(Life.dying(down)).isFalse();
    }

    /**
     * His window counts what of his bleed-out has run, never the ticks since he went down, so a clock stopped - a haul
     * stops a trooper's (sim.casualty.draggedBleedFactor) - holds it shut. No commander can be hauled today; this is the
     * rule that stands if one ever can.
     */
    @Test
    void opensHisWindowOnlyOnceThatMuchOfHisBleedOutHasRun() {
        Vitality vitality = new Vitality().set(10, 1, 1, 1, 0, 1f, 1f).redeploysAfter(5);

        vitality.begin();
        for (int i = 0; i < 8; i++) {
            vitality.bleed(0);
        }
        assertThat(vitality.redeployOpen()).isFalse();

        for (int i = 0; i < 4; i++) {
            vitality.bleed(Vitality.TICK);
        }
        assertThat(vitality.redeployOpen()).isFalse();

        vitality.bleed(Vitality.TICK);
        assertThat(vitality.redeployOpen()).isTrue();
    }

    @Test
    void bleedsHimOutAsBeforeWhenHeNeverPresses() {
        Simulation simulation = new Simulation(config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);

        putDown(commander);
        steps(simulation, ticksIn(COMMANDER_BLEED_OUT) - 2);
        assertThat(Life.casualty(commander)).isTrue();

        steps(simulation, 4);
        assertThat(Life.dying(commander)).isTrue();
    }

    @Test
    void neverOpensATroopersWindow() {
        Simulation simulation = new Simulation(config());
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);

        putDown(trooper);
        steps(simulation, ticksIn(CASUALTY_BLEED_OUT) - 5);

        assertThat(trooper.getComponent(Vitality.class).redeployOpen()).isFalse();
        assertThat(trooper.getComponent(Appearance.class).redeployOpen).isFalse();
        assertThat(Life.casualty(trooper)).isTrue();
    }

    /** The down mark is his alone, for as long as he is down (G0109); the window is published for his own prompt. */
    @Test
    void marksACommanderWhoIsDownAndSaysWhenHisWindowOpens() {
        Simulation simulation = new Simulation(config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 10f, MIDDLE);
        Appearance appearance = commander.getComponent(Appearance.class);

        simulation.step();
        assertThat(appearance.downMarked).isFalse();

        putDown(commander);
        putDown(trooper);
        simulation.step();
        assertThat(appearance.downMarked).isTrue();
        assertThat(appearance.redeployOpen).isFalse();
        assertThat(trooper.getComponent(Appearance.class).downMarked).isFalse();

        steps(simulation, ticksIn(COMMANDER_REDEPLOY_AFTER) + 1);
        assertThat(appearance.redeployOpen).isTrue();

        simulation.step(PRESSING);
        assertThat(appearance.downMarked).isFalse();
        assertThat(appearance.redeployOpen).isFalse();
    }

    @Test
    void refusesARedeployPointThatIsNotBeforeTheEndOfHisBleedOut() {
        SimConfig.CommanderConfig commander = config().commander();

        assertThatThrownBy(() -> commander.withRedeployAfterSeconds(commander.bleedOutSeconds()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> commander.withRedeployAfterSeconds(0f)).isInstanceOf(IllegalArgumentException.class);
    }

    /** What ships: twice the old five seconds, and the old five seconds is where he may redeploy. */
    @Test
    void shipsATenSecondBleedOutWithItsWindowHalfWay() {
        SimConfig.CommanderConfig shipped = SimConfig.defaults().commander();

        assertThat(shipped.bleedOutSeconds()).isEqualTo(10f);
        assertThat(shipped.redeployAfterSeconds()).isEqualTo(5f);
    }

    private static void putDown(Entity entity) {
        entity.getComponent(Damage.class).land(0, Harm.DOWN);
    }
}
