package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.hud.DispatchText;
import pl.mzebrows.pwt.client.hud.Hud;
import pl.mzebrows.pwt.client.hud.MenuPainter;
import pl.mzebrows.pwt.client.hud.PageHints;
import pl.mzebrows.pwt.client.hud.UiStrings;
import pl.mzebrows.pwt.client.input.ControlsConfig;
import pl.mzebrows.pwt.client.input.GamepadSource;
import pl.mzebrows.pwt.client.input.InputReader;
import pl.mzebrows.pwt.client.input.KeyHolds;
import pl.mzebrows.pwt.client.input.KeyPresses;
import pl.mzebrows.pwt.client.input.NavInputReader;
import pl.mzebrows.pwt.client.input.PageControls;
import pl.mzebrows.pwt.client.play.HarnessCommand;
import pl.mzebrows.pwt.client.play.MissionCode;
import pl.mzebrows.pwt.client.play.MissionRecords;
import pl.mzebrows.pwt.client.play.ScenarioPlay;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.ui.hud.LevelTime;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;
import pl.mzebrows.pwt.ui.nav.InputModality;
import pl.mzebrows.pwt.ui.nav.NavInput;
import pl.mzebrows.pwt.ui.nav.SelectionMemory;
import pl.mzebrows.pwt.ui.page.Briefing;
import pl.mzebrows.pwt.ui.page.MenuEntry;
import pl.mzebrows.pwt.ui.page.MenuLayout;
import pl.mzebrows.pwt.ui.page.MenuPage;
import pl.mzebrows.pwt.ui.page.MenuTree;
import pl.mzebrows.pwt.ui.page.PageMotion;
import pl.mzebrows.pwt.ui.page.UiEffect;
import pl.mzebrows.pwt.ui.page.UiFlow;
import pl.mzebrows.pwt.ui.page.UiIntent;
import pl.mzebrows.pwt.ui.page.UiPage;

/**
 * A level's pages: the pause menu, the leave confirm, the controls and the result over the view, the paused
 * banner, and the connecting panel before an online world has arrived ({@code UI:play...}, {@code
 * UI:connecting}). The controls are the main menu's page - PROMPTS and the bindings - centred on the whole UI
 * canvas, because the table is taller than the view between the bars.
 *
 * <p>What a press does is {@link UiFlow}'s, where things are is {@code MenuLayout}'s, and the selection is
 * {@link PageControls}'; this carries out the flow's effects on the {@link ScenarioPlay} and draws.
 * <b>The pause is shared; the menu is not</b>: {@code Esc} or a pad's pause button opens the menu here at
 * once and asks for the pause, which online lands a few ticks later; somebody else's pause shows only the
 * banner.
 *
 * <p>Offline, losing the window or a seated pad opens the menu (plan Q7, Q27): an alt-tab mid-fight would
 * otherwise cost the player his man. Online neither pauses anybody.
 */
final class PlayMenus {

    private final GraphicConfig graphic;
    private final UiStrings strings;
    private final PageHints hints;
    private final MenuPainter painter;
    private final NavInputReader reader;
    private final UiFlow flow = new UiFlow(UiPage.PLAY);
    private final NavInput nav = new NavInput();
    private final InputModality modality = new InputModality();
    private final SelectionMemory memory = new SelectionMemory();
    private final PageControls page = new PageControls();
    private final PageMotion motion;
    private final List<String> head = new ArrayList<>(2);
    private final List<String> labels = new ArrayList<>(4);
    private final List<String> foot = new ArrayList<>(2);
    private final List<String> connecting = new ArrayList<>(1);

    /** The controls over the pause: their switch and, under their entries, their table of bindings. */
    private final ControlsPage controls;

    /** On the controls page, how much of each foot line is bright: the table's headings and actions. */
    private final IntUnaryOperator tableBright = this::brightInTable;

    private UiPage shown = UiPage.PLAY;
    private Rect panel = Rect.EMPTY;
    private float scrim;
    private boolean focusLost;
    private @Nullable String footFor;

    private ScenarioPlay.@Nullable Host footHost;

    /** The players the pause page's foot lists, as last written, and the foot line the first of them is on (-1: none). */
    private List<Briefing.Row> footPlayers = List.of();

    private int playersFrom = -1;

    /** The pause in force is a solo draft's (G0049): no banner and no scrim over the game being chosen for. */
    private boolean quiet;

