package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import java.security.SecureRandom;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.config.NetConfigLoader;
import pl.mzebrows.pwt.client.config.SimConfigLoader;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.hud.Hud;
import pl.mzebrows.pwt.client.hud.SystemPointer;
import pl.mzebrows.pwt.client.input.ChosenGamepads;
import pl.mzebrows.pwt.client.input.ControlsConfig;
import pl.mzebrows.pwt.client.input.GamepadSource;
import pl.mzebrows.pwt.client.input.GdxGamepads;
import pl.mzebrows.pwt.client.play.MissionChoices;
import pl.mzebrows.pwt.client.play.MissionCode;
import pl.mzebrows.pwt.client.play.MissionRecords;
import pl.mzebrows.pwt.client.play.ScenarioSession;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.scenario.ProvingSetup;
import pl.mzebrows.pwt.ui.nav.SelectionMemory;
import pl.mzebrows.pwt.ui.page.UiPage;

/**
 * Application entry point: owns everything that outlives a single screen - configuration, assets and
 * the shared text renderer - and opens the scenario menu, or the mission {@code --mission} named.
 *
 * <p>Fields are created in {@link #create()} because they need a GL context, which does not exist
 * when this object is constructed - hence the class-level NullAway.Init suppression.
 */
@SuppressWarnings("NullAway.Init")
public final class PwtGame extends Game implements GameContext {

    private final GraphicConfig graphicConfig;
    private final LaunchOptions launchOptions;
    private final SystemPointer pointer;

    private SimConfig simConfig;
    private String worldConfigText;
    private NetConfig netConfig;
    private ControlsConfig controlsConfig;
    private GamepadSource gamepads;
    private Assets assets;
    private Hud hud;

    private final SelectionMemory selections = new SelectionMemory();
    private ProvingSetup provingSetup = ProvingSetup.first();
    private final MissionChoices missions = new MissionChoices(Clock.systemUTC(), new SecureRandom()::nextLong);
    private @Nullable MissionRecords missionRecords;

    private @Nullable Screen pendingScreen;

    public PwtGame(GraphicConfig graphicConfig, LaunchOptions launchOptions, SystemPointer pointer) {
        this.graphicConfig = graphicConfig;
        this.launchOptions = launchOptions;
        this.pointer = pointer;
    }

    @Override
    public void create() {
        Log.info("Starting Project-W, instance " + launchOptions.instance() + ", pads " + launchOptions.pads());
        for (String problem : launchOptions.problems()) {
            Log.error("Command line: " + problem, null);
        }
        SimConfigLoader.Loaded loaded = SimConfigLoader.loaded();
        simConfig = loaded.config();
        worldConfigText = loaded.shipped();
        Log.debug("Simulation config: " + simConfig);
        netConfig = NetConfigLoader.load();
        controlsConfig = ControlsConfig.load();
        gamepads = new ChosenGamepads(new GdxGamepads(controlsConfig.pad()), launchOptions.pads());

        assets = new Assets();
        assets.queueAll();
        assets.finishLoading();

        hud = new Hud(graphicConfig.layout(), graphicConfig.hud().reticle().size(), pointer);
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        MissionCode mission = launchOptions.mission();
        if (mission == null) {
            setScreen(new MenuScreen(this));
        } else {
            // A developer's way in; leaving it lands where a player would have started it.
            Log.info("Starting mission " + mission);
            missions.played(mission);
            setScreen(new ScenarioScreen(
                    this,
                    new ScenarioSession(mission.scenario(), simConfig.withSeed(mission.seed())),
                    UiPage.MISSIONS));
        }
    }

    /**
     * Switches screen at the end of the current frame instead of immediately.
     *
     * <p>{@code setScreen} calls the outgoing screen's {@code hide()} synchronously, and a screen
     * releases its GL resources there. Calling it from inside that screen's own {@code render} frees
     * a buffer the rest of the frame is still drawing with, which crashes the JVM outright rather
     * than throwing - there is no stack trace to read, just an access violation.
     *
     * <p>So screens never switch themselves. They ask, the frame finishes, and the swap happens here
     * where nothing is mid-draw.
     */
    @Override
    public void queueScreen(Screen next) {
        pendingScreen = next;
    }

    @Override
    public void render() {
        super.render();
        Screen next = pendingScreen;
        if (next != null) {
            pendingScreen = null;
            setScreen(next);
        }
    }

    /**
     * The window lost focus. Offline, a level opens its pause menu - an alt-tab mid-fight would otherwise
     * cost the player his man; online nothing stops for one player's alt-tab.
     */
    public void focusLost() {
        if (getScreen() instanceof ScenarioScreen level) {
            level.focusLost();
        }
    }

    @Override
    public void resize(int width, int height) {
        hud.resize(width, height);
        super.resize(width, height);
    }

    @Override
    public GraphicConfig graphicConfig() {
        return graphicConfig;
    }

    @Override
    public SimConfig simConfig() {
        return simConfig;
    }

    @Override
    public String worldConfigText() {
        return worldConfigText;
    }

    @Override
    public NetConfig netConfig() {
        return netConfig;
    }

    @Override
    public LaunchOptions launchOptions() {
        return launchOptions;
    }

    @Override
    public ControlsConfig controlsConfig() {
        return controlsConfig;
    }

    @Override
    public GamepadSource gamepads() {
        return gamepads;
    }

    @Override
    public Hud hud() {
        return hud;
    }

    @Override
    public SelectionMemory selections() {
        return selections;
    }

    @Override
    public ProvingSetup provingSetup() {
        return provingSetup;
    }

    @Override
    public void provingSetup(ProvingSetup setup) {
        provingSetup = setup;
    }

    @Override
    public MissionChoices missions() {
        return missions;
    }

    @Override
    public MissionRecords missionRecords() {
        MissionRecords read = missionRecords;
        if (read == null) {
            // Read on first use, once the application - and so its preferences - exists.
            read = LocalMissionRecords.load();
            missionRecords = read;
        }
        return read;
    }

    @Override
    public void dispose() {
        if (getScreen() != null) {
            getScreen().hide();
        }
        hud.dispose();
        assets.dispose();
        Log.info("Project-W stopped");
    }
}
