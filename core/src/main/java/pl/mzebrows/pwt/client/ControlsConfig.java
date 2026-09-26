package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Input;
import java.util.Locale;

/**
 * Every binding and every feel number, read from {@code controls.properties}.
 *
 * <p>Kept apart from {@link GraphicConfig} and from {@code game.properties} on purpose. A binding is
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
 */
public record ControlsConfig(Keyboard keyboard, Pad pad, Feel feel, Hardware hardware, Ui ui) {

    private static final String FILE = "controls.properties";

    /** Two keys that push an axis one way and two that push it the other. */
    public record KeyAxis(int positive, int positiveAlias, int negative, int negativeAlias) {}

    /**
     * One action's place on a pad: a button, a trigger, or both.
     *
     * <p>Both, for every action, because a trigger is not a second-class button here - the hand
     * strike is on one and firing is on a trigger and a face button at once. Either half may be
     * {@link GamepadButton#NONE} or {@link GamepadAxis#NONE}, which is how this codebase says <em>not
     * bound</em>, so every action stays in the file and the file says which ones do nothing.
     */
    public record PadBinding(GamepadButton button, GamepadAxis trigger) {}

    /**
     * What the keyboard and mouse do. Player one's, and in the alpha nobody else's (1.a).
     *
     * @param moveX keys that walk him right, and keys that walk him left
     * @param moveY keys that walk him up, and keys that walk him down
     * @param fireButton mouse button that fires, from {@code Input.Buttons}
     * @param strikeButton mouse button that swings
     * @param grenade key that throws
     * @param action key that acts on whatever is in reach
     * @param drag key that takes hold of a load, or lets go
     * @param zoom key that pulls the view back and puts it again
     * @param rollCall key that swaps the bottom bar between the players' panels and the roll call
     * @param orderFollowMe, orderForward, orderDefence, orderCancel the four order keys. Alpha-phase
     *     scaffolding, as the input map says - the intended binding is one button and a radial menu
     */
    public record Keyboard(
            KeyAxis moveX,
            KeyAxis moveY,
            int fireButton,
            int strikeButton,
            int grenade,
            int action,
            int drag,
            int zoom,
            int rollCall,
            int orderFollowMe,
            int orderForward,
            int orderDefence,
            int orderCancel) {}

    /**
     * What a gamepad does. Every player but the first, in the alpha.
     *
     * @param moveX, moveY the stick he walks with
     * @param aimX, aimY the stick he points with. It sends a <em>direction</em>, which is why a pad's
     *     input is marked relative and the simulation turns it into a place
     * @param fire what fires. A face button and a trigger, because both are how people hold a pad to
     *     shoot
     * @param strike what swings
     * @param grenade what throws
     * @param action what acts on whatever is in reach
     * @param drag what takes hold of a load, or lets go
     * @param zoom what pulls the view back. Shared, like the view itself: any player's press moves
     *     everybody's camera
     * @param pause what stops the clock
     * @param orderFollowMe, orderForward, orderDefence, orderCancel the four orders, on the d-pad
     */
    public record Pad(
            GamepadAxis moveX,
            GamepadAxis moveY,
            GamepadAxis aimX,
            GamepadAxis aimY,
            PadBinding fire,
            PadBinding strike,
            PadBinding grenade,
            PadBinding action,
            PadBinding drag,
            PadBinding zoom,
            PadBinding pause,
            PadBinding orderFollowMe,
            PadBinding orderForward,
            PadBinding orderDefence,
            PadBinding orderCancel) {}

    /**
     * Facts about the hardware that {@link GdxGamepads} needs and no decision above it does.
     *
     * <p><strong>They are here rather than hardcoded because the library cannot answer them.</strong>
     * gdx-controllers 2.2.4's canonical mapping has no trigger axes at all - only {@code buttonL2}
     * and {@code buttonR2}, which an XInput pad leaves undefined - so a trigger has to be read as a
     * raw axis by index, and the index is a fact about SDL and the pad rather than about the game.
     * Putting them in the file means a pad that numbers its axes differently is one line to fix
     * rather than a rebuild.
     *
     * @param leftTriggerAxis raw axis index of the left trigger; SDL's is 4
     * @param rightTriggerAxis raw axis index of the right trigger; SDL's is 5
     * @param triggerRestsAtMinusOne whether a released trigger reads -1 rather than 0. SDL's read
     *     zero to one, which is the default; a pad that reads -1 to 1 needs this on or its triggers
     *     will only register past halfway
     */
    public record Hardware(int leftTriggerAxis, int rightTriggerAxis, boolean triggerRestsAtMinusOne) {}

