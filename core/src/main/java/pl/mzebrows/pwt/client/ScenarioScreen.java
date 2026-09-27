package pl.mzebrows.pwt.client;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Focus;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.ui.hud.GoalLine;
import pl.mzebrows.pwt.ui.hud.PanelAlarms;
import pl.mzebrows.pwt.ui.hud.PanelAssignment;
import pl.mzebrows.pwt.ui.hud.PlayerReporter;
import pl.mzebrows.pwt.ui.hud.Posture;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.hud.ReadoutWriter;
import pl.mzebrows.pwt.ui.hud.Reporters;
import pl.mzebrows.pwt.ui.hud.SquadReporter;
import pl.mzebrows.pwt.ui.hud.Terminal;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;

/**
 * Draws one running scenario, and does as little thinking as possible.
 *
 * <p>What the harness keys mean is {@link HarnessControls}; what a command does is the {@link
 * ScenarioPlay} it is handed - {@link ScenarioSession} offline, {@link OnlineSession} online. Both are
 * unit-tested. What is left here needs a GL context and therefore cannot be, which is exactly why there is
 * so little of it.
 *
 * <p>The screen is letterboxed ({@link ScreenLayout}): the world is drawn only into the view between the
 * two bars, and the bars are the interface's.
 *
 * <p>Online, the camera frames only the commanders played on this machine (G0038), and those are drawn
 * facing their players' aim this frame rather than a lead of ticks ago - the cosmetic lead (ADR 0012).
 */
