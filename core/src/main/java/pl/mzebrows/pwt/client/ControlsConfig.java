package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Input;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import pl.mzebrows.pwt.sim.ConfigBinder;

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
 */
public record ControlsConfig(Keyboard keyboard, Pad pad, Feel feel, Hardware hardware, Ui ui) {

    private static final String FILE = "controls.properties";

    private static final String ROOT = "controls";

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
     * @param draft key that opens his oldest upgrade draft in the top bar, and takes the highlighted card
     *     while it is open (G0049). With it open the order keys choose a card instead
     * @param crateClose key that shuts the crate he has open (G0056). With one open the order keys choose
     *     among its cards and the action key takes the highlighted one; the fourth order key is a card there,
     *     so shutting it needs a key of its own
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
            int orderCancel,
            int draft,
            int crateClose) {}

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
     * @param draft what opens his oldest upgrade draft, and takes the highlighted card while it is open.
     *     With it open the d-pad moves the highlight instead of giving orders
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
            PadBinding orderCancel,
            PadBinding draft) {}

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
     * @param scrollLinesPerSecond how fast a pad's right stick, pushed all the way, scrolls a list too long
     *     for its panel - the pause menu's upgrades (A.1)
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
            int pointerWakePixels,
            float scrollLinesPerSecond) {}

    /**
     * The bindings from {@code controls.properties}, with every value it lacks or garbles taken from what
     * ships and said out loud ({@link #parse}).
     */
    public static ControlsConfig load() {
        return parse(GameProperties.text(FILE));
    }

    /** What ships: {@code assets/controls.properties}, compiled in. What every test presses. */
    public static ControlsConfig defaults() {
        return Shipped.CONFIG;
    }

    /**
     * The bindings from the text of a {@code controls.properties}. Empty text - no file at all - is what
     * ships. A key no record has, a value missing or unreadable, and a key or mouse button with no such
     * name are each logged, and the shipped binding stands for it; {@code ShippedControlsConfigTest} holds
     * the shipped file to stricter terms.
     */
    public static ControlsConfig parse(String text) {
        Parsed parsed = read(text);
        for (String problem : parsed.problems()) {
            Log.error(FILE + ": " + problem + "; the shipped binding stands", null);
        }
        return parsed.config();
    }

    /** The same, with everything that was wrong with the text rather than logging it. */
    static Parsed read(String text) {
        if (text.isBlank()) {
            return new Parsed(defaults(), List.of());
        }
        List<String> problems = new ArrayList<>();
        SortedMap<String, String> values = names(ConfigBinder.parse(text), problems);
        ConfigBinder.Bound<ControlsFile> bound = ConfigBinder.bind(ControlsFile.class, ROOT, values, Shipped.VALUES);
        problems.addAll(bound.problems());
        ControlsFile file = bound.value();
        return new Parsed(file == null ? defaults() : of(file), List.copyOf(problems));
    }

    /** A config read from text, and everything that had to be taken from what ships instead. */
    public record Parsed(ControlsConfig config, List<String> problems) {}

    /**
     * The same text with every key and mouse button that names nothing replaced by the shipped one, and
     * named. Enums the binder reads itself.
     */
    static SortedMap<String, String> names(SortedMap<String, String> values, List<String> problems) {
        SortedMap<String, String> checked = new TreeMap<>(values);
        for (var entry : values.entrySet()) {
            String key = entry.getKey();
            boolean keyName = key.startsWith(ROOT + ".key.") || key.startsWith(ROOT + ".ui.key.");
            boolean mouseName = key.startsWith(ROOT + ".mouse.");
            if ((keyName && Input.Keys.valueOf(entry.getValue()) == -1)
                    || (mouseName && mouseButton(entry.getValue()) < 0)) {
                problems.add(key + "='" + entry.getValue() + "' names no " + (keyName ? "key" : "mouse button"));
                String shipped = Shipped.VALUES.get(key);
                if (shipped == null) {
                    checked.remove(key);
                } else {
                    checked.put(key, shipped);
                }
            }
        }
        return checked;
    }

    /** What the input reads, built from the file as written. */
    static ControlsConfig of(ControlsFile file) {
        ControlsFile.Key key = file.key();
        ControlsFile.Pad pad = file.pad();
        ControlsFile.Ui ui = file.ui();
        return new ControlsConfig(
                new Keyboard(
                        new KeyAxis(
                                key(key.moveRight()),
                                key(key.moveRightAlias()),
                                key(key.moveLeft()),
                                key(key.moveLeftAlias())),
                        new KeyAxis(
                                key(key.moveUp()),
                                key(key.moveUpAlias()),
                                key(key.moveDown()),
                                key(key.moveDownAlias())),
                        mouseButton(file.mouse().fire()),
                        mouseButton(file.mouse().strike()),
                        key(key.grenade()),
                        key(key.action()),
                        key(key.drag()),
                        key(key.zoom()),
                        key(key.rollCall()),
                        key(key.orderFollowMe()),
                        key(key.orderForward()),
                        key(key.orderDefence()),
                        key(key.orderCancel()),
                        key(key.draft()),
                        key(key.crateClose())),
                new Pad(
                        pad.moveX(),
                        pad.moveY(),
                        pad.aimX(),
                        pad.aimY(),
                        new PadBinding(pad.fire(), pad.fireTrigger()),
                        new PadBinding(pad.strike(), pad.strikeTrigger()),
                        new PadBinding(pad.grenade(), pad.grenadeTrigger()),
                        new PadBinding(pad.action(), pad.actionTrigger()),
                        new PadBinding(pad.drag(), pad.dragTrigger()),
                        new PadBinding(pad.zoom(), pad.zoomTrigger()),
                        new PadBinding(pad.pause(), pad.pauseTrigger()),
                        new PadBinding(pad.orderFollowMe(), pad.orderFollowMeTrigger()),
                        new PadBinding(pad.orderForward(), pad.orderForwardTrigger()),
                        new PadBinding(pad.orderDefence(), pad.orderDefenceTrigger()),
                        new PadBinding(pad.orderCancel(), pad.orderCancelTrigger()),
                        new PadBinding(pad.draft(), pad.draftTrigger())),
                new Feel(pad.moveDeadZone(), pad.aimDeadZone(), pad.triggerThreshold()),
                new Hardware(pad.leftTriggerAxis(), pad.rightTriggerAxis(), pad.triggerRestsAtMinusOne()),
                new Ui(
                        key(ui.key().confirm()),
                        key(ui.key().confirmAlias()),
                        key(ui.key().back()),
                        key(ui.key().backAlias()),
                        ui.pad().confirm(),
                        ui.pad().back(),
                        ui.nav().stickThreshold(),
                        ui.nav().repeatDelaySeconds(),
                        ui.nav().repeatIntervalSeconds(),
                        ui.nav().pointerWakePixels(),
                        ui.nav().scrollLinesPerSecond()));
    }

    /** A key by the name libGDX gives it; {@link #names} has already refused a name that is none. */
    private static int key(String name) {
        return Input.Keys.valueOf(name);
    }

    /** A mouse button by name, or -1 for a name that is none. */
    private static int mouseButton(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "LEFT" -> Input.Buttons.LEFT;
            case "RIGHT" -> Input.Buttons.RIGHT;
            case "MIDDLE" -> Input.Buttons.MIDDLE;
            default -> -1;
        };
    }

    /** Bound on first use, so a failure names the key rather than a class. */
    private static final class Shipped {

        static final SortedMap<String, String> VALUES = ConfigBinder.parse(ShippedControlsConfig.TEXT);

        static final ControlsConfig CONFIG =
                of(ConfigBinder.bindStrictly(ControlsFile.class, ROOT, ShippedControlsConfig.TEXT));

        private Shipped() {}
    }
}
