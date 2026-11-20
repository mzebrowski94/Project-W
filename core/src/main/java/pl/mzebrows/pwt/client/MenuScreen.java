package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.config.BuildVersion;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.draw.Palette;
import pl.mzebrows.pwt.client.hud.DispatchText;
import pl.mzebrows.pwt.client.hud.Hud;
import pl.mzebrows.pwt.client.hud.MenuPainter;
import pl.mzebrows.pwt.client.hud.PageHints;
import pl.mzebrows.pwt.client.hud.UiStrings;
import pl.mzebrows.pwt.client.input.KeyHolds;
import pl.mzebrows.pwt.client.input.KeyPresses;
import pl.mzebrows.pwt.client.input.NavInputReader;
import pl.mzebrows.pwt.client.input.PageControls;
import pl.mzebrows.pwt.client.play.HostChoices;
import pl.mzebrows.pwt.client.play.MissionChoices;
import pl.mzebrows.pwt.client.play.MissionCode;
import pl.mzebrows.pwt.client.play.MissionRecords;
import pl.mzebrows.pwt.client.play.OnlineChoices;
import pl.mzebrows.pwt.client.play.OnlineSession;
import pl.mzebrows.pwt.client.play.ScenarioPlay;
import pl.mzebrows.pwt.client.play.ScenarioSession;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.net.session.SessionSetup;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Strength;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.scenario.ProvingSetup;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.ui.hud.LevelTime;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;
import pl.mzebrows.pwt.ui.nav.CodePicker;
import pl.mzebrows.pwt.ui.nav.InputModality;
import pl.mzebrows.pwt.ui.nav.NavInput;
import pl.mzebrows.pwt.ui.page.Briefing;
import pl.mzebrows.pwt.ui.page.MenuEntry;
import pl.mzebrows.pwt.ui.page.MenuLayout;
import pl.mzebrows.pwt.ui.page.MenuPage;
import pl.mzebrows.pwt.ui.page.MenuTree;
import pl.mzebrows.pwt.ui.page.PageMotion;
import pl.mzebrows.pwt.ui.page.ProvingRows;
import pl.mzebrows.pwt.ui.page.UiEffect;
import pl.mzebrows.pwt.ui.page.UiFlow;
import pl.mzebrows.pwt.ui.page.UiIntent;
import pl.mzebrows.pwt.ui.page.UiPage;

/**
 * The main menu ({@code UI:main...}): one libGDX {@code Screen} holding every page as a stack, because
 * swapping screens disposes GL resources and a page change must never cost that. The campaign and the
 * hosting list show every scenario in {@link Scenarios}, the whole registry - there is no separate menu
 * definition to keep in sync - or none of them, while {@code graphic.ui.page.scenarios} is off: the campaign
 * then says it is not open yet, and hosting hosts missions only.
 *
 * <p>What a press does is {@link UiFlow}'s and the selection is {@link PageControls}'; this turns the
 * flow's effects into screens and draws. MAIN opens on DAILY MISSIONS, and that page on MEDIUM with nothing
 * chosen: a daily is MEDIUM, then START MISSION (missions ticket 08). Every entry of the missions page writes
 * into its code line and START MISSION starts what it holds - {@link MissionChoices}'.
 */
public final class MenuScreen extends ScreenAdapter {

    private static final int MAX_NUMBER_KEYS = 9;
    private static final int DESCRIPTION_LINES = 2;
    private static final int BODY_LINES = 3;

    /** Spaces between a choice row's label and its value. */
    private static final int CHOICE_GAP = 2;

    /** HOST GAME's rows before the scenarios: WHERE, PASSWORD, OPEN (G0101). */
    private static final int HOST_WHERE = 0;

    private static final int HOST_PASSWORD = 1;
    private static final int HOST_ROWS = 3;

    /** Lines under the missions page's list: why the code line names no mission. */
    private static final int MISSION_FOOT = 1;

    private final GameContext context;
    private final UiStrings strings = UiStrings.load();
    /** The scenarios the lists show: the whole registry, or none while they are not listed. */
    private final List<Scenario> scenarios;

    private final UiFlow flow = new UiFlow(UiPage.MAIN);
    private final NavInput nav = new NavInput();
    private final InputModality modality = new InputModality();
    private final PageControls page = new PageControls();
    private final PageHints hints;
    private final MenuPainter painter;
    private final Palette palette;
    /** One reader for every page: on the missions page W, A, S, D and Backspace are the code line's text. */
    private final NavInputReader reader;

    private final MissionChoices missions;

    /** The main page's corner: which build this is, in words. */
    private final String version = strings.format("ui.main.version", BuildVersion.current());

    /** A pad is picking the missions page's code a letter at a time; null otherwise. */
    private @Nullable CodePicker picker;

    /** A key typed a character since the last frame: the selection goes to the code line. */
    private boolean typed;

    /** HOST GAME's choices, made as the page first opens; null until then. */
    private @Nullable HostChoices hostChoices;

    /** HOST GAME's password, while a pad is picking it a letter at a time. */
    private @Nullable CodePicker passwordPicker;

    /** A key typed a character into HOST GAME's password line since the last frame. */
    private boolean typedPassword;

    /** The online page's lines to type into - nickname, server ip, server port - made as the page first opens. */
    private @Nullable OnlineChoices onlineChoices;

    /** The room being asked of the relay from HOST GAME: null until OPEN asks, and again once it is answered. */
    private @Nullable OnlineSession opening;

    /** What HOST GAME says under its rows: why OPEN did not open, or that it is asking; set at the row it was said on. */
    private String hostStatus = "";

    private int hostStatusAt = -1;

    /**
     * The menu was opened on the missions page on the way back from a mission it started: that first opening
     * keeps what was played in the code line and START selected. Spent by it.
     */
    private boolean backFromMission;

    private final PageMotion motion;
    private final KeyPresses keys = keycode -> Gdx.input.isKeyJustPressed(keycode);
    private final KeyHolds held = keycode -> Gdx.input.isKeyPressed(keycode);
    private final List<String> head = new ArrayList<>(2);
    private final List<String> labels = new ArrayList<>();
    private final List<String> foot = new ArrayList<>(DESCRIPTION_LINES);

    /** The controls page: its switch and, under its entries, its table of bindings. */
    private final ControlsPage controls;

    /** How much of each foot line is bright: on the controls page the table's headings and actions, else none. */
    private final IntUnaryOperator footBright = this::brightInFoot;

    /** Why the player is back here, if the game he was in ended without him asking; empty otherwise. */
    private String notice;

    private final ProvingRows rows;

    /** The proving ground's window, while one of its pages is open; null otherwise. */
    private @Nullable ProvingPreview preview;

    /** How wide a choice row's label is padded, in characters, on the page shown. */
    private int labelWidth;

    /** On the upgrades page: the adaptations' panel, beside the upgrades' in {@link #panel}. */
    private Rect side = Rect.EMPTY;

    /** On the upgrades page: how many of the entries are the players' upgrades - the rest are the swarm's. */
    private int split;

    private final List<String> swarmHead = new ArrayList<>(1);

    /** The session a briefing is of, once one is open - hosted from HOST GAME, or joined - and until it is left or handed to a level. */
    private @Nullable OnlineSession online;

    /** The session has gone to a level: this screen no longer closes it. */
    private boolean handedOver;

    /** What the session is given each frame while it is on its briefing: nobody holds anything there. */
    private final PlayerInputs idle = new PlayerInputs(Players.MAX);