    /**
     * How a stick has to be pushed before the game believes it.
     *
     * @param moveDeadZone how far the walking stick must leave centre before he walks. Radial rather
     *     than per-axis: a square dead zone makes a diagonal need less push than a straight line,
     *     which players feel as the pad sticking to the axes
     * @param aimDeadZone the same for the pointing stick, and larger on purpose - a stick resting
     *     slightly off centre must not swing his aim while he is walking
     * @param triggerThreshold how far a trigger must be squeezed to count as held
     */
    public record Feel(float moveDeadZone, float aimDeadZone, float triggerThreshold) {
        public Feel {
            if (moveDeadZone < 0f || moveDeadZone >= 1f || aimDeadZone < 0f || aimDeadZone >= 1f) {
                throw new IllegalArgumentException("dead zones must be in [0, 1)");
            }
            if (triggerThreshold <= 0f || triggerThreshold > 1f) {
                throw new IllegalArgumentException("trigger threshold must be in (0, 1]");
            }
        }
    }

    /**
     * How pages and modals are driven (plan 6.2). Pad buttons by position (ADR 0011): in a page
     * {@code SOUTH} confirms and {@code EAST} goes back, which in a level are act and throw - the
     * release latch keeps the one from becoming the other.
     *
     * @param confirm, confirmAlias keys that confirm the selected entry
     * @param back, backAlias keys that go back a page
     * @param padConfirm, padBack pad buttons that confirm and go back
     * @param stickThreshold how far a stick must be pushed to move the selection
     * @param repeatDelaySeconds how long a held direction waits before it repeats
     * @param repeatIntervalSeconds how often it repeats after that
     * @param pointerWakePixels how far the mouse must move, in screen pixels, to take the selection
     */
    public record Ui(
            int confirm,
            int confirmAlias,
            int back,
            int backAlias,
            GamepadButton padConfirm,
            GamepadButton padBack,
            float stickThreshold,
            float repeatDelaySeconds,
            float repeatIntervalSeconds,
            int pointerWakePixels) {}

    /** Loads the bindings, falling back to the defaults below for anything missing or malformed. */
    public static ControlsConfig load() {
        GameProperties properties = GameProperties.load(FILE);
        return new ControlsConfig(
                keyboard(properties), pad(properties), feel(properties), hardware(properties), ui(properties));
    }

    private static Ui ui(GameProperties properties) {
        return new Ui(
                key(properties, "controls.ui.key.confirm", Input.Keys.ENTER),
                key(properties, "controls.ui.key.confirmAlias", Input.Keys.SPACE),
                key(properties, "controls.ui.key.back", Input.Keys.ESCAPE),
                key(properties, "controls.ui.key.backAlias", Input.Keys.BACKSPACE),
                button(properties, "controls.ui.pad.confirm", GamepadButton.SOUTH),
                button(properties, "controls.ui.pad.back", GamepadButton.EAST),
                properties.getFloat("controls.ui.nav.stickThreshold", 0.5f),
                properties.getFloat("controls.ui.nav.repeatDelaySeconds", 0.35f),
                properties.getFloat("controls.ui.nav.repeatIntervalSeconds", 0.09f),
                properties.getInt("controls.ui.nav.pointerWakePixels", 4));
    }

    private static Keyboard keyboard(GameProperties properties) {
        return new Keyboard(
                new KeyAxis(
                        key(properties, "controls.key.moveRight", Input.Keys.D),
                        key(properties, "controls.key.moveRightAlias", Input.Keys.RIGHT),
                        key(properties, "controls.key.moveLeft", Input.Keys.A),
                        key(properties, "controls.key.moveLeftAlias", Input.Keys.LEFT)),
                new KeyAxis(
                        key(properties, "controls.key.moveUp", Input.Keys.W),
                        key(properties, "controls.key.moveUpAlias", Input.Keys.UP),
                        key(properties, "controls.key.moveDown", Input.Keys.S),
                        key(properties, "controls.key.moveDownAlias", Input.Keys.DOWN)),
                mouseButton(properties, "controls.mouse.fire", Input.Buttons.LEFT),
                mouseButton(properties, "controls.mouse.strike", Input.Buttons.RIGHT),
                key(properties, "controls.key.grenade", Input.Keys.Q),
                key(properties, "controls.key.action", Input.Keys.E),
                key(properties, "controls.key.drag", Input.Keys.F),
                key(properties, "controls.key.zoom", Input.Keys.TAB),
                key(properties, "controls.key.rollCall", Input.Keys.GRAVE),
                key(properties, "controls.key.orderFollowMe", Input.Keys.NUM_1),
                key(properties, "controls.key.orderForward", Input.Keys.NUM_2),
                key(properties, "controls.key.orderDefence", Input.Keys.NUM_3),
                key(properties, "controls.key.orderCancel", Input.Keys.NUM_4));
    }

