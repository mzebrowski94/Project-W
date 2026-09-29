package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.Objective;

class GoalLineTest {

    private static final float HOLD = 1f;
    private static final float PULSE_HZ = 2f;
    private static final float FRAME = 1f / 60f;

    @Test
    void namesTheFirstUnmetObjective() {
        Readout readout = requiring(Objective.CLEAR_ENEMIES, Objective.CAPTURE_POINTS);
        readout.pointsTaken = 2;
        readout.pointsTotal = 4;
        GoalLine line = line();

        line.update(readout, FRAME);

        assertThat(line.shown()).isEqualTo(Goal.CAPTURE_POINTS);
        assertThat(line.first()).isEqualTo(2);
        assertThat(line.second()).isEqualTo(4);
    }

    @Test
    void movesToTheNextObjectiveOnceTheFirstIsMet() {
        Readout readout = requiring(Objective.CLEAR_ENEMIES, Objective.CAPTURE_POINTS);
        readout.objectivesMet = Readout.bit(Objective.CAPTURE_POINTS);

        assertThat(GoalLine.resolve(readout)).isEqualTo(Goal.CLEAR_ENEMIES);
    }

    @Test
    void saysCompletedWhenEveryRequiredObjectiveIsMet() {
        Readout readout = requiring(Objective.CLEAR_ENEMIES, Objective.HOLD_POINT);
        readout.objectivesMet = readout.objectivesRequired;

        assertThat(GoalLine.resolve(readout)).isEqualTo(Goal.COMPLETE);
    }

    /** A lost level says so over everything else, objectives met or not (G0055). */
    @Test
    void saysFailedOverEverythingOnceTheLevelIsLost() {
        Readout readout = requiring(Objective.CLEAR_ENEMIES);
        readout.objectivesMet = readout.objectivesRequired;
        readout.failed = true;

        assertThat(GoalLine.resolve(readout)).isEqualTo(Goal.FAILED);
    }

    @Test
    void saysFailedEvenOnALevelWithoutObjectives() {
        Readout readout = new Readout();
        readout.failed = true;

        assertThat(GoalLine.resolve(readout)).isEqualTo(Goal.FAILED);
    }

    @Test
    void showsNothingForALevelWithoutObjectives() {
        assertThat(GoalLine.resolve(new Readout())).isEqualTo(Goal.NONE);
    }

    @Test
    void goesBackToTheGoalWhenAnObjectiveIsLostAgain() {
        Readout readout = requiring(Objective.CLEAR_ENEMIES);
        readout.objectivesMet = readout.objectivesRequired;
        GoalLine line = line();
        line.update(readout, FRAME);

        readout.objectivesMet = 0;
        run(line, readout, HOLD + FRAME);

        assertThat(line.shown()).isEqualTo(Goal.CLEAR_ENEMIES);
    }

    @Test
    void holdsATextForItsMinimumTime() {
        Readout readout = requiring(Objective.CLEAR_ENEMIES);
        GoalLine line = line();
        line.update(readout, FRAME);

        readout.objectivesMet = readout.objectivesRequired;
        run(line, readout, HOLD * 0.5f);

        assertThat(line.shown()).isEqualTo(Goal.CLEAR_ENEMIES);
    }

    @Test
    void takesANewWorldsGoalAtOnce() {
        Readout readout = requiring(Objective.CLEAR_ENEMIES);
        GoalLine line = line();
        line.update(readout, FRAME);

        readout.worldSerial++;
        readout.objectivesRequired = Readout.bit(Objective.CAPTURE_POINTS);
        line.update(readout, FRAME);

        assertThat(line.shown()).isEqualTo(Goal.CAPTURE_POINTS);
    }

    @Test
    void updatesItsCountsAtOnce() {
        Readout readout = requiring(Objective.CAPTURE_POINTS);
        readout.pointsTotal = 4;
        GoalLine line = line();
        line.update(readout, FRAME);
        int revision = line.revision();

        readout.pointsTaken = 1;
        line.update(readout, FRAME);

        assertThat(line.first()).isEqualTo(1);
        assertThat(line.revision()).isNotEqualTo(revision);
    }

    @Test
    void pulsesTwiceWhenItCompletesThenHoldsStill() {
        Readout readout = requiring(Objective.CLEAR_ENEMIES);
        GoalLine line = line();
        line.update(readout, FRAME);
        run(line, readout, HOLD);
        readout.objectivesMet = readout.objectivesRequired;
        line.update(readout, FRAME);

        int dark = 0;
        boolean wasBright = true;
        for (int frame = 0; frame < 240; frame++) {
            line.update(readout, FRAME);
            if (wasBright && !line.bright()) {
                dark++;
            }
            wasBright = line.bright();
        }

        assertThat(dark).isEqualTo(2);
        assertThat(line.bright()).isTrue();
    }

    @Test
    void holdsStillWithFlashesTurnedOff() {
        Readout readout = requiring(Objective.CLEAR_ENEMIES);
        GoalLine line = new GoalLine(HOLD, PULSE_HZ, 0f);
        line.update(readout, FRAME);
        run(line, readout, HOLD);
        readout.objectivesMet = readout.objectivesRequired;

        for (int frame = 0; frame < 120; frame++) {
            line.update(readout, FRAME);
            assertThat(line.bright()).isTrue();
        }
    }

    private static GoalLine line() {
        return new GoalLine(HOLD, PULSE_HZ, 1f);
    }

    private static void run(GoalLine line, Readout readout, float seconds) {
        for (float t = 0f; t < seconds; t += FRAME) {
            line.update(readout, FRAME);
        }
    }

    private static Readout requiring(Objective... objectives) {
        Readout readout = new Readout();
        for (Objective objective : objectives) {
            readout.objectivesRequired |= Readout.bit(objective);
        }
        return readout;
    }
}
