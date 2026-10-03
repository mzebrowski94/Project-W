package pl.mzebrows.pwt.client.input;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * A gamepad made of plain values, for the tests of everything above the port.
 *
 * <p>The first mock-shaped thing in the repository and deliberately not a mock: what it stands in for
 * is a device, the interesting cases are all about <em>what it reads</em> rather than about who
 * called it, and a stub that can be read twice in a frame is what edge detection needs anyway.
 */
final class FakeGamepad implements Gamepad {

    private final String id;
    private final String name;
    private final EnumMap<GamepadAxis, Float> axes = new EnumMap<>(GamepadAxis.class);
    private final EnumSet<GamepadButton> down = EnumSet.noneOf(GamepadButton.class);

    FakeGamepad(String id, String name) {
        this.id = id;
        this.name = name;
    }

    static FakeGamepad xbox() {
        return new FakeGamepad("pad-1", "Xbox Wireless Controller");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public float axis(GamepadAxis axis) {
        return axes.getOrDefault(axis, 0f);
    }

    @Override
    public boolean pressed(GamepadButton button) {
        return down.contains(button);
    }

    FakeGamepad push(GamepadAxis axis, float value) {
        axes.put(axis, value);
        return this;
    }

    FakeGamepad hold(GamepadButton button) {
        down.add(button);
        return this;
    }

    FakeGamepad release(GamepadButton button) {
        down.remove(button);
        return this;
    }
}
