package pl.mzebrows.pwt.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Whatever pads a test says are plugged in right now. */
final class FakeGamepads implements GamepadSource {

    private final List<Gamepad> plugged = new ArrayList<>();

    @Override
    public int count() {
        return plugged.size();
    }

    @Override
    public Gamepad get(int index) {
        return plugged.get(index);
    }

    void pluggedIn(Gamepad... pads) {
        plugged.clear();
        plugged.addAll(Arrays.asList(pads));
    }
}
