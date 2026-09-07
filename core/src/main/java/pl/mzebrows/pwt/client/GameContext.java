package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Screen;
import pl.mzebrows.pwt.sim.SimConfig;

/**
 * What a screen is allowed to ask of the application.
 *
 * <p>Screens are handed this rather than {@link PwtGame} itself, and the omission is the point:
 * {@code setScreen} is not on it. A screen that switches itself frees GL resources the current frame
 * is still drawing with, which crashes the JVM with an access violation instead of an exception, so
 * the only way to leave a screen is to ask for the swap and let the frame finish.
 *
 * <p>{@code ClientArchitectureTest} enforces that no screen reaches past this interface.
 */
public interface GameContext {

    /** Rendering tunables: viewport size, colours. */
    GraphicConfig graphicConfig();

    /** The simulation config a scenario starts from, before its own overrides. */
    SimConfig simConfig();

    /** Shared screen-space text renderer, owned by the application rather than by any one screen. */
    Hud hud();

    /** Asks for a screen change. It happens once the current frame has finished drawing. */
    void queueScreen(Screen next);
}