    /** The level was won, as last read: the result's title (G0066). */
    private boolean won;

    /** The level's time, as last read: the result's TIME on a win. */
    private long outcomeTick;

    private float tickSeconds;

    /** The control points taken and all of them, as last read: a mission's result shows them. */
    private int pointsTaken;

    private int pointsTotal;

    /** The mission being played, or null for any other level: its code, tier and time on the pause page. */
    private @Nullable MissionCode mission;

    /** What the host made the swarm (G0106): shown on the pause page when it is not the tier's own. */
    private String missionStrength = "";

    /** The day it is the daily of, or null if it is none. */
    private @Nullable LocalDate missionDay;

    /** The tick the pause page's mission line was written for, or -1 for none. */
    private long missionFootTick = -1;

    /** What this machine remembers of the dailies won: written on the win's tick, read for NEW BEST. */
    private final MissionRecords records;

    /**
     * This machine has seen the world before its outcome, so a win is one it was present at (G0067): set on
     * every frame before it, spent by the win. A world that arrives already won - a joiner too late, or a heal
     * after the win - is never offered, nor is the same win twice.
     */
    private boolean present;

    /** The win beat this machine's best for the daily: the result says NEW BEST. */
    private boolean newBest;

    PlayMenus(
            Hud hud,
            GraphicConfig graphic,
            HudMetrics metrics,
            ControlsConfig controls,
            UiStrings strings,
            GamepadSource pads,
            MissionRecords records,
            LocalSettings settings) {
        this.graphic = graphic;
        this.controls = new ControlsPage(controls, settings, strings);
        this.records = records;
        this.strings = strings;
        this.hints = new PageHints(controls, strings);
        this.painter = new MenuPainter(hud, graphic, metrics);
        painter.footInk(this::footInk);
        this.reader = new NavInputReader(controls.ui(), pads);
        this.motion = new PageMotion(
                graphic.ui().motion().pageSeconds(),
                graphic.ui().motion().pageSlide(),
                graphic.ui().motionScale());
    }

    /** The level is this mission - a daily of {@code day}, or of none - or, for null, no mission at all. */
    void mission(@Nullable MissionCode code, @Nullable LocalDate day, String strengthWords) {
        mission = code;
        missionStrength = strengthWords;
        missionDay = day;
        missionFootTick = -1;
    }

    /** Whether a page is open here: this machine's commanders stand still and its presses are the menu's. */
    boolean menuOpen() {
        return flow.menuOpen();
    }

    /** Whether the pause in force is the draft's own, which the paused banner does not show over. */
    void quietPause(boolean draft) {
        quiet = draft;
    }

    /** The window lost focus; offline, the next frame opens the menu. */
    void focusLost() {
        focusLost = true;
    }

    /**
     * One frame of the level's pages, after the devices were read and before the session advances.
     *
     * @param readout the world as the last frame read it: whether the result is due, and which
     * @return whether a menu opened or closed - a change of context, whose held presses the caller spends
     */
    boolean update(
            float frameSeconds,
            ScenarioPlay play,
            InputReader input,
            PlayerInputs players,
            KeyPresses keys,
            KeyHolds held,
            ScreenLayout layout,
            Readout readout) {
        boolean wasOpen = flow.menuOpen();
        reader.read(nav, frameSeconds, wasOpen, input.pauseRequested(), keys, held, layout);
        modality.update(nav);
        if (nav.any()) {
            motion.finish();
        }
        motion.advance(frameSeconds);
        flow.observe(play.paused(), play.canRestart());
        won = readout.won;
        outcomeTick = readout.outcomeTick;
        tickSeconds = readout.tickSeconds;
        pointsTaken = readout.pointsTaken;
        pointsTotal = readout.pointsTotal;
        flow.observeResult(readout.resultDue(graphic.ui().resultHoldSeconds()), hosting(play), play.briefed());

        boolean lostAPad = input.seats().nextNewlyUnplugged() >= 0;
        if ((focusLost || lostAPad) && !play.online() && flow.top() == UiPage.PLAY) {
            carryOut(flow.apply(UiIntent.PAUSE), play, players);
        }
        focusLost = false;

        if (flow.top() == UiPage.PLAY) {
            if (nav.menu) {
                carryOut(flow.apply(UiIntent.PAUSE), play, players);
            }
        } else {
            navigate(play, players, area(layout));
        }
        follow(play);
        scrim = approach(scrim, target(), frameSeconds);
        return flow.menuOpen() != wasOpen;
    }

