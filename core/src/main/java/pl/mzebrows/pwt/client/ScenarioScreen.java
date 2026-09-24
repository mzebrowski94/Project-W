package pl.mzebrows.pwt.client;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Focus;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.def.ActionKind;

/**
 * Draws one running scenario, and does as little thinking as possible.
 *
 * <p>What the harness keys mean is {@link HarnessControls}; what a command does is the {@link
 * ScenarioPlay} it is handed - {@link ScenarioSession} offline, {@link OnlineSession} online. Both are
 * unit-tested. What is left here needs a GL context and therefore cannot be, which is exactly why there is
 * so little of it.
 *
 * <p>Online, the camera frames only the commanders played on this machine (G0038), and those are drawn
 * facing their players' aim this frame rather than a lead of ticks ago - the cosmetic lead (ADR 0012).
 */
public final class ScenarioScreen extends ScreenAdapter {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);
    private static final ComponentMapper<Shake> SHAKE = ComponentMapper.getFor(Shake.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final Family FOCUSED =
            Family.all(Focus.class, Position.class, PreviousPosition.class).get();
    private static final Family SHAKEN = Family.all(Shake.class, Position.class).get();

    private final GameContext context;
    private final ScenarioPlay play;
    private final Viewport viewport;
    private final LocalAim localAim = new LocalAim();
    private final DebugOverlay overlay;
    private final KeyPresses keys = keycode -> Gdx.input.isKeyJustPressed(keycode);
    private final KeyHolds held = keycode -> Gdx.input.isKeyPressed(keycode);

    @SuppressWarnings("NullAway.Init")
    private WorldRenderer renderer;

    @SuppressWarnings("NullAway.Init")
    private TerrainRenderer terrain;

    @SuppressWarnings("NullAway.Init")
    private InputReader input;

    @SuppressWarnings("NullAway.Init")
    private CameraDirector camera;

    /** Cached: Ashley keeps this view live, so it must not be re-queried per frame. */
    private ImmutableArray<Entity> focused = new ImmutableArray<>(new Array<>());

    /** Everything violent enough for the view to notice. Almost always empty. */
    private ImmutableArray<Entity> shaken = new ImmutableArray<>(new Array<>());

    /** The world the renderer is currently bound to; a restart replaces it with a different one. */
    private @Nullable Simulation bound;

    /** Takes {@code play} over: the screen closes it when it is hidden. */
    public ScenarioScreen(GameContext context, ScenarioPlay play) {
        this.context = context;
        this.play = play;
        GraphicConfig graphicConfig = context.graphicConfig();
        this.viewport = new FitViewport(graphicConfig.virtualWidth(), graphicConfig.virtualHeight());
        this.overlay = new DebugOverlay(context.hud());
    }

    @Override
    public void show() {
        renderer = new WorldRenderer(
                viewport,
                context.graphicConfig().palette(),
                context.graphicConfig().stateTint(),
                context.graphicConfig().chargeMark(),
                context.graphicConfig().bloatMark(),
                context.graphicConfig().nestMark(),
                context.graphicConfig().facingMark(),
                context.graphicConfig().chargedMark(),
                context.graphicConfig().orderMark(),
                context.graphicConfig().heatMark());
        terrain = new TerrainRenderer(
                viewport,
                context.graphicConfig().terrainPalette(),
                context.graphicConfig().groundDetail());
        input = new InputReader(viewport, context.controlsConfig(), context.gamepads());
        camera = new CameraDirector(context.graphicConfig().camera());
        bound = null;
    }

    @Override
    public void render(float delta) {
        // Input is read first, and therefore against the camera as it was when the frame the player
        // reacted to was drawn. Moving the camera before reading would interpret the mouse against a
        // view they have not seen yet.
        PlayerInputs players = input.read(held, keys);
        for (int joined = input.seats().nextNewlySeated();
                joined >= 0;
                joined = input.seats().nextNewlySeated()) {
            play.seat(joined);
        }
        for (HarnessCommand command : HarnessControls.poll(keys)) {
            play.apply(command, players);
        }
        if (input.pauseRequested()) {
            play.apply(HarnessCommand.PAUSE, players);
        }
        if (input.zoomToggled()) {
            camera.toggleWide();
        }
        if (play.leaving()) {
            context.queueScreen(new MenuScreen(context));
        }
        play.advance(delta, players);
        String ended = play.ended();
        if (ended != null) {
            // Online, the game cannot go on: the host has left, the link has dropped, the host said no.
            // Back to the menu, saying why, rather than a world that will never move again.
            Log.info(ended);
            context.queueScreen(new MenuScreen(context, ended));
        }
        Simulation world = play.simulation();
        if (world == null) {
            // Online, before the world has arrived: connecting, joining, or turned away.
            ScreenUtils.clear(context.graphicConfig().backgroundColor());
            String connection = play.connection();
            overlay.renderWaiting(connection == null ? "..." : connection);
            return;
        }
        announceAction();
        announceWave();
        rebindIfRestarted(world);
        aimCamera(world, delta);
        aimAhead(players);

        ScreenUtils.clear(context.graphicConfig().backgroundColor());
        terrain.render();
        renderer.render(play.renderAlpha(), play.cosmeticLead() ? localAim : null);
        if (play.overlayVisible()) {
            String name = play.scenarioName();
            overlay.render(
                    world,
                    Math.max(0, play.slotOf(PlayerSeats.KEYBOARD_SLOT)),
                    name == null ? "" : name,
                    play.paused(),
                    play.lastOrder(),
                    play.connection(),
                    play.invitation());
        }
    }

    /**
     * What each local player is aiming with this frame, by slot, for the cosmetic lead. A seat with no
     * pad in it says nothing, or its idle aim would turn a commander left standing by an unplugged pad.
     */
    private void aimAhead(PlayerInputs players) {
        localAim.clear();
        for (int seat = 0; seat < PlayerSeats.SEATS; seat++) {
            int slot = play.slotOf(seat);
            if (slot >= 0 && (seat == PlayerSeats.KEYBOARD_SLOT || input.seats().taken(seat))) {
                localAim.set(slot, players.get(seat));
            }
        }
    }

    /**
     * Says which wave has just walked in, and how many there are.
     *
     * <p>Scaffolding in exactly the sense the action prompt is: what the level has left to send is
     * something the player is entitled to know (pillar 5), and the console is not where he will
     * eventually read it.
     */
    private void announceWave() {
        int arrived = play.newlyArrivedWave();
        if (arrived == 0) {
            return;
        }
        Log.info("wave " + arrived + "/" + play.totalWaves());
    }

    /**
     * Says what the action key would do, each time that changes.
     *
     * <p>Alpha-phase scaffolding standing in for an on-screen prompt: the console is not where a
     * player looks mid-fight, and pillar 5 wants this beside the commander. What it is standing in
     * for is a label, not a system - the decision is already made in the simulation and published.
     */
    private void announceAction() {
        for (int slot = 0; slot < Players.MAX; slot++) {
            ActionKind offered = play.newlyOfferedAction(slot);
            if (offered == null) {
                continue;
            }
            String who = slot == 0 ? "" : "player " + (slot + 1) + " ";
            Log.info(offered == ActionKind.NONE ? who + "action: nothing in reach" : who + "action: " + offered);
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void hide() {
        renderer.dispose();
        terrain.dispose();
        play.close();
    }

    private void rebindIfRestarted(Simulation current) {
        if (bound != current) {
            renderer.bind(current);
            terrain.bind(current.map());
            focused = current.engine().getEntitiesFor(FOCUSED);
            shaken = current.engine().getEntitiesFor(SHAKEN);
            camera = new CameraDirector(context.graphicConfig().camera());
            bound = current;
        }
    }

    /**
     * Points the view at everything the simulation marked with a {@code Focus} that is this machine's
     * to watch - a commander only if he is played here (G0038) - using the same interpolated positions
     * the renderer will draw at so the view does not lag what is on screen by a tick of its own.
     */
    private void aimCamera(Simulation world, float delta) {
        float alpha = play.renderAlpha();
        camera.begin();
        for (int i = 0; i < focused.size(); i++) {
            Entity entity = focused.get(i);
            if (COMMANDER.has(entity) && !play.local(COMMANDER.get(entity).slot)) {
                continue;
            }
            Position position = POSITION.get(entity);
            PreviousPosition previous = PREVIOUS.get(entity);
            camera.watch(
                    previous.x + (position.x - previous.x) * alpha, previous.y + (position.y - previous.y) * alpha);
        }
        for (int i = 0; i < shaken.size(); i++) {
            Entity entity = shaken.get(i);
            Position at = POSITION.get(entity);
            Shake shake = SHAKE.get(entity);
            camera.shake(at.x, at.y, shake.strength, shake.radius, shake.fade());
        }
        camera.heading(input.headingX(), input.headingY());

        TileMap map = world.map();
        camera.update(viewport.getWorldWidth(), viewport.getWorldHeight(), map.worldWidth(), map.worldHeight(), delta);
        if (viewport.getCamera() instanceof OrthographicCamera lens) {
            lens.zoom = camera.zoom();
            lens.position.set(camera.centreX(), camera.centreY(), 0f);
            lens.update();
        }
    }
}
