package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * What a gamepad is asking for. The decisions a pad needs and a mouse does not: a dead zone, a
 * remembered aim, and an edge on every button that means something once rather than continuously.
 */
class PadControlsTest {

    private static final Offset<Float> TOLERANCE = Offset.offset(1e-4f);
    private static final ControlsConfig CONTROLS = ControlsConfig.load();

    @Test
    void walksWhereTheLeftStickIsPushed() {
        CommanderInput input = new PadControls(CONTROLS).read(FakeGamepad.xbox().push(GamepadAxis.LEFT_X, 1f));

        assertThat(input.moveX()).isCloseTo(1f, TOLERANCE);
        assertThat(input.moveY()).isZero();
    }

    @Test
    void ignoresAStickResting() {
        float inside = CONTROLS.feel().moveDeadZone() * 0.5f;

        CommanderInput input = new PadControls(CONTROLS)
                .read(FakeGamepad.xbox().push(GamepadAxis.LEFT_X, inside).push(GamepadAxis.LEFT_Y, inside));

        assertThat(input.moveX()).isZero();
        assertThat(input.moveY()).isZero();
    }

    /**
     * The dead zone is taken off the <em>length</em>. A square one lets a diagonal past sooner than a
     * straight push, which players feel as the stick sticking to the axes.
     */
    @Test
    void takesTheDeadZoneOffTheLengthRatherThanOffEachAxis() {
        float justInside = CONTROLS.feel().moveDeadZone() * 0.8f;

        CommanderInput input = new PadControls(CONTROLS)
                .read(FakeGamepad.xbox().push(GamepadAxis.LEFT_X, justInside).push(GamepadAxis.LEFT_Y, justInside));

        assertThat(length(input.moveX(), input.moveY())).isPositive();
    }

    /** Aim is a direction and never a distance, so a half-pushed stick turns him as far as a full one. */
    @Test
    void sendsAnAimOfLengthOneHoweverHardTheStickIsPushed() {
        CommanderInput gentle =
                new PadControls(CONTROLS).read(FakeGamepad.xbox().push(GamepadAxis.RIGHT_X, 0.6f));
        CommanderInput hard = new PadControls(CONTROLS).read(FakeGamepad.xbox().push(GamepadAxis.RIGHT_X, 1f));

        assertThat(length(gentle.aimX(), gentle.aimY())).isCloseTo(1f, TOLERANCE);
        assertThat(gentle.aimX()).isCloseTo(hard.aimX(), TOLERANCE);
    }

    @Test
    void marksAPadsAimAsAnOffsetFromTheCommander() {
        CommanderInput input = new PadControls(CONTROLS).read(FakeGamepad.xbox().push(GamepadAxis.RIGHT_X, 1f));

        assertThat(input.aimRelative()).isTrue();
    }

    /** Letting go of the pointing stick leaves him pointing where he was, not at his own feet. */
    @Test
    void keepsTheLastAimWhenThePointingStickComesBackToCentre() {
        PadControls controls = new PadControls(CONTROLS);
        FakeGamepad pad = FakeGamepad.xbox();
        controls.read(pad.push(GamepadAxis.RIGHT_Y, 1f));

        CommanderInput released = controls.read(pad.push(GamepadAxis.RIGHT_Y, 0f));

        assertThat(released.aimY()).isCloseTo(1f, TOLERANCE);
    }

    @Test
    void firesOnTheFaceButtonAndOnTheTrigger() {
        FakeGamepad button = FakeGamepad.xbox().hold(CONTROLS.pad().fire().button());
        FakeGamepad trigger = FakeGamepad.xbox().push(CONTROLS.pad().fire().trigger(), 1f);

        assertThat(new PadControls(CONTROLS).read(button).firing()).isTrue();
        assertThat(new PadControls(CONTROLS).read(trigger).firing()).isTrue();
    }

    /**
     * The hand strike lives on a trigger and nothing else, which is what made reading triggers as
     * buttons a bug rather than a rough edge - the action simply did not exist.
     */
    @Test
    void swingsOnATriggerAlone() {
        CommanderInput input = new PadControls(CONTROLS)
                .read(FakeGamepad.xbox().push(CONTROLS.pad().strike().trigger(), 1f));

        assertThat(input.striking()).isTrue();
    }

    @Test
    void leavesTheTriggerAloneUntilItIsSqueezedFarEnough() {
        float light = CONTROLS.feel().triggerThreshold() * 0.5f;

        CommanderInput input = new PadControls(CONTROLS)
                .read(FakeGamepad.xbox().push(CONTROLS.pad().fire().trigger(), light));

        assertThat(input.firing()).isFalse();
    }

    /** An order is given once and then stands on whoever heard it (G0017), so a held d-pad is one order. */
    @Test
    void givesAnOrderOnceRatherThanForAsLongAsTheDpadIsHeld() {
        PadControls controls = new PadControls(CONTROLS);
        FakeGamepad pad = FakeGamepad.xbox().hold(CONTROLS.pad().orderFollowMe().button());

        controls.read(pad);
        Order first = controls.ordered();
        controls.read(pad);
        Order second = controls.ordered();

        assertThat(first).isEqualTo(Order.FOLLOW_ME);
        assertThat(second).isNull();
    }

    @Test
    void givesTheOrderAgainWhenTheDpadIsPressedAgain() {
        PadControls controls = new PadControls(CONTROLS);
        FakeGamepad pad = FakeGamepad.xbox().hold(CONTROLS.pad().orderDefence().button());
        controls.read(pad);

        controls.read(pad.release(CONTROLS.pad().orderDefence().button()));
        controls.read(pad.hold(CONTROLS.pad().orderDefence().button()));
        Order again = controls.ordered();

        assertThat(again).isEqualTo(Order.DEFENCE);
    }

    @Test
    void asksToSwapTheViewOnceForEachPressOfTheZoomButton() {
        PadControls controls = new PadControls(CONTROLS);
        FakeGamepad pad = FakeGamepad.xbox().hold(CONTROLS.pad().zoom().button());

        controls.read(pad);
        boolean first = controls.zoomToggled();
        controls.read(pad);
        boolean second = controls.zoomToggled();

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void pressesTheDraftButtonOnceForEachPress() {
        PadControls controls = new PadControls(CONTROLS);
        FakeGamepad pad = FakeGamepad.xbox().hold(CONTROLS.pad().draft().button());

        controls.read(pad);
        boolean first = controls.drafted();
        controls.read(pad);
        boolean held = controls.drafted();
        controls.read(pad.release(CONTROLS.pad().draft().button()));
        controls.read(pad.hold(CONTROLS.pad().draft().button()));

        assertThat(first).isTrue();
        assertThat(held).isFalse();
        assertThat(controls.drafted()).isTrue();
    }

    @Test
    void asksToPauseOnceForEachPressOfTheStartButton() {
        PadControls controls = new PadControls(CONTROLS);
        FakeGamepad pad = FakeGamepad.xbox().hold(CONTROLS.pad().pause().button());

        controls.read(pad);
        boolean first = controls.pauseRequested();
        controls.read(pad);
        boolean second = controls.pauseRequested();

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }
}