    /** The briefing's foot as last written, and the players among its lines, from which line. */
    private @Nullable String describedBriefing;

    /** HOST GAME's foot as last written - its status and selection - or null once it must be written again. */
    private @Nullable String describedHost;

    private List<Briefing.Row> briefingRows = List.of();
    private int playersFrom = -1;

    /** The code line as the host last said it to the session; null until he has. */
    private @Nullable String sentNote;

    private UiPage shown = UiPage.PLAY;
    private int describedFor = -1;

    /** The missions page's problem as last written under it; null until it is written on this opening. */
    private @Nullable String describedProblem;

    private Rect panel = Rect.EMPTY;

    public MenuScreen(GameContext context) {
        this(context, "");
    }

    /** The menu, saying why the player is back - an online game that ended under him. */
    public MenuScreen(GameContext context, String notice) {
        this(context, notice, UiPage.MAIN);
    }

    /**
     * The menu opened on a page under MAIN, with the pages above it under it, so Back climbs out as it would
     * have - back from joining, the online page; back from a level, the page it was started from (G0066).
     */
    public MenuScreen(GameContext context, String notice, UiPage start) {
        this(context, notice, start, null);
    }

    /**
     * The briefing of a session that is already open (G0101): a joiner's first sight of it, or the host's and every
     * joiner's after a mission. The screen closes the session when it is left.
     */
    public MenuScreen(GameContext context, String notice, OnlineSession session) {
        this(context, notice, UiPage.BRIEFING, session);
    }

    private MenuScreen(GameContext context, String notice, UiPage start, @Nullable OnlineSession session) {
        this.online = session;
        this.context = context;
        this.notice = notice;
        GraphicConfig graphic = context.graphicConfig();
        this.hints = new PageHints(context.controlsConfig(), strings);
        this.painter = new MenuPainter(
                context.hud(), graphic, context.graphicConfig().layout().metrics());
        this.reader = new NavInputReader(context.controlsConfig().ui(), context.gamepads());
        this.missions = context.missions();
        this.motion = new PageMotion(
                graphic.ui().motion().pageSeconds(),
                graphic.ui().motion().pageSlide(),
                graphic.ui().motionScale());
        this.rows = new ProvingRows(context.simConfig());
        this.scenarios = graphic.ui().page().scenarios() ? Scenarios.all() : List.of();
        this.controls = new ControlsPage(context.controlsConfig(), context.settings(), strings);
        this.backFromMission = start == UiPage.BRIEFING || isMissions(start);
        palette = graphic.palette();
        painter.footInk(this::footInk);
        openDown(start);
    }

    private void openDown(UiPage page) {
        UiPage parent = page.parent();
        if (parent == null) {
            return;
        }
        openDown(parent);
        flow.apply(UiIntent.open(page));
    }

