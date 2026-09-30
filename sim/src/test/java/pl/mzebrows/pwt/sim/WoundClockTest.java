package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.upgrade.Effect;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;
import pl.mzebrows.pwt.sim.upgrade.Stat;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * A man's clocks - his bleed-out, the stabilise that puts him back on his feet, the mend that clears his
 * wounds - count whole ticks, converted once from the seconds the config gives ({@link Ticks#of}), so each
 * takes exactly as long as its number says. Run on what ships: a clock that adds up fractions of a second
 * is on time or a tick late depending on the number it is handed.
 */
class WoundClockTest {

    private static final float MIDDLE = 50f;

    /** Comfortably inside arm's length once both bodies are counted. */
    private static final float BESIDE = 2f;

    /** Far past any clock here: a loop that gets this far has a clock that never runs out. */
    private static final int NEVER = 10_000;

    private static final CommanderInput WORKING = CommanderInput.IDLE.acting(true);

    @Test
    void putsAManBackOnHisFeetAfterExactlyTheTicksOfWorkTheStabiliseSays() {
        SimConfig config = SimConfig.defaults();

        assertThat(ticksToStabilise(config, false, 0))
                .isEqualTo(Ticks.of(config.casualty().stabiliseSeconds(), config.tickSeconds()));
    }

    @Test
    void takesAManWhoWasHauledExactlyTheTicksOfHisLongerStabilise() {
        SimConfig config = SimConfig.defaults();
        float seconds = config.casualty().stabiliseSeconds() * config.casualty().hauledStabiliseFactor();

        assertThat(ticksToStabilise(config, true, 0)).isEqualTo(Ticks.of(seconds, config.tickSeconds()));
    }

    /** First Aid makes each tick of work worth more; the work still ends on the first tick that is enough. */
    @Test
    void takesFirstAidsShareOffTheStabiliseAndNotATickMore() {
        SimConfig config = SimConfig.defaults();
        int ranks = config.upgrades().of(UpgradeKind.FIRST_AID).ranks();
        int perTick = 100 + firstAidPercent(config) * ranks;
        int needed = Ticks.of(config.casualty().stabiliseSeconds(), config.tickSeconds()) * 100;

        assertThat(ticksToStabilise(config, false, ranks)).isEqualTo((needed + perTick - 1) / perTick);
    }

    @Test
    void bleedsATrooperOutAfterExactlyTheTicksHisBleedOutSays() {
        SimConfig config = SimConfig.defaults();
        Simulation simulation = new Simulation(config);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);

        trooper.getComponent(Vitality.class).begin();

        assertThat(ticksUntilHisThroes(simulation, trooper))
                .isEqualTo(Ticks.of(config.casualty().bleedOutSeconds(), config.tickSeconds()));
    }

    @Test
    void bleedsTheCommanderOutAfterExactlyTheTicksHisOwnBleedOutSays() {
        SimConfig config = SimConfig.defaults();
        Simulation simulation = new Simulation(config);
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);

        commander.getComponent(Vitality.class).begin();

        assertThat(ticksUntilHisThroes(simulation, commander))
                .isEqualTo(Ticks.of(config.commander().bleedOutSeconds(), config.tickSeconds()));
    }

    /** Counted from the tick of his last wound, that tick included. */
    @Test
    void mendsHisWoundsExactlyTheTicksSelfTreatmentSaysAfterTheLastOne() {
        SimConfig config = SimConfig.defaults();
        Simulation simulation = new Simulation(config);
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        DraftTest.ledger(simulation).held.add(new Upgrades.Held(UpgradeKind.SELF_TREATMENT, 0, 1));
        int seconds = new Modifiers(simulation.engine(), config)
                .mendSeconds(commander, config.wound().mendSeconds());

        commander.getComponent(Damage.class).add(1, 0, Harm.WOUND);

        int ticks = 0;
        do {
            simulation.step();
            ticks++;
        } while (commander.getComponent(Vitality.class).wounds > 0 && ticks < NEVER);
        assertThat(ticks).isEqualTo(Ticks.of(seconds, config.tickSeconds()));
    }

    /** The commander stands over him with the key held from the first tick; the ticks until he is up. */
    private static int ticksToStabilise(SimConfig config, boolean hauled, int firstAidRanks) {
        Simulation simulation = new Simulation(config);
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);
        if (firstAidRanks > 0) {
            DraftTest.ledger(simulation)
                    .held
                    .add(new Upgrades.Held(UpgradeKind.FIRST_AID, Upgrades.SHARED, firstAidRanks));
        }
        trooper.getComponent(Vitality.class).begin();
        trooper.getComponent(Dragged.class).hauled = hauled;

        int ticks = 0;
        do {
            simulation.step(WORKING);
            ticks++;
        } while (trooper.getComponent(Vitality.class).begun && ticks < NEVER);
        return ticks;
    }

    private static int ticksUntilHisThroes(Simulation simulation, Entity man) {
        int ticks = 0;
        do {
            simulation.step();
            ticks++;
        } while (!man.getComponent(Dying.class).begun && ticks < NEVER);
        return ticks;
    }

    private static int firstAidPercent(SimConfig config) {
        for (Effect effect : config.upgrades().of(UpgradeKind.FIRST_AID).effects()) {
            if (effect instanceof Effect.Scale scale && scale.stat() == Stat.STABILISE_SPEED) {
                return scale.percent();
            }
        }
        throw new IllegalStateException("First Aid no longer speeds the stabilise");
    }
}
