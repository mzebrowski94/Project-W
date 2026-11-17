package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Screen;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.hud.Hud;
import pl.mzebrows.pwt.client.input.ControlsConfig;
import pl.mzebrows.pwt.client.input.GamepadSource;
import pl.mzebrows.pwt.client.play.MissionChoices;
import pl.mzebrows.pwt.client.play.MissionRecords;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.scenario.ProvingSetup;
import pl.mzebrows.pwt.ui.nav.SelectionMemory;

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

    /** The text {@link #simConfig()} was bound from, comments left out: the world config a host ships. */
    String worldConfigText();

    /** Every binding and feel number, read once at startup from {@code controls.properties}. */
    ControlsConfig controlsConfig();

    /**
     * Where gamepads come from.
     *
     * <p>Owned by the application rather than by a screen, so a pad plugged in during the menu is
     * already seated when a scenario starts, and unplugging one during a restart does not lose a
     * player's seat.
     */
    GamepadSource gamepads();

    /** The networking tunables, read once at startup from {@code network.properties}. */
    NetConfig netConfig();

    /** What this copy of the game was started with: which instance, which pads, where to join. */
    LaunchOptions launchOptions();

    /** Shared screen-space text renderer, owned by the application rather than by any one screen. */
    Hud hud();

    /**
     * Which entry each menu page had selected, for this run of the game: coming back to a page lands
     * where the player was, and the campaign list opens on the scenario last played.
     */
    SelectionMemory selections();

    /** The proving ground's setup, as the player last left it this run - never saved (G0060). */
    ProvingSetup provingSetup();

    void provingSetup(ProvingSetup setup);

    /** The DAILY MISSIONS page's choices, kept for the run: RANDOM's tier and the last code played (G0065). */
    MissionChoices missions();

    /** What this machine remembers of the dailies it has won: a ✓ and a best time each (G0067). */
    MissionRecords missionRecords();

    /** What the player has set on this computer - whether the prompts show - kept between runs. */
    LocalSettings settings();

    /** Asks for a screen change. It happens once the current frame has finished drawing. */
    void queueScreen(Screen next);
}
