package pl.mzebrows.pwt.client.input;

import java.util.Map;
import pl.mzebrows.pwt.client.config.ConfigLoader;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.sim.ConfigBinder;
import pl.mzebrows.pwt.sim.ConfigFile;

/**
 * Every binding and every feel number, read from {@code controls.properties}.
 *
 * <p>Kept apart from {@link GraphicConfig} and from {@code assets/sim/*.properties} on purpose. A binding is
 * not a rendering setting and it is emphatically not a simulation number: what the fire button is
 * called cannot be allowed to change what firing does, and nothing here ever reaches the simulation
 * except as the {@code CommanderInput} it produces.
 *
 * <p>Keys are named the way libGDX names them - {@code W}, {@code Up}, {@code Tab} - and a name it
 * does not recognise falls back to the default with a line in the log, which is the same bargain
 * every other config file in this game makes: a malformed file degrades the game rather than
 * stopping it.
 *
 * <p>Pad buttons are named by <strong>position</strong> ({@link GamepadButton}), so one table serves
 * an Xbox pad and a PlayStation one and {@link PadLabels} decides what the player is told to press.
 * Binding an action to {@link GamepadButton#NONE} is how the file says "not on the pad".
 *
 * <p><strong>Shaped as the file is</strong>, and bound from it directly: a binding is a component here and a
 * line there, and nothing else. A pad action's button and trigger are two components, as they are two
 * lines.
 */
public record ControlsConfig(Keys key, Mouse mouse, Pad pad, Ui ui) {

    /** How a key and a mouse button are read from their names - what the binder cannot see from {@code sim}. */
    private static final ConfigBinder.Conversions NAMES =
            new ConfigBinder.Conversions(Map.of(KeyCode.class, KeyCode::named, MouseButton.class, MouseButton::named));

    /** {@code assets/controls.properties}: what ships, compiled in, and how a player's copy is read. */
    public static final ConfigFile<ControlsConfig> FILE = new ConfigFile<>(
            "controls.properties", ControlsConfig.class, "controls", ShippedControlsConfig.TEXT, NAMES);

    /**
     * What the keyboard does, under {@code controls.key}. Player one's, and in the alpha nobody else's (1.a).
     *
     * @param moveUp, moveUpAlias, moveDown, moveDownAlias, moveLeft, moveLeftAlias, moveRight, moveRightAlias
     *     the keys he walks with, two each way
     * @param grenade key that throws
     * @param action key that acts on whatever is in reach
     * @param drag key that takes hold of a load, or lets go
     * @param dash, dashAlias the keys that dash (G0107), two for one action - either works
     * @param redeploy key that redeploys him while he is down and his window is open (G0109). The drag's key: down, he
     *     can drag nothing, so the two never meet
     * @param zoom key that pulls the view back and puts it again
     * @param rollCall key that swaps the bottom bar between the players' panels and the roll call
     * @param orderFollowMe, orderForward, orderDefence, orderCancel the four order keys. Alpha-phase
     *     scaffolding, as the input map says - the intended binding is one button and a radial menu
     * @param draft key that opens his oldest upgrade draft in the top bar, and takes the highlighted card
     *     while it is open (G0049). With it open the order keys choose a card instead
     * @param crateClose key that shuts the crate he has open (G0056). With one open the order keys choose
     *     among its cards and the action key takes the highlighted one; the fourth order key is a card there,
     *     so shutting it needs a key of its own
     */
    public record Keys(
            KeyCode moveUp,
            KeyCode moveUpAlias,
            KeyCode moveDown,
            KeyCode moveDownAlias,
            KeyCode moveLeft,
            KeyCode moveLeftAlias,
            KeyCode moveRight,
            KeyCode moveRightAlias,
            KeyCode grenade,
            KeyCode action,
            KeyCode drag,
            KeyCode dash,
            KeyCode dashAlias,
            KeyCode redeploy,
            KeyCode zoom,
            KeyCode rollCall,
            KeyCode orderFollowMe,
            KeyCode orderForward,
            KeyCode orderDefence,
            KeyCode orderCancel,
            KeyCode draft,
            KeyCode crateClose) {}

    /** The mouse buttons that fire and swing, under {@code controls.mouse}. */
    public record Mouse(MouseButton fire, MouseButton strike) {}

