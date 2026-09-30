package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.ArrayList;
import java.util.List;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;
import pl.mzebrows.pwt.ui.nav.InputModality;
import pl.mzebrows.pwt.ui.nav.NavInput;
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
 * The main menu ({@code UI:main...}): one libGDX {@code Screen} holding every page as a stack, because
 * swapping screens disposes GL resources and a page change must never cost that. The campaign and the
 * hosting list show every scenario in {@link Scenarios}, the whole registry - there is no separate menu
 * definition to keep in sync.
 *
 * <p>What a press does is {@link UiFlow}'s and the selection is {@link PageControls}'; this turns the
 * flow's effects into screens and draws. Launch to a level is two confirms: MAIN opens on CAMPAIGN, and
 * the campaign list on the scenario played last this run.
 */
public final class MenuScreen extends ScreenAdapter {

    private static final int MAX_NUMBER_KEYS = 9;
    private static final int DESCRIPTION_LINES = 2;
    private static final int BODY_LINES = 3;

    private final GameContext context;
    private final UiStrings strings = UiStrings.load();
    private final List<Scenario> scenarios = Scenarios.all();
    private final UiFlow flow = new UiFlow(UiPage.MAIN);
    private final NavInput nav = new NavInput();
    private final InputModality modality = new InputModality();
    private final PageControls page = new PageControls();
    private final PageHints hints;
    private final MenuPainter painter;
    private final NavInputReader reader;
    private final PageMotion motion;
    private final KeyPresses keys = keycode -> Gdx.input.isKeyJustPressed(keycode);
    private final KeyHolds held = keycode -> Gdx.input.isKeyPressed(keycode);
    private final List<String> head = new ArrayList<>(2);
    private final List<String> labels = new ArrayList<>();
    private final List<String> foot = new ArrayList<>(DESCRIPTION_LINES);

    /** Why the player is back here, if the game he was in ended without him asking; empty otherwise. */
    private String notice;

    private UiPage shown = UiPage.PLAY;
    private int describedFor = -1;
    private Rect panel = Rect.EMPTY;

    public MenuScreen(GameContext context) {
        this(context, "");
    }

    /** The menu, saying why the player is back - an online game that ended under him. */
    public MenuScreen(GameContext context, String notice) {
        this(context, notice, UiPage.MAIN);
    }

    /** The menu opened on a page under MAIN - back from joining, the online page. */
    public MenuScreen(GameContext context, String notice, UiPage start) {
        this.context = context;
        this.notice = notice;
        HudStyle style = context.graphicConfig().hud();
        this.hints = new PageHints(context.controlsConfig(), strings);
        this.painter = new MenuPainter(
                context.hud(), style, context.graphicConfig().layout().metrics());
        this.reader = new NavInputReader(context.controlsConfig().ui(), context.gamepads());
        this.motion = new PageMotion(style.pageSeconds(), style.pageSlide(), style.motionScale());
        if (start.parent() == UiPage.MAIN) {
            flow.apply(UiIntent.open(start));
        }
    }

    @Override
    public void render(float delta) {
        ScreenLayout layout = context.hud().layout();
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
        ScreenUtils.clear(context.graphicConfig().hud().bar());
        Rect view = layout.canvas();
        describe();
        panel = painter.page(
                view,
                MenuLayout.Anchor.CENTRED,
                head,
                labels,
                page.enabled(),
                page.shown(modality),
                page.scroll(),
                painter.visibleEntries(view, head, labels.size(), foot.size()),
                foot,
                hints.selectAndBack(modality),
                motion.offsetX(),
                motion.alpha());
    }

    /** Applies this frame's presses. False when the screen is being left and should draw nothing more. */
    private boolean handle(ScreenLayout layout) {
        UiPage top = flow.top();
        if (isScenarioList(top)) {
            for (int i = 0; i < scenarios.size() && i < MAX_NUMBER_KEYS; i++) {
                if (keys.justPressed(Input.Keys.NUM_1 + i) && page.jump(i)) {
                    return carryOut(flow.apply(intentFor(top, i)));
                }
            }
        }
        int visible = painter.visibleEntries(layout.canvas(), head, labels.size(), foot.size());
        int chosen = page.handle(nav, painter.layout(), panel, MenuPainter.firstEntryLine(head), visible);
        if (chosen >= 0) {
            return carryOut(flow.apply(intentFor(top, chosen)));
        }
        if (chosen == PageControls.BACK) {
            return carryOut(flow.apply(UiIntent.BACK));
        }
        return true;
    }

