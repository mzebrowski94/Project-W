package pl.mzebrows.pwt.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import pl.mzebrows.pwt.sim.FixedStepClock;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;

/**
 * Application entry point: owns the simulation, the viewport and the renderer, and drives the
 * fixed-step loop.
 *
 * <p>Fields are created in {@link #create()} because they need a GL context, which does not exist
 * when this object is constructed - hence the class-level NullAway.Init suppression.
 */
@SuppressWarnings("NullAway.Init")
public final class PwtGame extends ApplicationAdapter {

    private final GraphicConfig graphicConfig;

    private SimConfig simConfig;
    private Simulation simulation;
    private FixedStepClock clock;
    private Viewport viewport;
    private WorldRenderer renderer;
    private Assets assets;

    public PwtGame(GraphicConfig graphicConfig) {
        this.graphicConfig = graphicConfig;
    }

    @Override
    public void create() {
        Log.info("Starting Project-W");
        simConfig = SimConfigLoader.load();
        Log.debug("Simulation config: " + simConfig);

        assets = new Assets();
        assets.queueAll();
        assets.finishLoading();

        simulation = new Simulation(simConfig);
        clock = new FixedStepClock(simConfig);
        viewport = new FitViewport(graphicConfig.virtualWidth(), graphicConfig.virtualHeight());
        renderer = new WorldRenderer(simulation, viewport);

        spawnPlaceholderWorld();
        renderer.start();
    }

    @Override
    public void render() {
        int steps = clock.stepsFor(Gdx.graphics.getDeltaTime());
        for (int i = 0; i < steps; i++) {
            simulation.step();
        }
        ScreenUtils.clear(graphicConfig.backgroundColor());
        renderer.render(clock.alpha());
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        renderer.dispose();
        assets.dispose();
        Log.info("Project-W stopped after " + simulation.tick() + " simulation steps");
    }

    /** Temporary: something to look at until real gameplay exists. */
    private void spawnPlaceholderWorld() {
        Rng rng = simulation.rng();
        for (int i = 0; i < 24; i++) {
            simulation.spawn(
                    rng.nextFloat(0f, simConfig.worldWidth()),
                    rng.nextFloat(0f, simConfig.worldHeight()),
                    rng.nextFloat(-12f, 12f),
                    rng.nextFloat(-12f, 12f),
                    rng.nextFloat(0.8f, 2.4f));
        }
    }
}