    private static Pad pad(GameProperties properties) {
        return new Pad(
                axis(properties, "controls.pad.moveX", GamepadAxis.LEFT_X),
                axis(properties, "controls.pad.moveY", GamepadAxis.LEFT_Y),
                axis(properties, "controls.pad.aimX", GamepadAxis.RIGHT_X),
                axis(properties, "controls.pad.aimY", GamepadAxis.RIGHT_Y),
                binding(properties, "controls.pad.fire", GamepadButton.WEST, GamepadAxis.RIGHT_TRIGGER),
                binding(properties, "controls.pad.strike", GamepadButton.NONE, GamepadAxis.LEFT_TRIGGER),
                binding(properties, "controls.pad.grenade", GamepadButton.EAST, GamepadAxis.NONE),
                binding(properties, "controls.pad.action", GamepadButton.SOUTH, GamepadAxis.NONE),
                binding(properties, "controls.pad.drag", GamepadButton.R1, GamepadAxis.NONE),
                binding(properties, "controls.pad.zoom", GamepadButton.L1, GamepadAxis.NONE),
                binding(properties, "controls.pad.pause", GamepadButton.START, GamepadAxis.NONE),
                binding(properties, "controls.pad.orderFollowMe", GamepadButton.DPAD_UP, GamepadAxis.NONE),
                binding(properties, "controls.pad.orderForward", GamepadButton.DPAD_RIGHT, GamepadAxis.NONE),
                binding(properties, "controls.pad.orderDefence", GamepadButton.DPAD_DOWN, GamepadAxis.NONE),
                binding(properties, "controls.pad.orderCancel", GamepadButton.DPAD_LEFT, GamepadAxis.NONE));
    }

    private static Hardware hardware(GameProperties properties) {
        return new Hardware(
                properties.getInt("controls.pad.leftTriggerAxis", 4),
                properties.getInt("controls.pad.rightTriggerAxis", 5),
                properties.getBoolean("controls.pad.triggerRestsAtMinusOne", false));
    }

    private static PadBinding binding(
            GameProperties properties, String property, GamepadButton button, GamepadAxis trigger) {
        return new PadBinding(button(properties, property, button), axis(properties, property + "Trigger", trigger));
    }

    private static Feel feel(GameProperties properties) {
        return new Feel(
                properties.getFloat("controls.pad.moveDeadZone", 0.2f),
                properties.getFloat("controls.pad.aimDeadZone", 0.3f),
                properties.getFloat("controls.pad.triggerThreshold", 0.4f));
    }

    private static int key(GameProperties properties, String property, int fallback) {
        String name = properties.getString(property, "");
        if (name.isEmpty()) {
            return fallback;
        }
        int keycode = Input.Keys.valueOf(name);
        if (keycode == -1) {
            Log.error(
                    FILE + ": " + property + "='" + name + "' is not a key name; using "
                            + Input.Keys.toString(fallback),
                    null);
            return fallback;
        }
        return keycode;
    }

    private static int mouseButton(GameProperties properties, String property, int fallback) {
        String name = properties.getString(property, "").toUpperCase(Locale.ROOT);
        return switch (name) {
            case "" -> fallback;
            case "LEFT" -> Input.Buttons.LEFT;
            case "RIGHT" -> Input.Buttons.RIGHT;
            case "MIDDLE" -> Input.Buttons.MIDDLE;
            default -> {
                Log.error(FILE + ": " + property + "='" + name + "' is not LEFT, RIGHT or MIDDLE", null);
                yield fallback;
            }
        };
    }

    private static GamepadButton button(GameProperties properties, String property, GamepadButton fallback) {
        String name = properties.getString(property, "").toUpperCase(Locale.ROOT);
        if (name.isEmpty()) {
            return fallback;
        }
        for (GamepadButton candidate : GamepadButton.values()) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        Log.error(FILE + ": " + property + "='" + name + "' is not a pad button; using " + fallback, null);
        return fallback;
    }

    private static GamepadAxis axis(GameProperties properties, String property, GamepadAxis fallback) {
        String name = properties.getString(property, "").toUpperCase(Locale.ROOT);
        if (name.isEmpty()) {
            return fallback;
        }
        for (GamepadAxis candidate : GamepadAxis.values()) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        Log.error(FILE + ": " + property + "='" + name + "' is not a pad axis; using " + fallback, null);
        return fallback;
    }
}