    /** Before an online world has arrived: back leaves. */
    void updateConnecting(
            float frameSeconds,
            ScenarioPlay play,
            PlayerInputs players,
            KeyPresses keys,
            KeyHolds held,
            InputReader input,
            ScreenLayout layout) {
        reader.read(nav, frameSeconds, true, input.pauseRequested(), keys, held, layout);
        modality.update(nav);
        if (nav.back || nav.menu || nav.pointerSecondary) {
            play.apply(HarnessCommand.LEAVE, players);
        }
    }

    /** Draws over the view, after the bars: the scrim, then the menu or the banner. */
    void draw(ScreenLayout layout, ScenarioPlay play) {
        Rect view = layout.view();
        painter.scrim(view, scrim);
        if (flow.menuOpen()) {
            List<String> under = foot(play);
            Rect area = area(layout);
            panel = painter.page(
                    area,
                    flow.top() == UiPage.PAUSE_CONTROLS ? MenuLayout.Anchor.CENTRED : MenuLayout.Anchor.DOCKED_LEFT,
                    head,
                    labels,
                    page.enabled(),
                    page.shown(modality),
                    page.scroll(),
                    painter.visibleEntries(area, head, labels.size(), under.size()),
                    under,
                    tableBright,
                    hints.selectAndBack(modality),
                    motion.offsetX(),
                    motion.alpha());
        } else if (flow.showsPausedBanner() && !quiet) {
            painter.banner(
                    view,
                    strings.get("ui.hud.paused") + "  "
                            + strings.format("ui.hud.pausedHint", hints.menuButton(modality)));
        }
    }

    /** The connecting panel ({@code UI:connecting}): where it is connecting, and how to go back. */
    void drawConnecting(ScreenLayout layout, String connection) {
        connecting.clear();
        connecting.add(strings.format("ui.connecting.body", connection));
        painter.page(
                layout.view(),
                MenuLayout.Anchor.CENTRED,
                connecting,
                List.of(),
                page.enabled(),
                -1,
                0,
                0,
                List.of(),
                hints.back(modality),
                0,
                1f);
    }

    /** Whether a page or the paused banner is over the view here: what a toast waits behind. */
    boolean coversView() {
        return flow.menuOpen() || (flow.showsPausedBanner() && !quiet);
    }

    /**
     * Whether the page on show is one the side's upgrades are listed beside: the pause menu itself - not
     * what is opened over it - and the result.
     */
    boolean showsUpgradesBeside() {
        return flow.menuOpen() && (flow.top() == UiPage.PAUSE || flow.top() == UiPage.RESULT);
    }

    /** Where the page's panel rests - not where its arrival has it this frame - for what keeps clear of it. */
    Rect pagePanel() {
        return new Rect(panel.x() - motion.offsetX(), panel.y(), panel.width(), panel.height());
    }

    /** Where the pointer is on the UI canvas, as this frame's navigation read it. */
    int pointerX() {
        return nav.pointerX;
    }

    int pointerY() {
        return nav.pointerY;
    }

    /** How opaque the page's arrival has it, for what is drawn beside it. */
    float pageAlpha() {
        return motion.alpha();
    }

    /** Whether the system pointer should show: in a menu, while the mouse is what the player is using. */
    boolean wantsPointer() {
        return flow.menuOpen() && modality.showsPointer();
    }

    /** The open page's entries: a mission's result has COPY CODE too. */
    private MenuPage content(UiPage top) {
        return top == UiPage.RESULT ? MenuTree.result(mission != null) : MenuTree.page(top);
    }

    private void navigate(ScenarioPlay play, PlayerInputs players, Rect area) {
        List<MenuEntry> entries = content(flow.top()).entries();
        int visible =
                painter.visibleEntries(area, head, entries.size(), foot(play).size());
        if (flow.top() == UiPage.PAUSE_CONTROLS
                && (nav.left || nav.right)
                && page.index() == MenuTree.CONTROLS_PROMPTS) {
            flipPrompts();
        }
        int chosen = page.handle(nav, painter.layout(), panel, MenuPainter.firstEntryLine(head), visible);
        if (chosen >= 0) {
            carryOut(flow.apply(entries.get(chosen).intent()), play, players);
        } else if (chosen == PageControls.BACK) {
            carryOut(flow.apply(UiIntent.BACK), play, players);
        } else if (nav.menuFromPad) {
            carryOut(flow.apply(UiIntent.RESUME), play, players);
        }
    }

