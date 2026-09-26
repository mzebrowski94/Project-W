package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Input;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
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
import pl.mzebrows.pwt.ui.page.UiEffect;
import pl.mzebrows.pwt.ui.page.UiFlow;
import pl.mzebrows.pwt.ui.page.UiIntent;
import pl.mzebrows.pwt.ui.page.UiPage;

/**
 * A level's pages: the pause menu and the leave confirm over the view, the paused banner, and the
 * connecting panel before an online world has arrived ({@code UI:play...}, {@code UI:connecting}).
 *
 * <p>What a press does is {@link UiFlow}'s and where things are is {@code MenuLayout}'s, both tested;
 * this reads the devices into a {@link NavInput}, moves the selection, carries out the flow's effects on
 * the {@link ScenarioPlay} and draws. <b>The pause is shared; the menu is not</b>: {@code Esc} or a pad's
 * pause button opens the menu here at once and asks for the pause, which online lands a few ticks later;
 * somebody else's pause shows only the banner.
 *
 * <p>Offline, losing the window or a seated pad opens the menu (plan Q7, Q27): an alt-tab mid-fight
 * would otherwise cost the player his man. Online neither pauses anybody.
 */
final class PlayMenus {

    private final HudStyle style;
    private final ControlsConfig controls;
    private final UiStrings strings;
    private final MenuPainter painter;
    private final NavInputReader reader;
    private final UiFlow flow = new UiFlow(UiPage.PLAY);
    private final NavInput nav = new NavInput();
    private final InputModality modality = new InputModality();
    private final SelectionMemory memory = new SelectionMemory();
    private final boolean[] enabled = new boolean[8];
    private final List<String> head = new ArrayList<>(2);
    private final List<String> labels = new ArrayList<>(4);
    private final List<String> foot = new ArrayList<>(2);
    private final List<String> connecting = new ArrayList<>(1);

    private UiPage shown = UiPage.PLAY;
    private MenuSelection selection = new MenuSelection(1);
    private boolean hovering;
    private Rect panel = Rect.EMPTY;
    private float scrim;
    private boolean focusLost;
    private @Nullable String footFor;

    PlayMenus(
            Hud hud,
            HudStyle style,
            HudMetrics metrics,
            ControlsConfig controls,
            UiStrings strings,
            PlayerSeats seats) {
        this.style = style;
        this.controls = controls;
        this.strings = strings;
        this.painter = new MenuPainter(hud, style, metrics);
        this.reader = new NavInputReader(controls.ui(), seats);
    }