    /**
     * What a gamepad does, under {@code controls.pad}. Every player but the first, in the alpha.
     *
     * <p>An action is a button <em>and</em> a trigger - {@code fire} and {@code fireTrigger} - because a
     * trigger is not a second-class button here: the hand strike is on one and firing is on a trigger and a
     * face button at once. Either half may be {@link GamepadButton#NONE} or {@link GamepadAxis#NONE}, which is
     * how this codebase says <em>not bound</em>, so every action stays in the file and the file says which
     * ones do nothing.
     *
     * @param moveX, moveY the stick he walks with
     * @param aimX, aimY the stick he points with. It sends a <em>direction</em>, which is why a pad's input is
     *     marked relative and the simulation turns it into a place
     * @param fire, fireTrigger what fires. A face button and a trigger, because both are how people hold a
     *     pad to shoot
     * @param dash, dashTrigger what dashes (G0107). The pad's back paddle has no button of its own here: a pad's own
     *     software maps it to {@code EAST}, which is what this is bound to
     * @param redeploy, redeployTrigger what redeploys him while he is down and his window is open (G0109). The dash's
     *     button: down, he cannot dash, so the two never meet
     * @param zoom, zoomTrigger what pulls the view back. Shared, like the view itself: any player's press
     *     moves everybody's camera
     * @param orderFollowMe, orderForward, orderDefence, orderCancel the four orders, on the d-pad
     * @param draft, draftTrigger what opens his oldest upgrade draft, and takes the highlighted card while it
     *     is open. With it open the d-pad moves the highlight instead of giving orders
     * @param leftTriggerAxis, rightTriggerAxis raw axis index of each trigger; SDL's are 4 and 5. Here rather
     *     than hardcoded because the library cannot answer it: gdx-controllers 2.2.4's canonical mapping has
     *     no trigger axes at all, so a trigger is read as a raw axis by index, a fact about SDL and the pad
     * @param triggerRestsAtMinusOne whether a released trigger reads -1 rather than 0. SDL's read zero to
     *     one; a pad that reads -1 to 1 needs this on or its triggers register only past halfway
     * @param moveDeadZone how far the walking stick must leave centre before he walks. Radial rather than
     *     per-axis: a square dead zone makes a diagonal need less push than a straight line
     * @param aimDeadZone the same for the pointing stick, and larger on purpose - a stick resting slightly off
     *     centre must not swing his aim while he is walking
     * @param triggerThreshold how far a trigger must be squeezed to count as held
     */
    public record Pad(
            GamepadAxis moveX,
            GamepadAxis moveY,
            GamepadAxis aimX,
            GamepadAxis aimY,
            GamepadButton fire,
            GamepadAxis fireTrigger,
            GamepadButton strike,
            GamepadAxis strikeTrigger,
            GamepadButton grenade,
            GamepadAxis grenadeTrigger,
            GamepadButton action,
            GamepadAxis actionTrigger,
            GamepadButton drag,
            GamepadAxis dragTrigger,
            GamepadButton dash,
            GamepadAxis dashTrigger,
            GamepadButton redeploy,
            GamepadAxis redeployTrigger,
            GamepadButton zoom,
            GamepadAxis zoomTrigger,
            GamepadButton pause,
            GamepadAxis pauseTrigger,
            GamepadButton orderFollowMe,
            GamepadAxis orderFollowMeTrigger,
            GamepadButton orderForward,
            GamepadAxis orderForwardTrigger,
            GamepadButton orderDefence,
            GamepadAxis orderDefenceTrigger,
            GamepadButton orderCancel,
            GamepadAxis orderCancelTrigger,
            GamepadButton draft,
            GamepadAxis draftTrigger,
            int leftTriggerAxis,
            int rightTriggerAxis,
            boolean triggerRestsAtMinusOne,
            float moveDeadZone,
            float aimDeadZone,
            float triggerThreshold) {

        public Pad {
            if (moveDeadZone < 0f || moveDeadZone >= 1f || aimDeadZone < 0f || aimDeadZone >= 1f) {
                throw new IllegalArgumentException("dead zones must be in [0, 1)");
            }
            if (triggerThreshold <= 0f || triggerThreshold > 1f) {
                throw new IllegalArgumentException("trigger threshold must be in (0, 1]");
            }
        }
    }

    /**
     * How pages and modals are driven (plan 6.2), under {@code controls.ui}. Pad buttons by position (ADR
     * 0011): in a page {@code SOUTH} confirms and {@code EAST} goes back, which in a level are act and throw -
     * the release latch keeps the one from becoming the other.
     */
    public record Ui(UiKeys key, UiPad pad, Nav nav) {}

    /** Keys that confirm the selected entry, and keys that go back a page - two of each. */
    public record UiKeys(KeyCode confirm, KeyCode confirmAlias, KeyCode back, KeyCode backAlias) {}

    /** Pad buttons that confirm and go back. */
    public record UiPad(GamepadButton confirm, GamepadButton back) {}

    /**
     * How a selection moves.
     *
     * @param stickThreshold how far a stick must be pushed to move the selection
     * @param repeatDelaySeconds how long a held direction waits before it repeats
     * @param repeatIntervalSeconds how often it repeats after that
     * @param pointerWakePixels how far the mouse must move, in screen pixels, to take the selection
     * @param scrollLinesPerSecond how fast a pad's right stick, pushed all the way, scrolls a list too long
     *     for its panel - the pause menu's upgrades (A.1)
     */
    public record Nav(
            float stickThreshold,
            float repeatDelaySeconds,
            float repeatIntervalSeconds,
            int pointerWakePixels,
            float scrollLinesPerSecond) {}

    /** The bindings from {@code controls.properties}, every value it lacks or garbles taken from what ships. */
    public static ControlsConfig load() {
        return ConfigLoader.load(FILE);
    }

    /** What ships: {@code assets/controls.properties}, compiled in. What every test presses. */
    public static ControlsConfig defaults() {
        return FILE.defaults();
    }
}
