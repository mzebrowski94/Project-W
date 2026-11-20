package pl.mzebrows.pwt.client;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.draw.ActionBrackets;
import pl.mzebrows.pwt.client.draw.CameraDirector;
import pl.mzebrows.pwt.client.draw.Drawn;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.draw.LetterboxViewport;
import pl.mzebrows.pwt.client.draw.LocalAim;
import pl.mzebrows.pwt.client.draw.Palette;
import pl.mzebrows.pwt.client.draw.TerrainRenderer;
import pl.mzebrows.pwt.client.draw.WorldRenderer;
import pl.mzebrows.pwt.client.hud.ActionChips;
import pl.mzebrows.pwt.client.hud.BottomBarPainter;
import pl.mzebrows.pwt.client.hud.CrateHints;
import pl.mzebrows.pwt.client.hud.DebugOverlay;
import pl.mzebrows.pwt.client.hud.DispatchText;
import pl.mzebrows.pwt.client.hud.DraftHints;
import pl.mzebrows.pwt.client.hud.HeldUpgradesPainter;
import pl.mzebrows.pwt.client.hud.Hud;
import pl.mzebrows.pwt.client.hud.PromptKeys;
import pl.mzebrows.pwt.client.hud.ToastPainter;
import pl.mzebrows.pwt.client.hud.TopBarPainter;
import pl.mzebrows.pwt.client.hud.UiStrings;
import pl.mzebrows.pwt.client.input.ControlsConfig;
import pl.mzebrows.pwt.client.input.InputReader;
import pl.mzebrows.pwt.client.input.KeyHolds;
import pl.mzebrows.pwt.client.input.KeyPresses;
import pl.mzebrows.pwt.client.input.PlayerSeats;
import pl.mzebrows.pwt.client.play.HarnessCommand;
import pl.mzebrows.pwt.client.play.MissionCode;
import pl.mzebrows.pwt.client.play.OnlineSession;
import pl.mzebrows.pwt.client.play.OverlayMode;
import pl.mzebrows.pwt.client.play.ScenarioPlay;
import pl.mzebrows.pwt.client.play.ScenarioSession;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.Strength;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Focus;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.ui.hud.DispatchKind;
import pl.mzebrows.pwt.ui.hud.DispatchSink;
import pl.mzebrows.pwt.ui.hud.GoalLine;
import pl.mzebrows.pwt.ui.hud.PanelAlarms;
import pl.mzebrows.pwt.ui.hud.PanelAssignment;
import pl.mzebrows.pwt.ui.hud.PlayerReporter;
import pl.mzebrows.pwt.ui.hud.Posture;
import pl.mzebrows.pwt.ui.hud.Prompts;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.hud.ReadoutWriter;
import pl.mzebrows.pwt.ui.hud.Reporters;
import pl.mzebrows.pwt.ui.hud.SquadReporter;
import pl.mzebrows.pwt.ui.hud.Terminal;
import pl.mzebrows.pwt.ui.hud.Toast;
import pl.mzebrows.pwt.ui.hud.TopBarSeats;
import pl.mzebrows.pwt.ui.hud.UpgradeReporter;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;
import pl.mzebrows.pwt.ui.page.UiPage;

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

    /** The menu page leaving this level goes back to: the one it was started from (G0066). */
    private final UiPage back;

    /** The session went back to its briefing and the menu has it now: this screen no longer closes it (G0101). */
    private boolean handedOver;

    private final LetterboxViewport viewport = new LetterboxViewport();
    private final LocalAim localAim = new LocalAim();
    private final DebugOverlay overlay;

    /** Every look's colours, built once from the config rather than on each frame that asks. */
    private final Palette palette;

    private final Readout readout = new Readout();
    private final ReadoutWriter readoutWriter = new ReadoutWriter();
    private final GoalLine goalLine;
    private final Terminal terminal;

    /** An upgrade earned, passed on or taken, or an adaptation, over the view: the terminal says it too, this is not missed. */
    private final Toast toast;

    /** Where the reporters file: the terminal and the toast. */
    private final DispatchSink dispatches;

    /** Everything that narrates the level: the terminal's reporters, one per fact. This list is the whole registry (plan 8.5). */
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

    /** Every upgrade held, beside the pause menu: more than the aside has room for. */
    private final HeldUpgradesPainter heldUpgrades;

    private final ToastPainter toastPainter;

    /** The wheel's notches since the last frame, down for positive. */
    private float wheelNotches;

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

    /**
     * The top bar and the seats that share it: the draft or the crate on show - one per machine - who drives
     * it, and the pause a solo player's draft makes (G0049, G0056).
     */
    private final TopBarSeats topBarSeats = new TopBarSeats(PlayerSeats.SEATS);

    private final CrateHints crateHints;

    private final DraftHints draftHints;

    /** Whether a slot is played on this machine; held once, so asking it allocates nothing. */
    private final IntPredicate localSlot;

    /** Each local commander's one prompt, and the brackets and chips that show it (G0098). */
    private final Prompts prompts;

    private final ActionBrackets brackets;

    private final ActionChips chips;

    /** By seat, the keys its prompt names; by slot, the seat's for a slot played here, else null. */
    private final PromptKeys[] seatKeys = new PromptKeys[PlayerSeats.SEATS];

    private final @Nullable PromptKeys[] slotKeys = new PromptKeys[Players.MAX];

    /** By slot, what that player presses to open his drafts; empty for a slot not played here. */
    private final String[] draftKeys = new String[Players.MAX];

    /** The world the renderer is currently bound to; a restart replaces it with a different one. */
    private @Nullable Simulation bound;

    /** The world {@link #missionLine} was worked out for; a restart or a heal replaces it. */
    private @Nullable Simulation named;

    /** What the top bar calls the level: a mission's tier, {@code DAILY} before a daily's, else its name. */
    private String missionLine = "";

    /** Takes {@code play} over: the screen closes it when it is hidden. */
    public ScenarioScreen(GameContext context, ScenarioPlay play) {
        this(context, play, UiPage.MAIN);
    }

    /**
     * A level that goes back to {@code back} when it is left - the proving ground's page for a setup
     * fought from it, so trying again is one confirm (G0060).
     */
    public ScenarioScreen(GameContext context, ScenarioPlay play, UiPage back) {
        this.context = context;
        this.play = play;
        this.back = back;
        GraphicConfig graphicConfig = context.graphicConfig();
        this.overlay = new DebugOverlay();
        this.palette = graphicConfig.palette();
        this.strings = UiStrings.load();
        this.rollCall = graphicConfig.hud().rollCall();
        this.goalLine = new GoalLine(
                graphicConfig.hud().goal().minHoldSeconds(),
                graphicConfig.hud().alert().pulseHz(),
                graphicConfig.ui().flashScale());
        this.terminal = new Terminal(graphicConfig.hud().terminal(), new DispatchText(strings));
        this.toast = new Toast(graphicConfig.toast(), new DispatchText(strings));
        this.dispatches = DispatchSink.both(terminal, toast);
        this.reporters = new Reporters(List.of(
                new PlayerReporter(),
                new SquadReporter(graphicConfig.hud().terminal().fewTroopers()),
                new UpgradeReporter()));
        this.draftHints = new DraftHints(context.controlsConfig());
        this.crateHints = new CrateHints(context.controlsConfig());
        this.localSlot = play::local;
        this.prompts = new Prompts(graphicConfig.hud().upgrade().promptSeconds());
        this.brackets = new ActionBrackets(
                graphicConfig.action(),
                graphicConfig.tree(),
                graphicConfig.actionMark(),
                graphicConfig.pickupMark(),
                readout,
                localSlot);
        this.chips = new ActionChips(
                context.hud(), strings, graphicConfig, graphicConfig.layout().metrics(), palette);
        for (int seat = 0; seat < PlayerSeats.SEATS; seat++) {
            seatKeys[seat] = new PromptKeys(context.controlsConfig());
        }
        this.alarms = new PanelAlarms(
                graphicConfig.hud().panel().alarmSeconds(),
                graphicConfig.hud().panel().alarmShake(),
                graphicConfig.hud().alert().pulseHz(),
                graphicConfig.ui().motionScale(),
                graphicConfig.ui().flashScale());
        this.topBar = new TopBarPainter(
                context.hud(), strings, graphicConfig, graphicConfig.layout().metrics(), palette);
        Arrays.fill(draftKeys, "");
        this.heldUpgrades = new HeldUpgradesPainter(
                context.hud(), strings, graphicConfig, graphicConfig.layout().metrics(), palette);
        this.bottomBar = new BottomBarPainter(
                context.hud(), strings, graphicConfig, graphicConfig.layout().metrics(), palette);
        this.toastPainter = new ToastPainter(
                context.hud(), graphicConfig, graphicConfig.layout().metrics(), palette);
    }

    @Override
    public void show() {
        renderer = new WorldRenderer(
                viewport,
                palette,
                context.graphicConfig().state(),
                context.graphicConfig().charge(),
                context.graphicConfig().bloat(),
                context.graphicConfig().nest(),
                context.graphicConfig().facing(),
                context.graphicConfig().segments(),
                context.graphicConfig().charged(),
                context.graphicConfig().order(),
                context.graphicConfig().heat(),
                context.graphicConfig().wound(),
                context.graphicConfig().shield(),
                context.graphicConfig().crate(),
                context.graphicConfig().door(),
                context.graphicConfig().post(),
                context.graphicConfig().flag(),
                context.graphicConfig().burst(),
                context.graphicConfig().daze(),
                context.graphicConfig().tree(),
                context.graphicConfig().boulder(),
                context.graphicConfig().sling(),
                context.graphicConfig().shadow(),
                context.graphicConfig().pop(),
                context.graphicConfig().geyser(),
                context.graphicConfig().slither(),
                context.graphicConfig().burrow(),
                context.graphicConfig().devour(),
                context.graphicConfig().limpet(),
                context.graphicConfig().ui().motionScale());
        terrain = new TerrainRenderer(
                viewport,
                context.graphicConfig().terrainPalette(),
                context.graphicConfig().groundDetail());
        input = new InputReader(viewport, context.controlsConfig(), context.gamepads());
        camera = new CameraDirector(context.graphicConfig().camera());
        menus = new PlayMenus(
                context.hud(),
                context.graphicConfig(),
                context.graphicConfig().layout().metrics(),
                context.controlsConfig(),
                strings,
                context.gamepads(),
                context.missionRecords(),
                context.settings());
        // The press that started the level - Enter, Space, a click, SOUTH - is spent, not fired.
        input.spendHeldPresses();
        bound = null;
        // Only the wheel is an event in libGDX; everything else here is polled.
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                wheelNotches += amountY;
                return true;
            }
        });
    }

    /**
     * How far down any pad's aiming stick is pushed, past its dead zone - what scrolls the pause menu's
     * list of upgrades (A.1). The stick that aims in a level is idle in a menu, so it is free for this.
     */
    private float rightStickDown() {
        ControlsConfig controls = context.controlsConfig();
        float most = 0f;
        for (int i = 0; i < context.gamepads().count(); i++) {
            // The aiming axis reads up as positive; a list scrolls down for positive.
            float down = -context.gamepads().get(i).axis(controls.pad().aimY());
            if (Math.abs(down) > controls.pad().aimDeadZone() && Math.abs(down) > Math.abs(most)) {
                most = down;
            }
        }
        return most;
    }

    /**
     * Works out, once a world, whether it is a mission and whether a daily (G0065) - on a joined machine too,
     * from the scenario and the seed the session carries - for the top bar, the pause page and the result.
     */
    private void name(Simulation world) {
        named = world;
        String scenario = play.scenarioName();
        MissionCode mission = scenario == null
                ? null
                : MissionCode.of(
                        scenario,
                        world.config().seed(),
                        world.config().mission().biomes());
        Strength strength = scenario == null ? Strength.NORMAL : MissionCode.strengthOf(scenario);
        // A daily played at another strength is not the day's: no best time is kept for it (G0106).
        LocalDate day = mission == null || !strength.normal()
                ? null
                : context.missions().dailyDay(mission);
        String strengthWords = mission == null || strength.normal()
                ? ""
                : strings.format(
                        "ui.briefing.strength",
                        world.config().mission().healthPercentOf(mission.tier(), strength),
                        world.config().mission().countPercentOf(mission.tier(), strength));
        menus.mission(mission, day, strengthWords);
        if (mission == null) {
            missionLine = scenario == null ? "" : scenario;
            return;
        }
        String tier = strings.get("ui.tier." + mission.tier()) + "  "
                + strings.format("ui.missions.biomes", mission.length());
        if (!strengthWords.isEmpty()) {
            tier += "  " + strengthWords;
        }
        missionLine = day == null ? tier : strings.format("ui.hud.missionDaily", tier);
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
        boolean menuOpen = menus.menuOpen();
        PlayerInputs players = input.read(held, keys);
        topBarSeats.route(players, input.draftPressed(), input.crateClosePressed(), menuOpen);
        for (int joined = input.seats().nextNewlySeated();
                joined >= 0;
                joined = input.seats().nextNewlySeated()) {
            play.seat(joined);
        }
        ScreenLayout layout = context.hud().layout();
        if (play.simulation() == null) {
            menus.updateConnecting(delta, play, players, keys, held, input, layout);
        } else if (menus.update(delta, play, input, players, keys, held, layout, readout)) {
            input.spendHeldPresses();
        }
        topBarSeats.work(players, readout, menus.menuOpen(), play);
        menus.quietPause(topBarSeats.quietPause(play));
        for (HarnessCommand command : HarnessControls.poll(keys)) {
            play.apply(command, players);
        }
        if (input.zoomToggled() && !menuOpen) {
            camera.toggleWide();
        }
        if (input.rollCallToggled()) {
            rollCall = !rollCall;
        }
        if (play.leaving()) {
            context.queueScreen(new MenuScreen(context, "", back));
        }
        play.advance(delta, players);
        if (!handedOver && play instanceof OnlineSession online && online.onBriefing()) {
            // The host took every machine back to the briefing, or this machine joined a session still on it.
            handedOver = true;
            context.queueScreen(new MenuScreen(context, "", online));
            return;
        }
        String ended = play.ended();
        if (ended != null) {
            // Online, the game cannot go on: the host has left, the link has dropped, the host said no.
            // Back to the main page, saying why, rather than a world that will never move again.
            Log.info(ended);
            context.queueScreen(new MenuScreen(context, ended));
        }
        Simulation world = play.simulation();
        if (world == null) {
            // Online, before the world has arrived: connecting, joining, or turned away.
            context.hud().showSystemCursor();
            ScreenUtils.clear(context.graphicConfig().hud().color().bar());
            String connection = play.reconnecting() ? strings.get("ui.reconnecting") : play.connection();
            menus.drawConnecting(layout, connection == null ? "..." : connection);
            return;
        }
        rebindIfRestarted(world);
        aimCamera(world, delta);
        aimAhead(players);

        String name = play.scenarioName();
        if (world != named) {
            name(world);
        }
        readoutWriter.fill(world, missionLine, play.planet(), readout);
        menus.record(readout);
        topBarSeats.follow(readout, menus.menuOpen(), play);
        goalLine.update(readout, delta);
        reporters.report(readout, dispatches);
        ScenarioPlay.Host newHost = play.takeHostChange();
        if (newHost != null) {
            // Every machine says it, on the tick it was sealed on: the commander's colour is the slot's (G0104).
            dispatches.offer(DispatchKind.HOST_CHANGED, newHost.slot(), newHost.peer(), 0);
        }
        if (!topBarSeats.draft().open() && !topBarSeats.crate().open()) {
            // The terminal holds its queue while the draft hides it, so a line said meanwhile types out
            // after it rather than ageing out unseen.
            terminal.advance(delta);
        }
        if (!menus.coversView()) {
            // Held, like the terminal under a draft, so a toast said behind a menu shows when it goes.
            toast.advance(delta);
        }
        alarms.update(readout, delta);
        seatPanels();
        prompts.update(readout, localSlot, delta);
        promptKeys();

        clearLetterbox();
        terrain.render();
        renderer.render(play.renderAlpha(), play.cosmeticLead() ? localAim : null, brackets);
        OverlayMode mode = play.overlayMode();
        List<String> lines = mode == OverlayMode.OFF
                ? List.of()
                : overlay.lines(
                        world,
                        readout,
                        Math.max(0, play.slotOf(PlayerSeats.KEYBOARD_SLOT)),
                        Gdx.graphics.getFramesPerSecond(),
                        name == null ? "" : name,
                        play.paused(),
                        play.lastOrder(),
                        play.connection(),
                        play.invitation());
        if (topBarSeats.draft().open()) {
            draftHints.follow(
                    topBarSeats.draft().seat(),
                    input.playStation(topBarSeats.draft().seat()));
        }
        if (topBarSeats.crate().open()) {
            crateHints.follow(
                    topBarSeats.crate().seat(),
                    input.playStation(topBarSeats.crate().seat()));
        }
        topBar.render(
                layout,
                readout,
                goalLine,
                terminal,
                lines,
                mode,
                topBarSeats.draft(),
                world.upgradeView(),
                draftHints,
                topBarSeats.crate(),
                crateHints,
                world.config());
        if (rollCall) {
            bottomBar.renderRollCall(layout);
        } else {
            bottomBar.render(layout, readout, seating, connected, alarms, draftKeys);
        }
        // PROMPTS off hides the chips and nothing else: brackets and statuses are state, not instructions (G0098).
        if (!menus.coversView() && context.settings().prompts()) {
            chips.draw(layout, viewport, prompts, readout, slotKeys, brackets, play.renderAlpha(), delta);
        }
        menus.draw(layout, play);
        if (play.reconnecting()) {
            menus.drawConnecting(layout, strings.get("ui.reconnecting"));
        }
        if (!menus.coversView()) {
            toastPainter.draw(layout.view(), toast);
        }
        if (menus.showsUpgradesBeside()) {
            heldUpgrades.draw(layout.view(), menus.pagePanel(), readout, menus.pageAlpha());
            heldUpgrades.wheel(wheelNotches, menus.pointerX(), menus.pointerY());
            heldUpgrades.glide(
                    rightStickDown(), context.controlsConfig().ui().nav().scrollLinesPerSecond(), delta);
        } else {
            heldUpgrades.home();
        }
        wheelNotches = 0f;
        drawReticles(play.renderAlpha());
    }

    /**
     * The game's own aim marks (plan 7.5), last, over everything - a bar included, since the pointer
     * still aims from there. Each is optional: the keyboard player's sits on the mouse and hides the
     * system pointer; a pad player's sits a fixed distance along where his commander faces, which is
     * where he aims (G0002). Pad reticles are drawn only for a commander on his feet.
     */
    private void drawReticles(float alpha) {
        GraphicConfig graphic = context.graphicConfig();
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
        if (graphic.hud().reticle().keyboard() && keyboard >= 0) {
            hud.hideSystemCursor();
            hud.reticle(Gdx.input.getX(), Gdx.graphics.getHeight() - 1 - Gdx.input.getY(), colourOf(keyboard));
        } else {
            hud.showSystemCursor();
        }
        if (!graphic.hud().reticle().pads()) {
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
            float x = Drawn.x(position, previous, alpha);
            float y = Drawn.y(position, previous, alpha);
            aimPoint.set(
                    x + facing.x * graphic.hud().reticle().padDistance(),
                    y + facing.y * graphic.hud().reticle().padDistance());
            viewport.project(aimPoint);
            hud.reticle(Math.round(aimPoint.x), Math.round(aimPoint.y), colourOf(slot));
        }
    }

    /**
     * Who has a panel, and whose is grey. A local seat whose pad is unplugged is offline, and so is a slot
     * whose link dropped (G0052, published by the host); either way his commander stays
     * on the field.
     */
    private void seatPanels() {
        for (int slot = 0; slot < Players.MAX; slot++) {
            connected[slot] = !readout.player(slot).away;
        }
        Arrays.fill(draftKeys, "");
        for (int seat = 0; seat < PlayerSeats.SEATS; seat++) {
            int slot = play.slotOf(seat);
            slotOfSeat[seat] = slot;
            if (slot >= 0 && slot < Players.MAX && readout.player(slot).drafts > 0) {
                draftKeys[slot] = input.draftKeyLabel(seat);
            }
            boolean padGone = seat != PlayerSeats.KEYBOARD_SLOT
                    && input.seats().taken(seat)
                    && input.seats().padIn(seat) == null;
            if (slot >= 0 && slot < Players.MAX && padGone) {
                connected[slot] = false;
            }
        }
        seating.update(readout, slotOfSeat);
    }

    /** Names each local seat's keys for its prompt, in its own device's words, and files them by slot. */
    private void promptKeys() {
        Arrays.fill(slotKeys, null);
        for (int seat = 0; seat < PlayerSeats.SEATS; seat++) {
            int slot = play.slotOf(seat);
            if (slot < 0
                    || slot >= Players.MAX
                    || (seat != PlayerSeats.KEYBOARD_SLOT && !input.seats().taken(seat))) {
                continue;
            }
            seatKeys[seat].follow(seat, input.playStation(seat));
            slotKeys[slot] = seatKeys[seat];
        }
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
        return palette.colorFor(Look.COMMANDER, readout.player(slot).variant);
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
        ScreenUtils.clear(context.graphicConfig().hud().color().bar());
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
        Gdx.input.setInputProcessor(null);
        context.hud().showSystemCursor();
        renderer.dispose();
        terrain.dispose();
        if (!handedOver) {
            play.close();
        }
    }

    private void rebindIfRestarted(Simulation current) {
        if (bound != current) {
            renderer.bind(current);
            terrain.bind(current.map());
            brackets.bind(current);
            chips.bind(current);
            focused = current.engine().getEntitiesFor(FOCUSED);
            shaken = current.engine().getEntitiesFor(SHAKEN);
            commanders = current.engine().getEntitiesFor(COMMANDERS);
            camera = new CameraDirector(context.graphicConfig().camera());
            topBarSeats.forget();
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
            camera.watch(Drawn.x(position, previous, alpha), Drawn.y(position, previous, alpha));
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