    /** Keeps the drawn page in step with the flow's: its words, which entries work, where it starts. */
    private void follow(ScenarioPlay play) {
        UiPage top = flow.top();
        if (top == shown && top != UiPage.PLAY) {
            refreshEnabled(content(top).entries());
            return;
        }
        if (shown != UiPage.PLAY) {
            if (shown == UiPage.LEAVE_CONFIRM) {
                memory.forget(shown);
            } else {
                memory.set(shown, page.index());
            }
        }
        boolean deeper = top != UiPage.PLAY && top.parent() == shown;
        shown = top;
        panel = Rect.EMPTY;
        footFor = null;
        footHost = null;
        if (top == UiPage.PLAY) {
            return;
        }
        motion.arrive(deeper ? 1 : -1);
        MenuPage content = content(top);
        head.clear();
        if (top == UiPage.RESULT) {
            head.add(strings.get(won ? "ui.result.won" : "ui.result.lost"));
            if (won) {
                head.add(strings.format("ui.result.time", LevelTime.text(outcomeTick, tickSeconds)));
            }
            describeMission();
        } else if (!content.titleKey().isEmpty()) {
            head.add(strings.get(content.titleKey()));
        }
        if (!content.bodyKey().isEmpty()) {
            boolean hostLeaving = top == UiPage.LEAVE_CONFIRM && hosting(play);
            painter.addWrapped(head, strings.get(hostLeaving ? "ui.leave.bodyHost" : content.bodyKey()), 3);
        }
        labels.clear();
        for (MenuEntry entry : content.entries()) {
            labels.add(strings.get(entry.labelKey()));
        }
        if (top == UiPage.PAUSE_CONTROLS) {
            controls.open(modality.playStation(), labels);
        }
        page.reset(
                content.entries().size(),
                memory.get(top, content.defaultSelection(), content.entries().size()));
        refreshEnabled(content.entries());
        page.settle();
    }

    private void flipPrompts() {
        controls.flip(labels);
    }

    private int brightInTable(int line) {
        return flow.top() == UiPage.PAUSE_CONTROLS ? controls.bright(line) : 0;
    }

    /** The controls page over the whole canvas - its table is taller than the view - the rest over the view. */
    private Rect area(ScreenLayout layout) {
        return flow.top() == UiPage.PAUSE_CONTROLS ? layout.canvas() : layout.view();
    }

    private void refreshEnabled(List<MenuEntry> entries) {
        boolean[] enabled = page.enabled();
        for (int i = 0; i < entries.size(); i++) {
            enabled[i] = MenuTree.enabled(entries.get(i), flow);
        }
    }

    /**
     * Offers a daily's win to the record on the frame it is first read - the outcome tick as the client sees
     * it, not the result three seconds on, so a machine that drops in the hold still writes (G0067). Called
     * with each readout as it is filled from the world, so the first a world shows is its own.
     */
    void record(Readout readout) {
        MissionCode played = mission;
        if (!readout.won) {
            present = !readout.failed;
            newBest = false;
            return;
        }
        if (present && played != null) {
            newBest = records.offer(missionDay, played.tier(), readout.outcomeTick);
        }
        present = false;
    }

    /** A mission's lines on the result, under the outcome and the time: points, code and tier, the daily's day. */
    private void describeMission() {
        MissionCode played = mission;
        if (played == null) {
            return;
        }
        if (won && newBest) {
            head.add(strings.get("ui.result.best"));
        }
        head.add(strings.format("ui.result.points", pointsTaken, pointsTotal));
        head.add(strings.format(
                "ui.result.code",
                played,
                strings.get("ui.tier." + played.tier()),
                strings.format("ui.missions.biomes", played.length())));
        LocalDate day = missionDay;
        if (day != null) {
            head.add(strings.format("ui.result.daily", day));
        }
    }

