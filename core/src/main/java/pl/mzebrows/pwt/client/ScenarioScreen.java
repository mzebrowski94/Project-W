package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.scenario.Scenario;

/**
 * Draws one running scenario, and does as little thinking as possible.
 *
 * <p>What the harness keys mean is {@link HarnessControls}; what a command does is {@link
 * ScenarioSession}. Both are unit-tested. What is left here needs a GL context and therefore cannot
 * be, which is exactly why there is so little of it.
 */
public final class ScenarioScreen extends ScreenAdapter {

    private final GameContext context;
    private final Scenario scenario;
    private final Viewport viewport;
    private final DebugOverlay overlay;
    private final KeyPresses keys = keycode -> Gdx.input.isKeyJustPressed(keycode);

    @SuppressWarnings("NullAway.Init")
    private ScenarioSession session;

    @SuppressWarnings("NullAway.Init")
    private WorldRenderer renderer;

    @SuppressWarnings("NullAway.Init")
    private InputReader input;

    /** The world the renderer is currently bound to; a restart replaces it with a different one. */
    private @Nullable Simulation bound;

    public ScenarioScreen(GameContext context, Scenario scenario) {
        this.context = context;
        this.scenario = scenario;
        GraphicConfig graphicConfig = context.graphicConfig();
        this.viewport = new FitViewport(graphicConfig.virtualWidth(), graphicConfig.virtualHeight());
        this.overlay = new DebugOverlay(context.hud(), scenario);
    }

    @Override
    public void show() {
        renderer = new WorldRenderer(viewport, context.graphicConfig().palette());
        input = new InputReader(viewport);
        session = new ScenarioSession(scenario, context.simConfig());
        bound = null;
    }

    @Override
    public void render(float delta) {
        CommanderInput commanderInput = input.read();
        for (HarnessCommand command : HarnessControls.poll(keys)) {
            session.apply(command, commanderInput);
        }
        if (session.leaving()) {
            context.queueScreen(new MenuScreen(context));
        }
        session.advance(delta, commanderInput);
        rebindIfRestarted();

        ScreenUtils.clear(context.graphicConfig().backgroundColor());
        renderer.render(session.renderAlpha());
        if (session.overlayVisible()) {
            overlay.render(session.simulation(), session.paused());
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void hide() {
        renderer.dispose();
    }

    private void rebindIfRestarted() {
        Simulation current = session.simulation();
        if (bound != current) {
            renderer.bind(current);
            bound = current;
        }
    }
}