public final class ScenarioScreen extends ScreenAdapter {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);
    private static final ComponentMapper<Shake> SHAKE = ComponentMapper.getFor(Shake.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Facing> FACING = ComponentMapper.getFor(Facing.class);
    private static final Family FOCUSED =
            Family.all(Focus.class, Position.class, PreviousPosition.class).get();
    private static final Family SHAKEN = Family.all(Shake.class, Position.class).get();
    private static final Family COMMANDERS = Family.all(
                    Commander.class, Facing.class, Position.class, PreviousPosition.class)
            .get();

    private final GameContext context;
    private final ScenarioPlay play;
    private final LetterboxViewport viewport = new LetterboxViewport();
    private final LocalAim localAim = new LocalAim();
    private final DebugOverlay overlay;
    private final Readout readout = new Readout();
    private final ReadoutWriter readoutWriter = new ReadoutWriter();
    private final GoalLine goalLine;
    private final Terminal terminal;

    /**
     * Everything that narrates the level: the terminal's reporters, one per fact, and the console's
     * action prompt. This list is the whole registry (plan 8.5).
     */
    private final Reporters reporters;

    private final PanelAlarms alarms;
    private final PanelAssignment seating = new PanelAssignment();
    private final int[] slotOfSeat = new int[PlayerSeats.SEATS];
    private final boolean[] connected = new boolean[Players.MAX];

    /** Whether the bottom bar shows the roll call instead of the players' panels. This machine's view. */
    private boolean rollCall;

    private final UiStrings strings;
    private final TopBarPainter topBar;
    private final BottomBarPainter bottomBar;
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

    @SuppressWarnings("NullAway.Init")
    private PlayMenus menus;

    /** Cached: Ashley keeps this view live, so it must not be re-queried per frame. */
    private ImmutableArray<Entity> focused = new ImmutableArray<>(new Array<>());

    /** Every commander, for the pad players' reticles. */
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    /** Scratch for projecting a reticle into the view; overwritten before every read. */
    private final Vector2 aimPoint = new Vector2();

    /** Everything violent enough for the view to notice. Almost always empty. */
    private ImmutableArray<Entity> shaken = new ImmutableArray<>(new Array<>());

    /** The world the renderer is currently bound to; a restart replaces it with a different one. */
    private @Nullable Simulation bound;

    /** Takes {@code play} over: the screen closes it when it is hidden. */
    public ScenarioScreen(GameContext context, ScenarioPlay play) {
        this.context = context;
        this.play = play;
        GraphicConfig graphicConfig = context.graphicConfig();
        this.overlay = new DebugOverlay();
        HudStyle style = graphicConfig.hud();
        this.strings = UiStrings.load();
        this.rollCall = style.rollCallAtStart();
        this.goalLine = new GoalLine(style.goalHoldSeconds(), style.pulseHz(), style.flashScale());
        this.terminal = new Terminal(graphicConfig.terminal(), new DispatchText(strings));
        this.reporters = new Reporters(List.of(
                new PlayerReporter(),
                new SquadReporter(graphicConfig.terminal().fewTroopers()),
                new ActionPrompts(play::local, Log::info)));
        this.alarms = new PanelAlarms(
                style.alarmSeconds(), style.alarmShake(), style.pulseHz(), style.motionScale(), style.flashScale());
        this.topBar = new TopBarPainter(
                context.hud(), strings, style, graphicConfig.layout().metrics());
        this.bottomBar = new BottomBarPainter(
                context.hud(), strings, style, graphicConfig.layout().metrics(), graphicConfig.palette());
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
        menus = new PlayMenus(
                context.hud(),
                context.graphicConfig().hud(),
                context.graphicConfig().layout().metrics(),
                context.controlsConfig(),
                strings,
                context.gamepads());
        // The press that started the level - Enter, Space, a click, SOUTH - is spent, not fired.
        input.spendHeldPresses();
        bound = null;
    }

    /** The window lost focus: offline, the pause menu opens (plan Q7). */
    public void focusLost() {
        menus.focusLost();
    }

    @Override
    public void render(float delta) {
        // Input is read first, and therefore against the camera as it was when the frame the player
        // reacted to was drawn. Moving the camera before reading would interpret the mouse against a
        // view they have not seen yet.
        PlayerInputs players = input.read(held, keys, menus.menuOpen());
        for (int joined = input.seats().nextNewlySeated();
                joined >= 0;
                joined = input.seats().nextNewlySeated()) {
            play.seat(joined);
        }
        ScreenLayout layout = context.hud().layout();
        if (play.simulation() == null) {
            menus.updateConnecting(delta, play, players, keys, held, input, layout);
        } else if (menus.update(delta, play, input, players, keys, held, layout)) {
            input.spendHeldPresses();
        }
        for (HarnessCommand command : HarnessControls.poll(keys)) {
            play.apply(command, players);
        }
        if (input.zoomToggled()) {
            camera.toggleWide();
        }
        if (input.rollCallToggled()) {
            rollCall = !rollCall;
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
            context.hud().showSystemCursor();
            ScreenUtils.clear(context.graphicConfig().hud().bar());
            String connection = play.connection();
            menus.drawConnecting(layout, connection == null ? "..." : connection);
            return;
        }
        rebindIfRestarted(world);
        aimCamera(world, delta);
        aimAhead(players);

        String name = play.scenarioName();
        readoutWriter.fill(world, name == null ? "" : name, play.planet(), readout);
        goalLine.update(readout, delta);
        reporters.report(readout, terminal);
        terminal.advance(delta);
        alarms.update(readout, delta);
        seatPanels();

        clearLetterbox();
        terrain.render();
        renderer.render(play.renderAlpha(), play.cosmeticLead() ? localAim : null);
        OverlayMode mode = play.overlayMode();
        List<String> lines = mode == OverlayMode.OFF
                ? List.of()
                : overlay.lines(
                        world,
                        Math.max(0, play.slotOf(PlayerSeats.KEYBOARD_SLOT)),
                        name == null ? "" : name,
                        play.paused(),
                        play.lastOrder(),
                        play.connection(),
                        play.invitation());
        topBar.render(layout, readout, goalLine, terminal, lines, mode);
        if (rollCall) {
            bottomBar.renderRollCall(layout);
        } else {
            bottomBar.render(layout, readout, seating, connected, alarms);
        }
        menus.draw(layout, play);
        drawReticles(play.renderAlpha());
    }

    /**
     * The game's own aim marks (plan 7.5), last, over everything - a bar included, since the pointer
     * still aims from there. Each is optional: the keyboard player's sits on the mouse and hides the
     * system pointer; a pad player's sits a fixed distance along where his commander faces, which is
     * where he aims (G0002). Pad reticles are drawn only for a commander on his feet.
     */
    private void drawReticles(float alpha) {
        HudStyle style = context.graphicConfig().hud();
        Hud hud = context.hud();
        if (menus.menuOpen()) {
            // In a menu the pointer is the menu's, and nobody is aiming.
            if (menus.wantsPointer()) {
                hud.showSystemCursor();
            } else {
                hud.hideSystemCursor();
            }
            return;
        }
        int keyboard = play.slotOf(PlayerSeats.KEYBOARD_SLOT);
        if (style.keyboardReticle() && keyboard >= 0) {
            hud.hideSystemCursor();
            hud.reticle(Gdx.input.getX(), Gdx.graphics.getHeight() - 1 - Gdx.input.getY(), colourOf(keyboard));
        } else {
            hud.showSystemCursor();
        }
        if (!style.padReticles()) {
            return;
        }
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            int slot = COMMANDER.get(commander).slot;
            if (!onALocalPad(slot) || readout.player(slot).posture != Posture.STANDING) {
                continue;
            }
            Facing facing = FACING.get(commander);
            if (facing.x == 0f && facing.y == 0f) {
                continue;
            }
            Position position = POSITION.get(commander);
            PreviousPosition previous = PREVIOUS.get(commander);
            float x = previous.x + (position.x - previous.x) * alpha;
            float y = previous.y + (position.y - previous.y) * alpha;
            aimPoint.set(x + facing.x * style.padReticleDistance(), y + facing.y * style.padReticleDistance());
            viewport.project(aimPoint);
            hud.reticle(Math.round(aimPoint.x), Math.round(aimPoint.y), colourOf(slot));
        }
    }

    /**
     * Who has a panel, and whose is grey. A local seat whose pad is unplugged is offline; his commander
     * stays on the field. Whether a player on another machine is still connected is not published to
     * this one yet, so remote players read as connected.
     */
    private void seatPanels() {
        Arrays.fill(connected, true);
        for (int seat = 0; seat < PlayerSeats.SEATS; seat++) {
            int slot = play.slotOf(seat);
            slotOfSeat[seat] = slot;
            boolean padGone = seat != PlayerSeats.KEYBOARD_SLOT
                    && input.seats().taken(seat)
                    && input.seats().padIn(seat) == null;
            if (slot >= 0 && slot < Players.MAX && padGone) {
                connected[slot] = false;
            }
        }
        seating.update(readout, slotOfSeat);
    }

    private boolean onALocalPad(int slot) {
        for (int seat = PlayerSeats.KEYBOARD_SLOT + 1; seat < PlayerSeats.SEATS; seat++) {
            if (play.slotOf(seat) == slot && input.seats().taken(seat)) {
                return true;
            }
        }
        return false;
    }

    private Color colourOf(int slot) {
        return context.graphicConfig().palette().colorFor(Look.COMMANDER, readout.player(slot).variant);
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

    @Override
    public void resize(int width, int height) {
        ScreenLayout layout = context.hud().layout();
        viewport.fit(layout.toScreen(layout.view()), context.graphicConfig().virtualHeight());
    }

    /**
     * Black everywhere - bars, pillars and the odd pixels round the canvas - then the ground colour
     * inside the view only, so where the map ends reads as ground beyond the map and never as the
     * letterbox.
     */
    private void clearLetterbox() {
        ScreenUtils.clear(context.graphicConfig().hud().bar());
        ScreenLayout layout = context.hud().layout();
        Rect view = layout.toScreen(layout.view());
        if (view.isEmpty()) {
            return;
        }
        Color ground = context.graphicConfig().backgroundColor();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(view.x(), view.y(), view.width(), view.height());
        Gdx.gl.glClearColor(ground.r, ground.g, ground.b, ground.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    @Override
    public void hide() {
        context.hud().showSystemCursor();
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
            commanders = current.engine().getEntitiesFor(COMMANDERS);
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