    private UiIntent intentFor(UiPage top, int entry) {
        return switch (top) {
            case CAMPAIGN -> UiIntent.start(entry);
            case ONLINE_HOST -> UiIntent.host(entry);
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
                return !host(effect.index());
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
        if (shown != UiPage.PLAY && shown != UiPage.EXIT_CONFIRM) {
            context.selections().set(shown, page.index());
        }
        if (top == UiPage.ONLINE_JOIN) {
            // Joining is its own screen: it types, it listens on the network.
            flow.apply(UiIntent.BACK);
            context.queueScreen(new JoinScreen(context));
            return;
        }
        motion.arrive(shown == UiPage.PLAY || top.parent() == shown ? 1 : -1);
        shown = top;
        panel = Rect.EMPTY;
        describedFor = -1;
        head.clear();
        labels.clear();
        int defaultSelection = 0;
        if (isScenarioList(top)) {
            head.add(strings.get(top == UiPage.CAMPAIGN ? "ui.campaign.title" : "ui.host.title"));
            for (Scenario scenario : scenarios) {
                labels.add(scenario.name());
            }
            // Hosting opens where the campaign was: the scenario played last is the likeliest to host.
            defaultSelection = context.selections().get(UiPage.CAMPAIGN, 0, scenarios.size());
        } else {
            MenuPage content = MenuTree.page(top);
            if (!content.titleKey().isEmpty()) {
                head.add(strings.get(content.titleKey()));
            }
            if (!content.bodyKey().isEmpty()) {
                painter.addWrapped(head, strings.get(content.bodyKey()), BODY_LINES);
            }
            if (top == UiPage.MAIN && !notice.isEmpty()) {
                painter.addWrapped(head, notice, BODY_LINES);
            }
            for (MenuEntry entry : content.entries()) {
                labels.add(strings.get(entry.labelKey()));
            }
            defaultSelection = content.defaultSelection();
        }
        int remembered = top == UiPage.EXIT_CONFIRM
                ? defaultSelection
                : context.selections().get(top, defaultSelection, labels.size());
        page.reset(labels.size(), remembered);
    }

    /**
     * Under a scenario list: what the selected one is for, on at most two lines - always two, the second
     * blank if it needs one, so the panel keeps its height as the selection moves.
     */
    private void describe() {
        if (!isScenarioList(flow.top())) {
            foot.clear();
            return;
        }
        int selected = page.index();
        if (selected == describedFor) {
            return;
        }
        describedFor = selected;
        foot.clear();
        painter.addWrapped(foot, scenarios.get(selected).description(), DESCRIPTION_LINES);
        while (foot.size() < DESCRIPTION_LINES) {
            foot.add("");
        }
    }

    private static boolean isScenarioList(UiPage page) {
        return page == UiPage.CAMPAIGN || page == UiPage.ONLINE_HOST;
    }

    private void start(int index) {
        Scenario scenario = scenarios.get(index);
        context.selections().set(UiPage.CAMPAIGN, index);
        Log.info("Starting scenario " + scenario.name());
        context.queueScreen(new ScenarioScreen(context, new ScenarioSession(scenario, context.simConfig())));
    }

    /**
     * Hosts the chosen scenario online and goes straight into it: the host plays from the first frame and
     * friends drop in (G0038). If it cannot, the player stays here and is told why.
     */
    private boolean host(int index) {
        Scenario scenario = scenarios.get(index);
        context.selections().set(UiPage.CAMPAIGN, index);
        try {
            context.queueScreen(new ScenarioScreen(
                    context,
                    OnlineSession.host(
                            scenario,
                            context.simConfig(),
                            context.worldConfigText(),
                            context.netConfig(),
                            context.launchOptions())));
            return true;
        } catch (RuntimeException e) {
            Log.error("Could not host " + scenario.name(), e);
            notice = "Could not host " + scenario.name() + ": " + e.getMessage();
            flow.apply(UiIntent.BACK);
            flow.apply(UiIntent.BACK);
            return false;
        }
    }

    @Override
    public void hide() {
        context.hud().showSystemCursor();
    }
}
