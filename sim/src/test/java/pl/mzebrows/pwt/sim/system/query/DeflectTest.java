package pl.mzebrows.pwt.sim.system.query;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A deflect (G0086): a chance the leap declares, keyed once per leap and man - the same answer however often it is
 * asked during one leap, a fresh one for the next - and only for a man it comes at inside his arc, his strike in hand
 * or already swung at this leap.
 */
class DeflectTest {

    private static final float MIDDLE = 50f;

    private static final float CHANCE = 0.3f;

    private static final int LEAPS = 2000;

    private static final long SEED = 1L;

    /** Far enough off that a leap from there at a mate passes well clear of the man in the middle. */
    private static final float BESIDE = 6f;

    /** Longer than any leap is in the air. */
    private static final int SPENT_TICKS = 600;

    @Test
    void deflectsAboutTheLeapsChanceOfItsLeaps() {
        Simulation simulation = harmless();
        Entity man = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity ant = leaping(simulation, CHANCE);
        int deflected = 0;

        for (int leap = 0; leap < LEAPS; leap++) {
            ant.getComponent(Leap.class).leaps = leap;
            if (Deflect.deflects(SEED, ant, man)) {
                deflected++;
            }
        }

        assertThat(deflected / (float) LEAPS).isCloseTo(CHANCE, Offset.offset(0.03f));
    }

    @Test
    void givesOneLeapTheSameAnswerEveryTimeItIsAsked() {
        Simulation simulation = harmless();
        Entity man = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity ant = leaping(simulation, CHANCE);
        boolean first = Deflect.deflects(SEED, ant, man);

        boolean again = Deflect.deflects(SEED, ant, man);

        assertThat(again).isEqualTo(first);
    }

    @Test
    void neverDeflectsALeapFromBehindHim() {
        Simulation simulation = harmless();
        Entity man = simulation.spawnTrooper(MIDDLE, MIDDLE);
        man.getComponent(Trooper.class).look(-1f, 0f);
        Entity ant = leaping(simulation, 1f);

        assertThat(Deflect.deflects(SEED, ant, man)).isFalse();
    }

    @Test
    void neverDeflectsALeapOnTheGround() {
        Simulation simulation = harmless();
        Entity man = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity ant = leaping(simulation, 1f);
        ant.getComponent(Leap.class).phase = Leap.Phase.HESITATING;

        assertThat(Deflect.deflects(SEED, ant, man)).isFalse();
    }

    @Test
    void neverDeflectsForTheCommander() {
        Simulation simulation = harmless();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity ant = leaping(simulation, 1f);

        assertThat(Deflect.deflects(SEED, ant, commander)).isFalse();
    }

    @Test
    void neverDeflectsWhileHisStrikeIsSpent() {
        Simulation simulation = harmless();
        Entity man = simulation.spawnTrooper(MIDDLE, MIDDLE);
        man.getComponent(Melee.class).remainingTicks = SPENT_TICKS;
        Entity ant = leaping(simulation, 1f);

        assertThat(Deflect.deflects(SEED, ant, man)).isFalse();
    }

    @Test
    void stillDeflectsTheLeapHeHasAlreadySwungAt() {
        Simulation simulation = harmless();
        Entity man = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity ant = leaping(simulation, 1f);
        swungAt(ant, man);
        man.getComponent(Melee.class).beginSwing(MIDDLE + 3f, MIDDLE);

        assertThat(Deflect.deflects(SEED, ant, man)).isTrue();
    }

    @Test
    void neverDeflectsTheNextLeapOnTheSwingHeSpentOnThisOne() {
        Simulation simulation = harmless();
        Entity man = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity ant = leaping(simulation, 1f);
        swungAt(ant, man);
        man.getComponent(Melee.class).beginSwing(MIDDLE + 3f, MIDDLE);

        ant.getComponent(Leap.class).leaps++;

        assertThat(Deflect.deflects(SEED, ant, man)).isFalse();
    }

    @Test
    void neverDeflectsALeapAimedAtAMate() {
        Simulation simulation = harmless();
        Entity man = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE + BESIDE);
        Entity ant = leaping(simulation, 1f);
        ant.getComponent(Position.class).set(MIDDLE + BESIDE, MIDDLE);
        Leap leap = ant.getComponent(Leap.class);
        leap.directionX = 0f;
        leap.directionY = 1f;

        assertThat(Deflect.deflects(SEED, ant, man)).isFalse();
    }

    /** What the squad records as he swings at a leap to deflect it. */
    private static void swungAt(Entity ant, Entity man) {
        Trooper trooper = man.getComponent(Trooper.class);
        trooper.metLeaper = ant.getComponent(Handle.class).id;
        trooper.metLeap = ant.getComponent(Leap.class).leaps;
    }

    /** An ant in the air in front of a man at the middle looking along x, leaping at him, deflected at this chance. */
    private static Entity leaping(Simulation simulation, float chance) {
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE + 3f, MIDDLE);
        Leap leap = ant.getComponent(Leap.class);
        leap.phase = Leap.Phase.AIRBORNE;
        leap.directionX = -1f;
        leap.directionY = 0f;
        leap.deflectChance = chance;
        return ant;
    }
}