    @Override
    public void show() {
        // A keyboard types into the missions page's code line at any time, as into join's (ticket 08).
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char character) {
                if (onlineTyping()) {
                    // The online page's lines take a keyboard whenever one of them is the selected row.
                    onlineChoices().type(OnlineChoices.row(page.index()), character);
                    return true;
                }
                if (flow.top() == UiPage.ONLINE_HOST
                        && opening == null
                        && hostChoices().passwordEnabled()) {
                    // The password line takes a keyboard at any time, as the code line does.
                    String was = hostChoices().typedPassword();
                    hostChoices().type(character);
                    typedPassword |= !was.equals(hostChoices().typedPassword());
                    return true;
                }
                if (!isMissions(flow.top())) {
                    return false;
                }
                // Enter arrives here too: only a key that changed the line takes the selection to it.
                typed |= missions.type(character);
                return true;
            }
        });
    }

    @Override
    public void render(float delta) {
        if (online != null && !runSession(delta)) {
            return;
        }
        if (opening != null) {
            pollOpening(delta);
        }
        ScreenLayout layout = context.hud().layout();
        reader.typing(isMissions(flow.top()) || onlineTyping());
        reader.read(nav, delta, true, false, keys, held, layout);
        modality.update(nav);
        if (nav.any()) {
            motion.finish();
        }
        motion.advance(delta);
        follow();
        if (!handle(layout)) {
            return;
        }
        follow();

        if (modality.showsPointer()) {
            context.hud().showSystemCursor();
        } else {
            context.hud().hideSystemCursor();
        }
        ScreenUtils.clear(context.graphicConfig().hud().color().bar());
        Rect view = layout.canvas();
        describe();
        UiPage top = flow.top();
        if (top == UiPage.PROVING_UPGRADES) {
            drawUpgrades(view);
            return;
        }
        int visible = painter.visibleEntries(view, head, labels.size(), foot.size());
        Rect area = view;
        MenuLayout.Anchor anchor = MenuLayout.Anchor.CENTRED;
        if (top == UiPage.PROVING_GROUND) {
            // The page and its window, side by side, centred together - or the page alone where they do not fit.
            MenuLayout menu = painter.layout();
            int columns = pageColumns();
            int lines = MenuPainter.firstEntryLine(head) + visible + foot.size() + 2;
            area = centred(view, menu.width(columns) + menu.width(columns / 2) + 6 * menu.padding(), lines);
            anchor = MenuLayout.Anchor.DOCKED_LEFT;
        }
        panel = painter.page(
                area,
                anchor,
                head,
                labels,
                page.enabled(),
                page.shown(modality),
                page.scroll(),
                visible,
                foot,
                footBright,
                picker == null && passwordPicker == null
                        ? hints.selectAndBack(modality)
                        : hints.picker(modality, "ui.missions.pickHint"),
                motion.offsetX(),
                motion.alpha());
        if (top == UiPage.PROVING_GROUND) {
            drawPreview(delta, layout, area);
        }
        if (top == UiPage.MAIN) {
            drawVersion(view);
        }
    }

    /** Which build this is, dim in the bottom right corner of the main page. */
    private void drawVersion(Rect view) {
        MenuLayout menu = painter.layout();
        Hud hud = context.hud();
        hud.beginCanvas();
        hud.text(
                version,
                view.right() - 2 * menu.padding() - version.length() * menu.advance(),
                view.y() + 2 * menu.padding() + menu.fontSize(),
                context.graphicConfig().hud().color().textDim());
        hud.endCanvas();
    }

    /**
     * The upgrades page: the players' upgrades in one panel and the swarm's adaptations in another beside
     * it, the pair centred. One list underneath - up and down run from the bottom of one into the other.
     */
    private void drawUpgrades(Rect view) {
        MenuLayout menu = painter.layout();
        int columns = pageColumns() / 2;
        int lines = MenuPainter.firstEntryLine(head) + Math.max(split, labels.size() - split) + 2;
        Rect pair = centred(view, 2 * menu.width(columns) + 6 * menu.padding(), lines);
        int selected = page.index();
        panel = painter.page(
                pair,
                MenuLayout.Anchor.DOCKED_LEFT,
                columns,
                head,
                labels.subList(0, split),
                page.enabled(),
                selected < split ? selected : -1,
                0,
                split,
                List.of(),
                "",
                motion.offsetX(),
                motion.alpha());
        side = painter.page(
                pair,
                MenuLayout.Anchor.DOCKED_RIGHT,
                columns,
                swarmHead,
                labels.subList(split, labels.size()),
                page.enabled(),
                selected >= split ? selected - split : -1,
                0,
                labels.size() - split,
                List.of(),
                hints.selectAndBack(modality),
                motion.offsetX(),
                motion.alpha());
    }

    /**
     * A rect this wide, tall enough for a panel of this many lines with a margin round it, centred in the
     * view - what a page docked left and what sits beside it are laid out in, so the two are centred as one.
     */
    private Rect centred(Rect view, int width, int lines) {
        MenuLayout menu = painter.layout();
        int height = Math.min(view.height(), lines * menu.pitch() + 7 * menu.padding());
        if (width > view.width()) {
            return view;
        }
        return new Rect(view.x() + (view.width() - width) / 2, view.y() + (view.height() - height) / 2, width, height);
    }

    private int pageColumns() {
        return context.graphicConfig().ui().page().columns();
    }

    /**
     * The window beside the page - under it where the canvas is too narrow - onto the chosen strain, with
     * its name on the line under it.
     */
    private void drawPreview(float delta, ScreenLayout layout, Rect view) {
        ProvingPreview window = preview;
        if (window == null || panel.isEmpty()) {
            return;
        }
        MenuLayout menu = painter.layout();
        int columns = pageColumns() / 2;
        // Where the page rests, not where its arrival has it this frame, so the window does not jump under it.
        Rect resting = new Rect(panel.x() - motion.offsetX(), panel.y(), panel.width(), panel.height());
        Rect box = menu.beside(view, resting, menu.linesIn(panel), columns, columns);
        int pitch = menu.pitch();
        if (box.height() <= 2 * pitch) {
            return;
        }
        ProvingSetup setup = context.provingSetup();
        String caption = strings.get("ui.strain." + setup.strain());
        if (setup.nest() && !rows.locked(setup, ProvingRows.Row.COUNT)) {
            caption += "  " + strings.get("ui.provingGround.fromNest");
        }
        Rect inside = new Rect(box.x(), box.y() + pitch, box.width(), box.height() - pitch);
        window.show(setup.strain());
        window.render(delta, layout.toScreen(inside));
        Hud hud = context.hud();
        hud.beginCanvas();
        hud.frame(
                inside.x(),
                inside.y(),
                inside.width(),
                inside.height(),
                context.graphicConfig().hud().color().textDim());
        hud.text(
                caption,
                box.x(),
                box.y() + pitch - menu.padding(),
                context.graphicConfig().hud().color().text());
        hud.endCanvas();
    }

    /** Applies this frame's presses. False when the screen is being left and should draw nothing more. */
    private boolean handle(ScreenLayout layout) {
        UiPage top = flow.top();
        if (isMissions(top)) {
            typeAndPaste();
            if (picker != null) {
                return pick();
            }
        }
        if (top == UiPage.ONLINE_HOST && !hostInput()) {
            return true;
        }
        if (top == UiPage.ONLINE) {
            // The lines show what was typed and where the caret is; a few short strings, only while this page is up.
            relabelOnline();
        }
        if (isScenarioList(top)) {
            int first = listOffset(top);
            for (int i = 0; i < scenarios.size() && i < MAX_NUMBER_KEYS; i++) {
                if (keys.justPressed(Input.Keys.NUM_1 + i) && page.jump(first + i)) {
                    return carryOut(flow.apply(intentFor(top, first + i)));
                }
            }
        }
        if (isProving(top) && (nav.left || nav.right)) {
            step(top, page.index(), nav.right ? 1 : -1);
        }
        if (top == UiPage.CONTROLS && (nav.left || nav.right) && page.index() == MenuTree.CONTROLS_PROMPTS) {
            flipPrompts();
        }
        if (isMissions(top) && (nav.left || nav.right) && page.index() == MenuTree.MISSION_RANDOM) {
            missions.stepRandomTier(nav.right ? 1 : -1);
            relabelMissions();
        }
        if (isMissions(top) && (nav.left || nav.right) && page.index() == MenuTree.MISSION_LENGTH) {
            missions.stepLength(nav.right ? 1 : -1);
            relabelMissions();
        }
        if (isMissions(top) && (nav.left || nav.right) && page.index() == MenuTree.MISSION_HEALTH) {
            missions.stepHealth(nav.right ? 1 : -1);
            relabelMissions();
        }
        if (isMissions(top) && (nav.left || nav.right) && page.index() == MenuTree.MISSION_COUNT) {
            missions.stepCount(nav.right ? 1 : -1);
            relabelMissions();
        }
        int chosen;
        if (top == UiPage.PROVING_UPGRADES) {
            // Two panels, one list: the pointer is found here, and the controls see no panel of their own.
            int under = upgradeUnder(nav.pointerX, nav.pointerY);
            if (nav.pointerMoved && under >= 0) {
                page.jump(under);
            }
            chosen = page.handle(nav, painter.layout(), Rect.EMPTY, 0, labels.size());
            if (nav.pointerPressed && under >= 0) {
                page.jump(under);
                chosen = under;
            }
        } else {
            int visible = painter.visibleEntries(layout.canvas(), head, labels.size(), foot.size());
            chosen = page.handle(nav, painter.layout(), panel, MenuPainter.firstEntryLine(head), visible);
        }
        if (chosen >= 0
                && nav.pointerPressed
                && isChoice(top, chosen)
                && onBackArrow(nav.pointerX, top == UiPage.PROVING_UPGRADES && chosen >= split ? side : panel)) {
            step(top, chosen, -1);
            return true;
        }
        if (chosen == MenuTree.MISSION_RANDOM && nav.pointerPressed && isMissions(top)) {
            // RANDOM's confirm starts; a click on either of its arrows steps its tier instead.
            int arrow = arrowUnder(nav.pointerX, tierName(missions.randomTier()).length());
            if (arrow != 0) {
                missions.stepRandomTier(arrow);
                relabelMissions();
                return true;
            }
        }
        if (chosen == MenuTree.MISSION_LENGTH && nav.pointerPressed && isMissions(top)) {
            // The length row's confirm steps it up; a click on its left arrow steps it down.
            int arrow = arrowUnder(nav.pointerX, biomesText().length());
            missions.stepLength(arrow == 0 ? 1 : arrow);
            relabelMissions();
            enableStart();
            return true;
        }
        if ((chosen == MenuTree.MISSION_HEALTH || chosen == MenuTree.MISSION_COUNT)
                && nav.pointerPressed
                && isMissions(top)) {
            // The strength rows' confirm steps them up; a click on a left arrow steps them down.
            boolean health = chosen == MenuTree.MISSION_HEALTH;
            int arrow = arrowUnder(
                    nav.pointerX,
                    percentText(
                                    health
                                            ? missions.strength().healthPercent()
                                            : missions.strength().countPercent())
                            .length());
            if (health) {
                missions.stepHealth(arrow == 0 ? 1 : arrow);
            } else {
                missions.stepCount(arrow == 0 ? 1 : arrow);
            }
            relabelMissions();
            return true;
        }
        if (chosen >= 0) {
            return carryOut(flow.apply(intentFor(top, chosen)));
        }
        if (chosen == PageControls.BACK) {
            return carryOut(flow.apply(UiIntent.BACK));
        }
        return true;
    }

    private UiIntent intentFor(UiPage top, int entry) {
        if (top == UiPage.CAMPAIGN && isScenarioList(top)) {
            return UiIntent.start(entry);
        }
        return switch (top) {
            case ONLINE_HOST ->
                entry <= HOST_PASSWORD
                        ? UiIntent.choose(entry)
                        : entry < listOffset(top) ? UiIntent.OPEN_SESSION : UiIntent.host(entry - listOffset(top));
            case BRIEFING ->
                MenuTree.briefing(hostsBriefing()).entries().get(entry).intent();
            case PROVING_UPGRADES -> entry < rows.upgrades().size() ? UiIntent.choose(entry) : UiIntent.BACK;
            default -> MenuTree.page(top).entries().get(entry).intent();
        };
    }

    private boolean carryOut(UiEffect effect) {
        switch (effect.kind()) {
            case SELECT_EXIT -> page.jump(MenuTree.page(UiPage.MAIN).entries().size() - 1);
            case QUIT_APP -> {
                Gdx.app.exit();
                return false;
            }
            case START -> {
                start(effect.index());
                return false;
            }
            case HOST -> {
                return effect.index() < 0 ? openSession() : !host(effect.index());
            }
            case LEAVE -> {
                return !leaveBriefing();
            }
            case CHOOSE -> {
                if (flow.top() == UiPage.CONTROLS) {
                    flipPrompts();
                } else if (flow.top() == UiPage.ONLINE_HOST) {
                    chooseHost(effect.index());
                } else if (flow.top() == UiPage.ONLINE) {
                    // Confirm on a line is done with it: on to the next row.
                    page.jump(Math.min(effect.index() + 1, MenuTree.ONLINE_ROWS));
                } else {
                    step(flow.top(), effect.index(), 1);
                }
            }
            case PROVE -> {
                prove();
                return false;
            }
            case MISSION, BRIEFED_MISSION -> {
                return mission(effect.index(), effect.kind() == UiEffect.Kind.BRIEFED_MISSION);
            }
            default -> {
                // Page changes are the flow's own; nothing else arises on the main menu.
            }
        }
        return true;
    }

    /** Keeps the drawn page in step with the flow's: its words and where its selection starts. */
    private void follow() {
        UiPage top = flow.top();
        if (top == shown) {
            return;
        }
        if (shown == UiPage.ONLINE) {
            commitOnline();
        }
        if (shown != UiPage.PLAY && shown != UiPage.EXIT_CONFIRM) {
            context.selections().set(shown, page.index());
        }
        if (top == UiPage.ONLINE_JOIN) {
            // Joining is its own screen: it types, it listens on the network.
            flow.apply(UiIntent.BACK);
            context.queueScreen(new JoinScreen(context));
            return;
        }
        if (isProving(top) && preview == null) {
            preview = new ProvingPreview(context.graphicConfig(), context.simConfig());
        } else if (!isProving(top)) {
            closePreview();
        }
        motion.arrive(shown == UiPage.PLAY || top.parent() == shown ? 1 : -1);
        shown = top;
        panel = Rect.EMPTY;
        side = Rect.EMPTY;
        describedFor = -1;
        describedHost = null;
        hostStatus = "";
        describedProblem = null;
        describedBriefing = null;
        head.clear();
        labels.clear();
        int defaultSelection = 0;
        if (isScenarioList(top)) {
            head.add(strings.get(top == UiPage.CAMPAIGN ? "ui.campaign.title" : "ui.host.title"));
            if (top == UiPage.ONLINE_HOST) {
                passwordPicker = null;
                relabelHost();
            } else {
                for (Scenario scenario : scenarios) {
                    labels.add(scenario.name());
                }
            }
            // Hosting opens where the campaign was: the scenario played last is the likeliest to host - or on OPEN.
            defaultSelection = scenarios.isEmpty()
                    ? Math.max(0, listOffset(top) - 1)
                    : listOffset(top) + context.selections().get(UiPage.CAMPAIGN, 0, scenarios.size());
        } else if (top == UiPage.PROVING_UPGRADES) {
            head.add(strings.get("ui.provingUpgrades.title"));
            swarmHead.clear();
            swarmHead.add(strings.get("ui.provingUpgrades.swarm"));
            relabel(top);
        } else {
            MenuPage content = top == UiPage.BRIEFING ? MenuTree.briefing(hostsBriefing()) : MenuTree.page(top);
            if (isMissions(top)) {
                // The date is read as the page opens, and a daily started from it keeps that day (G0065).
                if (backFromMission) {
                    missions.reopen();
                } else {
                    missions.open();
                }
                picker = null;
                head.add(strings.format(content.titleKey(), missions.day()));
            } else if (!content.titleKey().isEmpty()) {
                head.add(strings.get(content.titleKey()));
            }
            if (top == UiPage.BRIEFING && online != null && !online.roomCode().isEmpty()) {
                head.add(
                        online.roomPassword().isEmpty()
                                ? strings.format("ui.briefing.room", online.roomCode())
                                : strings.format("ui.briefing.roomPassword", online.roomCode(), online.roomPassword()));
            }
            if (!content.bodyKey().isEmpty()) {
                painter.addWrapped(head, strings.get(content.bodyKey()), BODY_LINES);
            }
            if (top == UiPage.MAIN && !notice.isEmpty()) {
                painter.addWrapped(head, notice, BODY_LINES);
            }
            if (top == UiPage.PROVING_GROUND) {
                relabel(top);
            } else if (top == UiPage.CONTROLS) {
                controls.open(modality.playStation(), labels);
                foot.clear();
                foot.addAll(controls.table());
            } else if (isMissions(top)) {
                relabelMissions();
            } else if (top == UiPage.ONLINE) {
                relabelOnline();
            } else {
                for (MenuEntry entry : content.entries()) {
                    labels.add(strings.get(entry.labelKey()));
                }
            }
            defaultSelection = content.defaultSelection();
        }
        // The missions page opened from the menu has nothing chosen, so it opens on MEDIUM; back from a
        // mission, on where it was started from.
        boolean fresh = top == UiPage.EXIT_CONFIRM || (isMissions(top) && !backFromMission);
        int remembered = fresh ? defaultSelection : context.selections().get(top, defaultSelection, labels.size());
        backFromMission = false;
        page.reset(labels.size(), remembered);
        if (top == UiPage.PROVING_GROUND) {
            lockRows();
        } else if (isMissions(top)) {
            enableStart();
        } else if (top == UiPage.ONLINE_HOST) {
            enableHost();
        }
    }

    /**
     * Writes a proving ground page's entries from the setup: a choice row as its label, padded, and its
     * value between arrows; the rest as their words. Keeps the selection - only the words change.
     */
    private void relabel(UiPage top) {
        ProvingSetup setup = context.provingSetup();
        labels.clear();
        labelWidth = 0;
        if (top == UiPage.PROVING_UPGRADES) {
            split = 0;
            for (UpgradeKind kind : rows.upgrades()) {
                labelWidth = Math.max(labelWidth, upgradeName(kind).length());
                split += rows.swarm(kind) ? 0 : 1;
            }
            for (UpgradeKind kind : rows.upgrades()) {
                labels.add(choice(upgradeName(kind), words(rows.rank(setup, kind))));
            }
            labels.add(strings.get("ui.common.back"));
            return;
        }
        List<MenuEntry> entries = MenuTree.page(top).entries();
        for (MenuEntry entry : entries) {
            labelWidth = Math.max(labelWidth, strings.get(entry.labelKey()).length());
        }
        for (int i = 0; i < entries.size(); i++) {
            String label = strings.get(entries.get(i).labelKey());
            if (i < ProvingRows.ROWS.size()) {
                labels.add(choice(label, words(rows.value(setup, ProvingRows.ROWS.get(i)))));
            } else if (entries.get(i).intent().page() == UiPage.PROVING_UPGRADES) {
                labels.add(pad(label) + strings.format("ui.provingGround.held", rows.held(setup)));
            } else {
                labels.add(label);
            }
        }
    }

    private int brightInFoot(int line) {
        return flow.top() == UiPage.CONTROLS ? controls.bright(line) : 0;
    }

    private void flipPrompts() {
        controls.flip(labels);
    }

    /** A count that cannot be chosen - a rooted strain's - is dimmed and passed over. */
    private void lockRows() {
        int count = ProvingRows.ROWS.indexOf(ProvingRows.Row.COUNT);
        page.enabled()[count] = !rows.locked(context.provingSetup(), ProvingRows.Row.COUNT);
        page.settle();
    }

    /** Steps one choice row of the page open, if that entry is one, and writes the page again. */
    private void step(UiPage top, int entry, int direction) {
        if (!isChoice(top, entry)) {
            return;
        }
        if (top == UiPage.ONLINE_HOST) {
            hostChoices().step();
            hostStatus = "";
            describedHost = null;
            relabelHost();
            enableHost();
            return;
        }
        ProvingSetup setup = context.provingSetup();
        context.provingSetup(
                top == UiPage.PROVING_UPGRADES
                        ? rows.stepRank(setup, rows.upgrades().get(entry), direction)
                        : rows.step(setup, ProvingRows.ROWS.get(entry), direction));
        relabel(top);
        if (top == UiPage.PROVING_GROUND) {
            lockRows();
        }
    }

    private boolean isChoice(UiPage top, int entry) {
        if (top == UiPage.ONLINE_HOST) {
            return entry == HOST_WHERE;
        }
        if (top == UiPage.PROVING_GROUND) {
            return entry >= 0 && entry < ProvingRows.ROWS.size();
        }
        return top == UiPage.PROVING_UPGRADES
                && entry >= 0
                && entry < rows.upgrades().size();
    }

    /** The upgrades page's entry under the pointer, in either panel, or -1. */
    private int upgradeUnder(int x, int y) {
        MenuLayout menu = painter.layout();
        int first = MenuPainter.firstEntryLine(head);
        int left = panel.isEmpty() ? -1 : menu.entryAt(panel, first, split, x, y);
        if (left >= 0) {
            return left;
        }
        int right = side.isEmpty() ? -1 : menu.entryAt(side, first, labels.size() - split, x, y);
        return right >= 0 ? split + right : -1;
    }

    /** Whether the pointer is over a choice row's {@code <}, in the panel it is in: what steps it back. */
    private boolean onBackArrow(int pointerX, Rect in) {
        MenuLayout menu = painter.layout();
        int arrow = menu.lineX(in) + (labelWidth + CHOICE_GAP) * menu.advance();
        return pointerX >= arrow && pointerX < arrow + 2 * menu.advance();
    }

    private String choice(String label, String value) {
        return pad(label) + "< " + value + " >";
    }

    private String pad(String label) {
        return label + " ".repeat(Math.max(0, labelWidth + CHOICE_GAP - label.length()));
    }

    private String words(ProvingRows.Choice choice) {
        return choice.key().isEmpty() ? choice.text() : strings.get(choice.key());
    }

    /** An upgrade's name; which side's it is, the panel it is in says. */
    private String upgradeName(UpgradeKind kind) {
        return strings.get("ui.upgrade." + kind);
    }

    private static boolean isProving(UiPage page) {
        return page == UiPage.PROVING_GROUND || page == UiPage.PROVING_UPGRADES;
    }

    /** Fights the setup offline; leaving it comes back here (G0060). */
    private void prove() {
        ProvingSetup setup = context.provingSetup();
        // Coming back lands on START, so trying again is one confirm.
        context.selections().set(UiPage.PROVING_GROUND, page.index());
        Log.info("Proving " + setup);
        context.queueScreen(new ScenarioScreen(
                context,
                new ScenarioSession(Scenarios.provingGround(setup), context.simConfig()),
                UiPage.PROVING_GROUND));
    }

    private void closePreview() {
        ProvingPreview open = preview;
        if (open != null) {
            open.dispose();
            preview = null;
        }
    }

    /**
     * Under a scenario list: what the selected one is for, on at most two lines - always two, the second
     * blank if it needs one, so the panel keeps its height as the selection moves.
     */
    private void describe() {
        UiPage top = flow.top();
        if (top == UiPage.BRIEFING) {
            describeBriefing();
            return;
        }
        if (isMissions(top)) {
            describeMission();
            return;
        }
        if (top == UiPage.CONTROLS) {
            // Its foot is its table, written as the page opened.
            return;
        }
        if (top == UiPage.ONLINE_HOST) {
            describeHost();
            return;
        }
        if (!isScenarioList(top)) {
            foot.clear();
            return;
        }
        int selected = page.index();
        if (selected == describedFor) {
            return;
        }
        describedFor = selected;
        foot.clear();
        if (selected >= listOffset(top)) {
            painter.addWrapped(foot, scenarios.get(selected - listOffset(top)).description(), DESCRIPTION_LINES);
        }
        while (foot.size() < DESCRIPTION_LINES) {
            foot.add("");
        }
    }

    /** A page that lists the scenarios: hosting, and the campaign while its scenarios are listed. */
    private boolean isScenarioList(UiPage page) {
        return page == UiPage.ONLINE_HOST || (page == UiPage.CAMPAIGN && !scenarios.isEmpty());
    }

    /** Entries before a scenario list's scenarios: hosting's DAILY MISSIONS. */
    private static int listOffset(UiPage page) {
        return page == UiPage.ONLINE_HOST ? HOST_ROWS : 0;
    }

    private boolean isMissions(UiPage page) {
        return page == UiPage.MISSIONS || (page == UiPage.BRIEFING && hostsBriefing());
    }

    /**
     * Under the missions page's list, always a line so the panel keeps its height: why the code line names no
     * mission - refused the moment its eighth character lands - or nothing.
     */
    private void describeMission() {
        String problem = missions.problem();
        if (problem.equals(describedProblem)) {
            return;
        }
        describedProblem = problem;
        foot.clear();
        foot.add(problem.isEmpty() ? "" : strings.format(problem, MissionChoices.tierLetters()));
        while (foot.size() < MISSION_FOOT) {
            foot.add("");
        }
    }

    /**
     * The missions page's entries: RANDOM as a choice row of tiers, the code line as what is typed - or, while
     * a pad picks it, the picker with a dash between the halves and the tier its first slot names.
     */
    private void relabelMissions() {
        List<MenuEntry> entries = MenuTree.page(flow.top()).entries();
        labels.clear();
        labelWidth = 0;
        for (MenuEntry entry : entries) {
            labelWidth = Math.max(labelWidth, strings.get(entry.labelKey()).length());
        }
        TierKind[] tiers = TierKind.values();
        for (int i = 0; i < entries.size(); i++) {
            String label = strings.get(entries.get(i).labelKey());
            long best =
                    i < tiers.length ? context.missionRecords().best(missions.day(), tiers[i]) : MissionRecords.NONE;
            if (best != MissionRecords.NONE) {
                // Won on this machine: the ✓ and the best time (G0067).
                labels.add(pad(label)
                        + strings.format(
                                "ui.missions.won",
                                LevelTime.text(best, context.simConfig().tickSeconds())));
            } else if (i == MenuTree.MISSION_RANDOM) {
                labels.add(choice(label, tierName(missions.randomTier())));
            } else if (i == MenuTree.MISSION_LENGTH) {
                labels.add(choice(label, biomesText()));
            } else if (i == MenuTree.MISSION_HEALTH) {
                labels.add(choice(label, percentText(missions.strength().healthPercent())));
            } else if (i == MenuTree.MISSION_COUNT) {
                labels.add(choice(label, percentText(missions.strength().countPercent())));
            } else if (i == MenuTree.MISSION_CODE) {
                labels.add(pad(label) + codeLine());
            } else {
                labels.add(label);
            }
        }
    }

    /** A strength row's value: {@code 150%}. */
    private String percentText(int percent) {
        return strings.format("ui.missions.percent", percent);
    }

    /** The length row's value: {@code 6 BIOMES}. */
    private String biomesText() {
        return biomes(missions.length());
    }

    private String biomes(int length) {
        return strings.format("ui.missions.biomes", length);
    }

    /**
     * The code line: what it holds and, once that names a mission, its tier, its length and where it came from -
     * {@code MEDIUM  6 BIOMES  DAILY}, {@code HARD  4 BIOMES  RANDOM}, {@code EASY  9 BIOMES  CUSTOM}.
     */
    private String codeLine() {
        CodePicker picking = picker;
        if (picking == null) {
            MissionCode code = missions.code();
            MissionChoices.Origin origin = missions.origin();
            return code == null || origin == null
                    ? missions.line()
                    : missions.line()
                            + "  "
                            + strings.format(
                                    "ui.missions.origin",
                                    tierName(code.tier()),
                                    strings.get("ui.missions." + origin),
                                    biomes(code.length()));
        }
        TierKind tier = MissionCode.tierOf(picking.code().charAt(0));
        int length = picking.code().length() > 1
                ? MissionCode.lengthOf(picking.code().charAt(1))
                : -1;
        return picking.shown(MissionCode.LENGTH / 2)
                + (tier == null ? "" : "  " + tierName(tier))
                + (tier == null || length < 1 ? "" : "  " + biomes(length));
    }

    private String tierName(TierKind tier) {
        return strings.get("ui.tier." + tier);
    }

    /** What a key typed or {@code Ctrl+V} pasted since the last frame: the selection goes to the code line. */
    private void typeAndPaste() {
        boolean control = held.held(Input.Keys.CONTROL_LEFT) || held.held(Input.Keys.CONTROL_RIGHT);
        if (control && keys.justPressed(Input.Keys.V)) {
            String clipboard = Gdx.app.getClipboard().getContents();
            if (clipboard != null) {
                missions.paste(clipboard);
                typed = true;
            }
        }
        if (typed) {
            typed = false;
            picker = null;
            page.jump(MenuTree.MISSION_CODE);
        }
        relabelMissions();
        enableStart();
    }

    /** START MISSION can be confirmed only while the code line names a mission; dimmed and passed over else. */
    private void enableStart() {
        page.enabled()[MenuTree.MISSION_START] = flow.top() == UiPage.BRIEFING
                ? Briefing.startEnabled(hostsBriefing(), missions.code() != null)
                : missions.code() != null;
    }

    /**
     * The code picker: left and right choose the slot, up and down the letter, confirm puts what it shows in
     * the code line - refused there at once if it is none - and back closes it, the line as it was.
     */
    private boolean pick() {
        CodePicker picking = picker;
        if (picking == null) {
            return true;
        }
        if (nav.left) {
            picking.left();
        }
        if (nav.right) {
            picking.right();
        }
        if (nav.up) {
            picking.up();
        }
        if (nav.down) {
            picking.down();
        }
        if (nav.back || nav.pointerSecondary) {
            picker = null;
        } else if (nav.confirm) {
            missions.replaceTyped(picking.code());
            picker = null;
        }
        relabelMissions();
        enableStart();
        return true;
    }

    /** Which arrow of a choice row's {@code < VALUE >} the pointer is on: -1, +1, or 0 for neither. */
    private int arrowUnder(int pointerX, int value) {
        if (onBackArrow(pointerX, panel)) {
            return -1;
        }
        MenuLayout menu = painter.layout();
        int arrow = menu.lineX(panel) + (labelWidth + CHOICE_GAP + 2 + value) * menu.advance();
        return pointerX >= arrow && pointerX < arrow + 2 * menu.advance() ? 1 : 0;
    }

    /**
     * What an entry of the missions page does: a daily writes its code into the code line, RANDOM a fresh
     * draw, and START MISSION plays what the line names. Confirm on the code line, from any device, always
     * opens the picker on it - editing; the picker's own confirm then takes the code. False when the screen is
     * being left.
     */
    private boolean mission(int entry, boolean hosted) {
        TierKind[] tiers = TierKind.values();
        if (entry < tiers.length) {
            missions.chooseDaily(tiers[entry]);
        } else if (entry == MenuTree.MISSION_RANDOM) {
            missions.drawRandom();
        } else if (entry == MenuTree.MISSION_LENGTH) {
            missions.stepLength(1);
        } else if (entry == MenuTree.MISSION_HEALTH) {
            missions.stepHealth(1);
        } else if (entry == MenuTree.MISSION_COUNT) {
            missions.stepCount(1);
        } else if (entry == MenuTree.MISSION_CODE) {
            picker = missions.openPicker();
        } else {
            MissionCode code = missions.code();
            return code == null || !play(code, hosted);
        }
        relabelMissions();
        enableStart();
        return true;
    }

    /**
     * Starts a mission - offline, or hosted for friends to drop into as the scenario {@code mission <tier>}
     * on the mission seed (G0065) - coming back to this page with it selected. False if it could not host.
     */
    private boolean play(MissionCode code, boolean hosted) {
        UiPage from = flow.top();
        missions.played(code);
        context.selections().set(from, page.index());
        SimConfig config = context.simConfig().withSeed(code.seed());
        Strength strength = missions.strength();
        OnlineSession briefing = online;
        if (hosted && briefing != null) {
            // The session is open already: every machine on its briefing builds this and plays from the first tick.
            Log.info("Starting mission " + code + " for the session");
            briefing.startMission(
                    new SessionSetup(
                            context.worldConfigText(), code.scenario(strength).name(), code.seed()),
                    config.tickSeconds());
            return true;
        }
        Log.info("Starting mission " + code);
        context.queueScreen(new ScenarioScreen(context, new ScenarioSession(code.scenario(strength), config), from));
        return true;
    }

    private void start(int index) {
        Scenario scenario = scenarios.get(index);
        context.selections().set(UiPage.CAMPAIGN, index);
        Log.info("Starting scenario " + scenario.name());
        context.queueScreen(
                new ScenarioScreen(context, new ScenarioSession(scenario, context.simConfig()), UiPage.CAMPAIGN));
    }

    /**
     * Hosts the chosen scenario online and goes straight into it: the host plays from the first frame and
     * friends drop in (G0038). If it cannot, the player stays here and is told why.
     */
    private boolean host(int index) {
        context.selections().set(UiPage.CAMPAIGN, index);
        context.selections().set(UiPage.ONLINE_HOST, listOffset(UiPage.ONLINE_HOST) + index);
        return host(scenarios.get(index), context.simConfig(), UiPage.ONLINE_HOST);
    }

    /** Hosts {@code scenario} on {@code config}, coming back to {@code back}; if it cannot, MAIN says why. */
    private boolean host(Scenario scenario, SimConfig config, UiPage back) {
        try {
            context.queueScreen(new ScenarioScreen(
                    context,
                    OnlineSession.host(
                            scenario,
                            config,
                            context.worldConfigText(),
                            context.netConfig(),
                            context.launchOptions(),
                            LocalMachineId.load()),
                    back));
            return true;
        } catch (RuntimeException e) {
            Log.error("Could not host " + scenario.name(), e);
            notice = "Could not host " + scenario.name() + ": " + e.getMessage();
            while (flow.top() != UiPage.MAIN) {
                flow.apply(UiIntent.BACK);
            }
            return false;
        }
    }

    /** Whether this machine hosts the session the briefing is of: its entries choose, and its Back ends the session. */
    private boolean hostsBriefing() {
        OnlineSession open = online;
        return open != null && open.hosts();
    }

    /**
     * One frame of the session a briefing is of: heard, and the host's code line said. False when the screen is being
     * left - the session ended under it, or the mission began and a level has it now.
     */
    private boolean runSession(float delta) {
        OnlineSession open = online;
        if (open == null) {
            return true;
        }
        open.advance(delta, idle);
        String ended = open.ended();
        if (ended != null) {
            Log.info(ended);
            online = null;
            open.close();
            context.queueScreen(new MenuScreen(context, ended));
            return false;
        }
        if (open.simulation() != null) {
            handedOver = true;
            context.queueScreen(new ScenarioScreen(context, open, open.hosts() ? UiPage.ONLINE_HOST : UiPage.MAIN));
            return false;
        }
        flow.observeBriefing(open.hosts(), Briefing.anybodyJoined(open.players()));
        if (open.hosts() && open.welcomed() && !missions.note().equals(sentNote)) {
            sentNote = missions.note();
            open.note(sentNote);
        }
        return true;
    }

    /** HOST GAME's OPEN: the session opens now, before a mission is chosen, and the briefing shows (G0101). */
    private boolean openSession() {
        if (hostChoices().internet()) {
            openRoom();
            return true;
        }
        try {
            online = OnlineSession.open(context.netConfig(), context.launchOptions(), LocalMachineId.load());
        } catch (RuntimeException e) {
            Log.error("Could not open a session", e);
            notice = "Could not open a session: " + e.getMessage();
            while (flow.top() != UiPage.MAIN) {
                flow.apply(UiIntent.BACK);
            }
            return true;
        }
        flow.apply(UiIntent.open(UiPage.BRIEFING));
        return true;
    }

    /** The online page's lines, from what this machine has kept and the relay as the config stands. */
    private OnlineChoices onlineChoices() {
        OnlineChoices choices = onlineChoices;
        if (choices == null) {
            NetConfig.Relay relay = context.netConfig().relay();
            choices = new OnlineChoices(LocalMachineId.load(), relay.host(), Integer.toString(relay.port()));
            onlineChoices = choices;
        }
        return choices;
    }

    /** Whether the selected row of the online page is a line a keyboard types into. */
    private boolean onlineTyping() {
        return flow.top() == UiPage.ONLINE && page.index() < MenuTree.ONLINE_ROWS;
    }

    /**
     * The online page's rows: the three lines as label and what is typed - the selected one with a caret - then JOIN
     * GAME, HOST GAME and BACK as their words. The selection stays; only the words change.
     */
    private void relabelOnline() {
        OnlineChoices choices = onlineChoices();
        List<MenuEntry> entries = MenuTree.page(UiPage.ONLINE).entries();
        labelWidth = 0;
        for (int i = 0; i < MenuTree.ONLINE_ROWS; i++) {
            labelWidth =
                    Math.max(labelWidth, strings.get(entries.get(i).labelKey()).length());
        }
        labels.clear();
        for (int i = 0; i < entries.size(); i++) {
            String label = strings.get(entries.get(i).labelKey());
            if (i >= MenuTree.ONLINE_ROWS) {
                labels.add(label);
                continue;
            }
            String typed = choices.text(OnlineChoices.row(i));
            boolean selected = page.index() == i;
            String shown = selected ? typed + "_" : typed.isEmpty() ? strings.get("ui.online.notSet") : typed;
            labels.add(pad(label) + shown);
        }
    }

    /**
     * Leaving the online page keeps what was typed on it: the nickname as this machine's id, and a relay address that
     * differs from the config's as the player's own. A relay that changed may change what HOST GAME can offer.
     */
    private void commitOnline() {
        OnlineChoices choices = onlineChoices;
        if (choices == null) {
            return;
        }
        onlineChoices = null;
        LocalMachineId.save(choices.nickname());
        NetConfig.Relay relay = context.netConfig().relay();
        String port = choices.port();
        if (!choices.host().equals(relay.host()) || (port != null && !port.equals(Integer.toString(relay.port())))) {
            context.settings().relay(choices.host(), port == null ? "" : port);
        }
        HostChoices hosting = hostChoices;
        if (hosting != null && hosting.relaySet() != context.netConfig().relay().set()) {
            hostChoices = null;
        }
    }

    private HostChoices hostChoices() {
        HostChoices choices = hostChoices;
        if (choices == null) {
            choices = new HostChoices(
                    context.netConfig().relay().set(), context.settings().hostPassword());
            hostChoices = choices;
        }
        return choices;
    }

    /**
     * HOST GAME's rows: WHERE as a choice row - the internet is not offered while no relay is set, and says so -, the
     * PASSWORD line (dim and passed over on this network), OPEN, then the developer's scenarios.
     */
    private void relabelHost() {
        HostChoices choices = hostChoices();
        labels.clear();
        String where = strings.get("ui.host.where");
        String password = strings.get("ui.host.password");
        labelWidth = Math.max(where.length(), password.length());
        labels.add(choice(where, strings.get(choices.internet() ? "ui.host.internet" : "ui.host.thisNetwork"))
                + (choices.relaySet() ? "" : "   " + strings.get("ui.host.noRelay")));
        CodePicker picking = passwordPicker;
        String typedPassword = choices.typedPassword();
        labels.add(pad(password)
                + (!choices.passwordEnabled()
                        ? strings.get("ui.host.passwordOff")
                        : picking != null
                                ? picking.shown(0)
                                : typedPassword.isEmpty() ? strings.get("ui.host.passwordNone") : typedPassword));
        labels.add(strings.get("ui.host.open"));
        for (Scenario scenario : scenarios) {
            labels.add(scenario.name());
        }
    }

    /** The password line is live on the internet only; dimmed and passed over otherwise. */
    private void enableHost() {
        page.enabled()[HOST_PASSWORD] = hostChoices().passwordEnabled() && opening == null;
        page.settle();
    }

    /**
     * HOST GAME's own input: a key typed into the password line takes the selection to it; a pad's picker has the
     * frame while it is open; left and right flip WHERE, or clear the password. While the relay is asked for a room
     * nothing is chosen - Back gives it up. False when the frame is spent.
     */
    private boolean hostInput() {
        if (opening != null) {
            if (nav.back || nav.pointerSecondary) {
                giveUpOpening();
            }
            return false;
        }
        if (typedPassword) {
            typedPassword = false;
            passwordPicker = null;
            hostStatus = "";
            describedHost = null;
            page.jump(HOST_PASSWORD);
            relabelHost();
        }
        CodePicker picking = passwordPicker;
        if (picking != null) {
            if (nav.left) {
                picking.left();
            }
            if (nav.right) {
                picking.right();
            }
            if (nav.up) {
                picking.up();
            }
            if (nav.down) {
                picking.down();
            }
            if (nav.back || nav.pointerSecondary) {
                passwordPicker = null;
            } else if (nav.confirm) {
                hostChoices().replacePassword(picking.code());
                passwordPicker = null;
            }
            relabelHost();
            return false;
        }
        if ((nav.left || nav.right) && page.index() == HOST_WHERE) {
            step(UiPage.ONLINE_HOST, HOST_WHERE, 1);
        } else if ((nav.left || nav.right)
                && page.index() == HOST_PASSWORD
                && hostChoices().passwordEnabled()) {
            hostChoices().replacePassword("");
            relabelHost();
        }
        return true;
    }

    /** A confirm on a HOST GAME choice row: WHERE flips, PASSWORD opens the picker - on the line as it stands. */
    private void chooseHost(int row) {
        if (row == HOST_WHERE) {
            step(UiPage.ONLINE_HOST, HOST_WHERE, 1);
        } else if (row == HOST_PASSWORD && hostChoices().passwordEnabled()) {
            String typedPassword = hostChoices().typedPassword();
            CodePicker picking = new CodePicker(RoomCode.ALPHABET, RoomCode.LENGTH);
            picking.show(typedPassword.length() == RoomCode.LENGTH ? typedPassword : RoomCode.ALPHABET.substring(0, 4));
            passwordPicker = picking;
            relabelHost();
        }
    }

    /** Under HOST GAME's rows: what OPEN has to say, else what the selected scenario is for - two lines always. */
    private void describeHost() {
        int selected = page.index();
        if (!hostStatus.isEmpty() && selected != hostStatusAt) {
            hostStatus = "";
        }
        String wanted = hostStatus + "|" + selected;
        if (wanted.equals(describedHost)) {
            return;
        }
        describedHost = wanted;
        foot.clear();
        if (!hostStatus.isEmpty()) {
            painter.addWrapped(foot, hostStatus, DESCRIPTION_LINES);
        } else if (selected >= listOffset(UiPage.ONLINE_HOST)) {
            painter.addWrapped(
                    foot,
                    scenarios.get(selected - listOffset(UiPage.ONLINE_HOST)).description(),
                    DESCRIPTION_LINES);
        }
        while (foot.size() < DESCRIPTION_LINES) {
            foot.add("");
        }
    }

    private void saying(String status) {
        hostStatus = status;
        hostStatusAt = page.index();
        describedHost = null;
    }

    /**
     * OPEN on the internet (G0101, G0103): asks the relay for a room with the password. The page stays up and says it
     * is asking; the answer is {@link #pollOpening}'s. A password half typed is told, and nothing is asked.
     */
    private void openRoom() {
        HostChoices choices = hostChoices();
        String password = choices.password();
        if (password == null) {
            page.jump(HOST_PASSWORD);
            saying(choices.problem());
            return;
        }
        context.settings().hostPassword(password);
        try {
            opening = OnlineSession.openThroughRelay(
                    context.netConfig(),
                    context.launchOptions(),
                    LocalMachineId.load(),
                    BuildVersion.current(),
                    password);
        } catch (RuntimeException e) {
            Log.error("Could not reach the relay", e);
            saying(OnlineSession.UNREACHABLE);
            return;
        }
        saying(strings.get("ui.host.connecting"));
        enableHost();
    }

    /**
     * One frame of the room being asked for: refused or unreachable, HOST GAME stays up and says why - no fallback to
     * the LAN (G0103); let in, the briefing opens with the relay's code at its head.
     */
    private void pollOpening(float delta) {
        OnlineSession asking = opening;
        if (asking == null) {
            return;
        }
        asking.advance(delta, idle);
        String refusal = asking.relayRefusal();
        String failure =
                refusal != null ? refusal : asking.relayUnreachable() ? OnlineSession.UNREACHABLE : asking.ended();
        if (failure != null) {
            Log.info(failure);
            opening = null;
            asking.close();
            saying(failure);
            enableHost();
        } else if (asking.relayAnswered()) {
            opening = null;
            online = asking;
            hostStatus = "";
            flow.apply(UiIntent.open(UiPage.BRIEFING));
        }
    }

    /** Back while the relay is asked: the attempt is dropped and the page is as it was. */
    private void giveUpOpening() {
        OnlineSession asking = opening;
        opening = null;
        if (asking != null) {
            asking.close();
        }
        hostStatus = "";
        describedHost = null;
        enableHost();
    }

    /** Leaves the briefing: the host ends the session, a joiner leaves it. True when the screen is being left. */
    private boolean leaveBriefing() {
        OnlineSession open = online;
        if (open == null) {
            return false;
        }
        boolean host = open.hosts();
        online = null;
        open.close();
        context.queueScreen(host ? new MenuScreen(context, "", UiPage.ONLINE_HOST) : new MenuScreen(context));
        return true;
    }

    /**
     * Under the briefing's list: for the host why the code line names no mission, for a joiner what the host's does
     * and that he waits - then the players, a row each, in their commanders' colours (G0101).
     */
    private void describeBriefing() {
        OnlineSession open = online;
        if (open == null) {
            return;
        }
        boolean host = open.hosts();
        List<Briefing.Row> rows = Briefing.rows(open.players());
        String line = host ? missions.problem() : open.note();
        ScenarioPlay.Host handedTo = open.handedOverHost();
        String signature = line + "|" + rows + "|" + handedTo;
        if (signature.equals(describedBriefing)) {
            return;
        }
        describedBriefing = signature;
        foot.clear();
        if (host) {
            foot.add(line.isEmpty() ? "" : strings.format(line, MissionChoices.tierLetters()));
        } else {
            foot.add(hostsMission(line));
            foot.add(strings.get("ui.briefing.waiting"));
        }
        if (handedTo != null) {
            foot.add(strings.format("ui.host.is", DispatchText.hostName(strings, handedTo.slot(), handedTo.peer())));
        }
        foot.add("");
        playersFrom = foot.size();
        briefingRows = rows;
        for (Briefing.Row row : rows) {
            String who = strings.format("ui.briefing.player", row.slot() + 1, row.machine());
            foot.add(row.online() ? who : who + "  " + strings.get("ui.briefing.offline"));
        }
    }

    /** The host's code line as a joiner reads it: the code, its tier and its length. */
    private String hostsMission(String line) {
        if (line.isEmpty()) {
            return strings.get("ui.briefing.none");
        }
        // The host's note is the code, then - when he changed them - the strength words (MissionChoices.note).
        int space = line.indexOf(' ');
        MissionCode code = MissionCode.parse(space < 0 ? line : line.substring(0, space));
        if (code == null) {
            return line;
        }
        String named =
                strings.format("ui.briefing.line", code.toString(), tierName(code.tier()), biomes(code.length()));
        Strength strength =
                space < 0 ? Strength.NORMAL : MissionCode.strengthOf("mission easy" + line.substring(space));
        return strength.normal()
                ? named
                : named + "  "
                        + strings.format("ui.briefing.strength", strength.healthPercent(), strength.countPercent());
    }

    /** A player's row in its commander's colour while he is on; dim - the page's own - once his link is gone. */
    private @Nullable Color footInk(int line) {
        if (flow.top() != UiPage.BRIEFING || playersFrom < 0 || line < playersFrom) {
            return null;
        }
        int at = line - playersFrom;
        if (at >= briefingRows.size() || !briefingRows.get(at).online()) {
            return null;
        }
        return palette.colorFor(Look.COMMANDER, briefingRows.get(at).slot());
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        context.hud().showSystemCursor();
        closePreview();
        OnlineSession open = online;
        if (open != null && !handedOver) {
            open.close();
        }
        OnlineSession asking = opening;
        if (asking != null) {
            asking.close();
            opening = null;
        }
    }
}
