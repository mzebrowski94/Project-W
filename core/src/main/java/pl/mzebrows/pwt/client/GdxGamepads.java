package pl.mzebrows.pwt.client;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link GamepadSource} on top of gdx-controllers, which is the only library this file knows about.
 *
 * <p>It was already a declared dependency of this module and of the desktop launcher - part of the
 * gdx-setup template, never used - so switching pads on cost no new dependency at all. On the
 * desktop it is SDL underneath, by way of Jamepad.
 *
 * <p><strong>Everything library-shaped stops here.</strong> Its {@code ControllerMapping} is a set of
 * integers whose meaning it works out per platform and per pad, and its own documentation says the
 * mapping is guaranteed on some platforms and merely usually right on others. That uncertainty is
 * exactly what the port is for: if it turns out to be wrong on a pad Mateusz owns, this one file is
 * replaced - by input4j, or by GLFW's own gamepad API, which the desktop launcher already ships -
 * and nothing that decides what a button means changes at all.
 *
 * <p>Sticks are flipped here rather than anywhere above: libGDX reports the vertical axis positive
 * <em>downwards</em>, the world has y up, and the port promises y up.
 */
public final class GdxGamepads implements GamepadSource {

    /** Reused between frames, so asking who is plugged in costs no allocation per frame. */
    private final List<GdxGamepad> pads = new ArrayList<>(4);

    private final ControlsConfig.Hardware hardware;

    public GdxGamepads(ControlsConfig.Hardware hardware) {
        this.hardware = hardware;
    }

    @Override
    public int count() {
        refresh();
        return pads.size();
    }

    @Override
    public Gamepad get(int index) {
        return pads.get(index);
    }

    /**
     * Brings the list in line with what is plugged in, reusing the wrapper for a pad that was already
     * there so the aim it remembers is not lost when somebody else unplugs theirs.
     */
    private void refresh() {
        Array<Controller> live = Controllers.getControllers();
        // An index loop rather than removeIf: this runs every frame, and the lambda would capture
        // `live` into a fresh object each time for nothing.
        for (int i = pads.size() - 1; i >= 0; i--) {
            if (!stillPluggedIn(live, pads.get(i).id())) {
                pads.remove(i);
            }
        }
        for (Controller controller : live) {
            if (!holds(controller.getUniqueId())) {
                pads.add(new GdxGamepad(controller, hardware));
                announce(controller);
            }
        }
    }

    /**
     * Says once what this pad looks like from here, because the guesswork this class contains is the
     * likeliest thing in the input path to be wrong on a pad nobody has tried.
     */
    private void announce(Controller controller) {
        int axes = controller.getAxisCount();
        boolean raw = hardware.leftTriggerAxis() < axes && hardware.rightTriggerAxis() < axes;
        Log.info("pad \"" + controller.getName() + "\": " + axes + " axes, triggers read from "
                + (raw
                        ? "axes " + hardware.leftTriggerAxis() + " and " + hardware.rightTriggerAxis()
                        : "the L2/R2 buttons"));
    }

    private static boolean stillPluggedIn(Array<Controller> live, String id) {
        for (Controller controller : live) {
            if (id.equals(controller.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private boolean holds(String id) {
        for (int i = 0; i < pads.size(); i++) {
            if (pads.get(i).id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** One controller, answering the port's questions in the port's terms. */
    private static final class GdxGamepad implements Gamepad {

        private final Controller controller;
        private final ControlsConfig.Hardware hardware;
        private final String id;

        private GdxGamepad(Controller controller, ControlsConfig.Hardware hardware) {
            this.controller = controller;
            this.hardware = hardware;
            this.id = controller.getUniqueId();
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return controller.getName();
        }

        @Override
        public float axis(GamepadAxis axis) {
            ControllerMapping mapping = controller.getMapping();
            return switch (axis) {
                case NONE -> 0f;
                case LEFT_X -> controller.getAxis(mapping.axisLeftX);
                case LEFT_Y -> -controller.getAxis(mapping.axisLeftY);
                case RIGHT_X -> controller.getAxis(mapping.axisRightX);
                case RIGHT_Y -> -controller.getAxis(mapping.axisRightY);
                case LEFT_TRIGGER -> trigger(mapping.buttonL2, hardware.leftTriggerAxis());
                case RIGHT_TRIGGER -> trigger(mapping.buttonR2, hardware.rightTriggerAxis());
            };
        }

        /**
         * How far a trigger is squeezed, the long way round, because the library cannot say.
         *
         * <p><strong>gdx-controllers 2.2.4's canonical mapping has no trigger axes.</strong> It
         * offers the two shoulder buttons underneath the bumpers and nothing else, and an XInput pad
         * - where both triggers are analogue axes - leaves those undefined. Reading them as buttons
         * is what made firing on the right trigger and hauling on the left do nothing at all.
         *
         * <p>So the raw axis is preferred and the button is the fallback. The raw index is SDL's, in
         * {@code controls.properties} rather than in this file, because it is a fact about the pad
         * rather than about the game and a pad that numbers its axes differently must be fixable
         * without a rebuild.
         */
        private float trigger(int button, int rawAxis) {
            if (rawAxis >= 0 && rawAxis < controller.getAxisCount()) {
                float value = controller.getAxis(rawAxis);
                return hardware.triggerRestsAtMinusOne() ? (value + 1f) * 0.5f : value;
            }
            return button != ControllerMapping.UNDEFINED && controller.getButton(button) ? 1f : 0f;
        }

        @Override
        public boolean pressed(GamepadButton button) {
            ControllerMapping mapping = controller.getMapping();
            return switch (button) {
                case NONE -> false;
                case SOUTH -> controller.getButton(mapping.buttonA);
                case EAST -> controller.getButton(mapping.buttonB);
                case WEST -> controller.getButton(mapping.buttonX);
                case NORTH -> controller.getButton(mapping.buttonY);
                case L1 -> controller.getButton(mapping.buttonL1);
                case R1 -> controller.getButton(mapping.buttonR1);
                case L3 -> controller.getButton(mapping.buttonLeftStick);
                case R3 -> controller.getButton(mapping.buttonRightStick);
                case START -> controller.getButton(mapping.buttonStart);
                case SELECT -> controller.getButton(mapping.buttonBack);
                case DPAD_UP -> controller.getButton(mapping.buttonDpadUp);
                case DPAD_DOWN -> controller.getButton(mapping.buttonDpadDown);
                case DPAD_LEFT -> controller.getButton(mapping.buttonDpadLeft);
                case DPAD_RIGHT -> controller.getButton(mapping.buttonDpadRight);
            };
        }
    }
}