    /** Whether a page is open here: this machine's commanders stand still and its presses are the menu's. */
    boolean menuOpen() {
        return flow.menuOpen();
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
            navigate(play, players);
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
            int selected = modality.mode() == InputModality.Mode.POINTER && !hovering ? -1 : selection.index();
            panel = painter.page(
                    view, MenuLayout.Anchor.DOCKED_LEFT, head, labels, enabled, selected, foot(play), hint());
        } else if (flow.showsPausedBanner()) {
            painter.banner(
                    view, strings.get("ui.hud.paused") + "  " + strings.format("ui.hud.pausedHint", menuButton()));
        }
    }

    /** The connecting panel ({@code UI:connecting}): where it is connecting, and how to go back. */
    void drawConnecting(ScreenLayout layout, String connection) {
        connecting.clear();
        connecting.add(strings.format("ui.connecting.body", connection));
        painter.page(
                layout.view(), MenuLayout.Anchor.CENTRED, connecting, List.of(), enabled, -1, List.of(), backHint());
    }

    /** Whether the system pointer should show: in a menu, while the mouse is what the player is using. */
    boolean wantsPointer() {
        return flow.menuOpen() && modality.showsPointer();
    }

    private void navigate(ScenarioPlay play, PlayerInputs players) {
        List<MenuEntry> entries = MenuTree.page(flow.top()).entries();
        if (nav.up) {
            step(entries, -1);
        }
        if (nav.down) {
            step(entries, 1);
        }
        int under = panel.isEmpty()
                ? -1
                : painter.layout()
                        .entryAt(panel, MenuPainter.firstEntryLine(head), entries.size(), nav.pointerX, nav.pointerY);
        if (nav.pointerMoved) {
            hovering = under >= 0 && enabled[under];
            if (hovering) {
                selection.select(under);
            }
        }
        if (nav.pointerPressed && under >= 0 && enabled[under]) {
            selection.select(under);
            carryOut(flow.apply(entries.get(under).intent()), play, players);
        } else if (nav.confirm && enabled[selection.index()]) {
            carryOut(flow.apply(entries.get(selection.index()).intent()), play, players);
        } else if (nav.back || nav.pointerSecondary) {
            carryOut(flow.apply(UiIntent.BACK), play, players);
        } else if (nav.menuFromPad) {
            carryOut(flow.apply(UiIntent.RESUME), play, players);
        }
    }

    /** Moves the selection, over any entry that cannot be confirmed now. */
    private void step(List<MenuEntry> entries, int direction) {
        hovering = false;
        for (int tries = 0; tries < entries.size(); tries++) {
            if (direction < 0) {
                selection.previous();
            } else {
                selection.next();
            }
            if (enabled[selection.index()]) {
                return;
            }
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
                memory.set(shown, selection.index());
            }
        }
        shown = top;
        panel = Rect.EMPTY;
        footFor = null;
        if (top == UiPage.PLAY) {
            return;
        }
        MenuPage page = MenuTree.page(top);
        head.clear();
        if (!page.titleKey().isEmpty()) {
            head.add(strings.get(page.titleKey()));
        }
        if (!page.bodyKey().isEmpty()) {
            boolean hostLeaving = top == UiPage.LEAVE_CONFIRM && play.online() && play.canRestart();
            head.add(strings.get(hostLeaving ? "ui.leave.bodyHost" : page.bodyKey()));
        }
        labels.clear();
        for (MenuEntry entry : page.entries()) {
            labels.add(strings.get(entry.labelKey()));
        }
        refreshEnabled(page.entries());
        selection = new MenuSelection(page.entries().size());
        selection.select(memory.get(top, page.defaultSelection(), page.entries().size()));
        if (!enabled[selection.index()]) {
            step(page.entries(), 1);
        }
        hovering = false;
    }

    private void refreshEnabled(List<MenuEntry> entries) {
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
            foot.add(invitation);
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
            return style.scrimAlpha();
        }
        return flow.showsPausedBanner() ? style.bannerScrimAlpha() : 0f;
    }

    /** Moves the scrim towards where it should be; at once with motion off. */
    private float approach(float current, float wanted, float frameSeconds) {
        float seconds = style.scrimSeconds() * style.motionScale();
        if (seconds <= 0f) {
            return wanted;
        }
        float step = frameSeconds / seconds * Math.max(style.scrimAlpha(), 1e-3f);
        return current < wanted ? Math.min(wanted, current + step) : Math.max(wanted, current - step);
    }

    private String hint() {
        return strings.format("ui.hint.select", confirmButton()) + "   " + backHint();
    }

    private String backHint() {
        return strings.format("ui.hint.back", backButton());
    }

    private String confirmButton() {
        return modality.padHints()
                ? PadLabels.of(controls.ui().padConfirm(), modality.playStation())
                : keyName(controls.ui().confirm());
    }

    private String backButton() {
        return modality.padHints()
                ? PadLabels.of(controls.ui().padBack(), modality.playStation())
                : keyName(controls.ui().back());
    }

    private String menuButton() {
        return modality.padHints()
                ? PadLabels.of(controls.pad().pause(), modality.playStation())
                : keyName(controls.ui().back());
    }

    private static String keyName(int keycode) {
        String name = Input.Keys.toString(keycode);
        return name == null ? "?" : name.toUpperCase(Locale.ROOT);
    }
}
