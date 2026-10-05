package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.hud.Hud;
import pl.mzebrows.pwt.client.hud.MenuPainter;
import pl.mzebrows.pwt.client.hud.PageHints;
import pl.mzebrows.pwt.client.hud.UiStrings;
import pl.mzebrows.pwt.client.input.KeyHolds;
import pl.mzebrows.pwt.client.input.KeyPresses;
import pl.mzebrows.pwt.client.input.NavInputReader;
import pl.mzebrows.pwt.client.input.PageControls;
import pl.mzebrows.pwt.client.play.OnlineSession;
import pl.mzebrows.pwt.client.play.ScenarioSession;
import pl.mzebrows.pwt.sim.scenario.ProvingSetup;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;
import pl.mzebrows.pwt.ui.nav.InputModality;
import pl.mzebrows.pwt.ui.nav.NavInput;
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

    /** Spaces between a choice row's label and its value. */
    private static final int CHOICE_GAP = 2;

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

    /**
     * The menu opened on a page under MAIN, with the pages above it under it, so Back climbs out as it would
     * have - back from joining, the online page; back from a level, the page it was started from (G0066).
     */
    public MenuScreen(GameContext context, String notice, UiPage start) {
        this.context = context;
        this.notice = notice;
        GraphicConfig graphic = context.graphicConfig();
        this.hints = new PageHints(context.controlsConfig(), strings);
        this.painter = new MenuPainter(
                context.hud(), graphic, context.graphicConfig().layout().metrics());
        this.reader = new NavInputReader(context.controlsConfig().ui(), context.gamepads());
        this.motion = new PageMotion(
                graphic.ui().motion().pageSeconds(),
                graphic.ui().motion().pageSlide(),
                graphic.ui().motionScale());
        this.rows = new ProvingRows(context.simConfig());
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
                hints.selectAndBack(modality),
                motion.offsetX(),
                motion.alpha());
        if (top == UiPage.PROVING_GROUND) {
            drawPreview(delta, layout, area);
        }
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
        if (isScenarioList(top)) {
            for (int i = 0; i < scenarios.size() && i < MAX_NUMBER_KEYS; i++) {
                if (keys.justPressed(Input.Keys.NUM_1 + i) && page.jump(i)) {
                    return carryOut(flow.apply(intentFor(top, i)));
                }
            }
        }
        if (isProving(top) && (nav.left || nav.right)) {
            step(top, page.index(), nav.right ? 1 : -1);
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
                return !host(effect.index());
            }
            case CHOOSE -> step(flow.top(), effect.index(), 1);
            case PROVE -> {
                prove();
                return false;
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
        } else if (top == UiPage.PROVING_UPGRADES) {
            head.add(strings.get("ui.provingUpgrades.title"));
            swarmHead.clear();
            swarmHead.add(strings.get("ui.provingUpgrades.swarm"));
            relabel(top);
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
            if (top == UiPage.PROVING_GROUND) {
                relabel(top);
            } else {
                for (MenuEntry entry : content.entries()) {
                    labels.add(strings.get(entry.labelKey()));
                }
            }
            defaultSelection = content.defaultSelection();
        }
        int remembered = top == UiPage.EXIT_CONFIRM
                ? defaultSelection
                : context.selections().get(top, defaultSelection, labels.size());
        page.reset(labels.size(), remembered);
        if (top == UiPage.PROVING_GROUND) {
            lockRows();
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
        context.queueScreen(
                new ScenarioScreen(context, new ScenarioSession(scenario, context.simConfig()), UiPage.CAMPAIGN));
    }

    /**
     * Hosts the chosen scenario online and goes straight into it: the host plays from the first frame and
     * friends drop in (G0038). If it cannot, the player stays here and is told why.
     */
    private boolean host(int index) {
        Scenario scenario = scenarios.get(index);
        context.selections().set(UiPage.CAMPAIGN, index);
        context.selections().set(UiPage.ONLINE_HOST, index);
        try {
            context.queueScreen(new ScenarioScreen(
                    context,
                    OnlineSession.host(
                            scenario,
                            context.simConfig(),
                            context.worldConfigText(),
                            context.netConfig(),
                            context.launchOptions(),
                            LocalMachineId.load()),
                    UiPage.ONLINE_HOST));
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
        closePreview();
    }
}
