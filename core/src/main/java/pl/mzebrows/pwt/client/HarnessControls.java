package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Input;
import java.util.ArrayList;
import java.util.List;

/**
 * The alpha-phase key bindings, and nothing else.
 *
 * <p>These are dev controls, deliberately kept out of the game's input map in {@code
 * docs/design/README.md} so a real binding is never assumed taken by one of them.
 */
public final class HarnessControls {

    private HarnessControls() {}

    /**
     * This frame's harness commands, in the order they should be applied.
     *
     * <p>{@link HarnessCommand#LEAVE} comes first when present. Pressing escape together with
     * anything else should leave, not pause and then leave, and putting that ordering here rather
     * than in the caller is what makes it testable.
     */
    public static List<HarnessCommand> poll(KeyPresses keys) {
        List<HarnessCommand> commands = new ArrayList<>(2);
        if (keys.justPressed(Input.Keys.ESCAPE)) {
            commands.add(HarnessCommand.LEAVE);
        }
        if (keys.justPressed(Input.Keys.P)) {
            commands.add(HarnessCommand.PAUSE);
        }
        if (keys.justPressed(Input.Keys.PERIOD)) {
            commands.add(HarnessCommand.STEP);
        }
        if (keys.justPressed(Input.Keys.R)) {
            commands.add(HarnessCommand.RESTART);
        }
        if (keys.justPressed(Input.Keys.F1)) {
            commands.add(HarnessCommand.TOGGLE_OVERLAY);
        }
        if (keys.justPressed(Input.Keys.G)) {
            commands.add(HarnessCommand.TOGGLE_GOD_MODE);
        }
        if (keys.justPressed(Input.Keys.F2)) {
            commands.add(HarnessCommand.TOGGLE_COSMETIC_LEAD);
        }
        if (keys.justPressed(Input.Keys.F3)) {
            commands.add(HarnessCommand.NEXT_LAG);
        }
        return commands;
    }
}
