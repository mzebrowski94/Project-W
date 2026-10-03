package pl.mzebrows.pwt.client;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
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
import pl.mzebrows.pwt.client.play.ScenarioPlay;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;
import pl.mzebrows.pwt.ui.nav.InputModality;
import pl.mzebrows.pwt.ui.nav.NavInput;
import pl.mzebrows.pwt.ui.nav.SelectionMemory;
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
 * A level's pages: the pause menu and the leave confirm over the view, the paused banner, and the
 * connecting panel before an online world has arrived ({@code UI:play...}, {@code UI:connecting}).
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

    private UiPage shown = UiPage.PLAY;
    private Rect panel = Rect.EMPTY;
    private float scrim;
    private boolean focusLost;
    private @Nullable String footFor;

    /** The pause in force is a solo draft's (G0049): no banner and no scrim over the game being chosen for. */
    private boolean quiet;

    PlayMenus(
            Hud hud,
            GraphicConfig graphic,
            HudMetrics metrics,
            ControlsConfig controls,
            UiStrings strings,
            GamepadSource pads) {
        this.graphic = graphic;
        this.strings = strings;
        this.hints = new PageHints(controls, strings);
        this.painter = new MenuPainter(hud, graphic, metrics);
        this.reader = new NavInputReader(controls.ui(), pads);
        this.motion = new PageMotion(
                graphic.ui().motion().pageSeconds(),
                graphic.ui().motion().pageSlide(),
                graphic.ui().motionScale());
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
     * @return whether a menu opened or closed - a change of context, whose held presses the caller spends
     */
    boolean update(
            float frameSeconds,
            ScenarioPlay play,
            InputReader input,
            PlayerInputs players,
            KeyPresses keys,
            KeyHolds held,
            ScreenLayout layout) {
        boolean wasOpen = flow.menuOpen();
        reader.read(nav, frameSeconds, wasOpen, input.pauseRequested(), keys, held, layout);
        modality.update(nav);
        if (nav.any()) {
            motion.finish();
        }
        motion.advance(frameSeconds);
        flow.observe(play.paused(), play.canRestart());

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
            navigate(play, players, layout.view());
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
            panel = painter.page(
                    view,
                    MenuLayout.Anchor.DOCKED_LEFT,
                    head,
                    labels,
                    page.enabled(),
                    page.shown(modality),
                    page.scroll(),
                    painter.visibleEntries(view, head, labels.size(), under.size()),
                    under,
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

    /** Whether the pause menu itself is the page on show - not the leave confirm over it. */
    boolean showsPausePage() {
        return flow.menuOpen() && flow.top() == UiPage.PAUSE;
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

    private void navigate(ScenarioPlay play, PlayerInputs players, Rect view) {
        List<MenuEntry> entries = MenuTree.page(flow.top()).entries();
        int visible = painter.visibleEntries(view, head, entries.size(), foot.size());
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
            refreshEnabled(MenuTree.page(top).entries());
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
        if (top == UiPage.PLAY) {
            return;
        }
        motion.arrive(deeper ? 1 : -1);
        MenuPage content = MenuTree.page(top);
        head.clear();
        if (!content.titleKey().isEmpty()) {
            head.add(strings.get(content.titleKey()));
        }
        if (!content.bodyKey().isEmpty()) {
            boolean hostLeaving = top == UiPage.LEAVE_CONFIRM && play.online() && play.canRestart();
            painter.addWrapped(head, strings.get(hostLeaving ? "ui.leave.bodyHost" : content.bodyKey()), 3);
        }
        labels.clear();
        for (MenuEntry entry : content.entries()) {
            labels.add(strings.get(entry.labelKey()));
        }
        page.reset(
                content.entries().size(),
                memory.get(top, content.defaultSelection(), content.entries().size()));
        refreshEnabled(content.entries());
        page.settle();
    }

    private void refreshEnabled(List<MenuEntry> entries) {
        boolean[] enabled = page.enabled();
        for (int i = 0; i < entries.size(); i++) {
            enabled[i] = MenuTree.enabled(entries.get(i), flow);
        }
    }

    /** Under the pause menu's entries, while this machine hosts: the room code and addresses. */
    private List<String> foot(ScenarioPlay play) {
        String invitation = flow.top() == UiPage.PAUSE ? play.invitation() : null;
        if (invitation == null) {
            foot.clear();
            footFor = null;
        } else if (!invitation.equals(footFor)) {
            foot.clear();
            painter.addWrapped(foot, invitation, 2);
            footFor = invitation;
        }
        return foot;
    }

    private void carryOut(UiEffect effect, ScenarioPlay play, PlayerInputs players) {
        switch (effect.kind()) {
            case REQUEST_PAUSE -> play.setPaused(true);
            case REQUEST_RESUME -> play.setPaused(false);
            case RESTART -> play.apply(HarnessCommand.RESTART, players);
            case LEAVE -> play.apply(HarnessCommand.LEAVE, players);
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