    /**
     * Under the pause menu's entries: in a mission its code, tier and time so far - the only place the code
     * and the time are seen during play - and while this machine hosts, the room code and addresses.
     */
    private List<String> foot(ScenarioPlay play) {
        if (flow.top() == UiPage.PAUSE_CONTROLS) {
            return controls.table();
        }
        boolean pause = flow.top() == UiPage.PAUSE;
        String invitation = pause ? play.invitation() : null;
        Simulation world = play.simulation();
        MissionCode played = mission;
        long tick = pause && played != null && world != null ? world.tick() : -1;
        ScenarioPlay.Host host = pause ? play.handedOverHost() : null;
        List<Briefing.Row> players = pause && play.online() ? Briefing.rows(play.players()) : List.of();
        if (tick == missionFootTick
                && Objects.equals(invitation, footFor)
                && Objects.equals(host, footHost)
                && players.equals(footPlayers)) {
            return foot;
        }
        missionFootTick = tick;
        footFor = invitation;
        footHost = host;
        footPlayers = players;
        foot.clear();
        playersFrom = -1;
        if (tick >= 0 && played != null) {
            foot.add(strings.format(
                    "ui.pause.mission",
                    played,
                    strings.get("ui.tier." + played.tier()),
                    LevelTime.text(tick, tickSeconds),
                    strings.format("ui.missions.biomes", played.length())));
            if (!missionStrength.isEmpty()) {
                foot.add(missionStrength);
            }
        }
        if (invitation != null) {
            painter.addWrapped(foot, invitation, 2);
        }
        if (host != null) {
            foot.add(strings.format("ui.host.is", DispatchText.hostName(strings, host.slot(), host.peer())));
        }
        if (!players.isEmpty()) {
            // Who is who: each in the colour of their commander, so the colour on the field has a name.
            foot.add("");
            foot.add(strings.get("ui.pause.players"));
            playersFrom = foot.size();
            for (Briefing.Row row : players) {
                String who = strings.format("ui.briefing.player", row.slot() + 1, row.machine());
                foot.add(row.online() ? who : who + "  " + strings.get("ui.briefing.offline"));
            }
        }
        return foot;
    }

    /** A player's row in their commander's colour while they are on; the page's dim - once their link is gone. */
    private @Nullable Color footInk(int line) {
        int at = line - playersFrom;
        if (flow.top() != UiPage.PAUSE || playersFrom < 0 || at < 0 || at >= footPlayers.size()) {
            return null;
        }
        Briefing.Row row = footPlayers.get(at);
        return row.online() ? graphic.palette().colorFor(Look.COMMANDER, row.slot()) : null;
    }

    /** This machine hosts an online session: leaving ends it for everybody. */
    private static boolean hosting(ScenarioPlay play) {
        return play.online() && play.canRestart();
    }

    private void carryOut(UiEffect effect, ScenarioPlay play, PlayerInputs players) {
        switch (effect.kind()) {
            case REQUEST_PAUSE -> play.setPaused(true);
            case REQUEST_RESUME -> play.setPaused(false);
            case RESTART -> play.apply(HarnessCommand.RESTART, players);
            case CHOOSE -> {
                // PLAYER 1 is locked over a level (MenuTree.enabled): PROMPTS is the one switch here.
                if (effect.index() == MenuTree.CONTROLS_PROMPTS) {
                    flipPrompts();
                }
            }
            case LEAVE -> play.apply(HarnessCommand.LEAVE, players);
            case TO_BRIEFING -> play.toBriefing();
            case COPY_CODE -> {
                MissionCode played = mission;
                if (played != null) {
                    Gdx.app.getClipboard().setContents(played.toString());
                }
            }
            default -> {
                // The main menu's effects never arise in a level.
            }
        }
    }

    private float target() {
        if (flow.menuOpen()) {
            return graphic.ui().scrimAlpha();
        }
        return flow.showsPausedBanner() && !quiet ? graphic.ui().bannerScrimAlpha() : 0f;
    }

    /** Moves the scrim towards where it should be; at once with motion off. */
    private float approach(float current, float wanted, float frameSeconds) {
        float seconds = graphic.ui().motion().scrimSeconds() * graphic.ui().motionScale();
        if (seconds <= 0f) {
            return wanted;
        }
        float step = frameSeconds / seconds * Math.max(graphic.ui().scrimAlpha(), 1e-3f);
        return current < wanted ? Math.min(wanted, current + step) : Math.max(wanted, current - step);
    }
}
