package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.sim.SimConfig;

/**
 * Application entry point: owns everything that outlives a single screen - configuration, assets and
 * the shared text renderer - and opens the scenario menu.
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
    private NetConfig netConfig;
    private ControlsConfig controlsConfig;
    private GamepadSource gamepads;
    private Assets assets;
    private Hud hud;

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
        simConfig = SimConfigLoader.load();
        Log.debug("Simulation config: " + simConfig);
        netConfig = NetConfigLoader.load();
        controlsConfig = ControlsConfig.load();
        gamepads = new ChosenGamepads(new GdxGamepads(controlsConfig.hardware()), launchOptions.pads());

        assets = new Assets();
        assets.queueAll();
        assets.finishLoading();

        hud = new Hud(graphicConfig.layout(), graphicConfig.hud().reticleSize(), pointer);
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        setScreen(new MenuScreen(this));
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
    public void dispose() {
        if (getScreen() != null) {
            getScreen().hide();
        }
        hud.dispose();
        assets.dispose();
        Log.info("Project-W stopped");
    }
}
