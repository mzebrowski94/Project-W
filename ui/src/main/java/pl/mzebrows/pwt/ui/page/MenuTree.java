package pl.mzebrows.pwt.ui.page;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every fixed page's entries, as {@code docs/ui/menu_layout_agent.md} section 4 lists them. The scenario
 * lists - the campaign and hosting - are built from the registry instead, and the proving ground's
 * upgrades from the catalogue ({@link ProvingRows#upgrades()}); neither is here.
 */
public final class MenuTree {

    private static final Map<UiPage, MenuPage> PAGES = new EnumMap<>(UiPage.class);

    static {
        PAGES.put(
                UiPage.MAIN,
                new MenuPage(
                        "ui.main.title",
                        "",
                        List.of(
                                entry("campaign", "ui.main.campaign", UiIntent.open(UiPage.CAMPAIGN)),
                                entry("proving-ground", "ui.main.provingGround", UiIntent.open(UiPage.PROVING_GROUND)),
                                entry("online", "ui.main.online", UiIntent.open(UiPage.ONLINE)),
                                entry("options", "ui.main.options", UiIntent.open(UiPage.OPTIONS)),
                                entry("exit", "ui.main.exit", UiIntent.open(UiPage.EXIT_CONFIRM))),
                        0));
        PAGES.put(
                UiPage.PROVING_GROUND,
                new MenuPage(
                        "ui.provingGround.title",
                        "",
                        List.of(
                                entry("strain", "ui.provingGround.strain", UiIntent.choose(0)),
                                entry("count", "ui.provingGround.count", UiIntent.choose(1)),
                                entry("gun", "ui.provingGround.gun", UiIntent.choose(2)),
                                entry("squad", "ui.provingGround.squad", UiIntent.choose(3)),
                                entry("others", "ui.provingGround.others", UiIntent.choose(4)),
                                entry("upgrades", "ui.provingGround.upgrades", UiIntent.open(UiPage.PROVING_UPGRADES)),
                                entry("start", "ui.provingGround.start", UiIntent.PROVE),
                                back()),
                        6));
        PAGES.put(
                UiPage.ONLINE,
                new MenuPage(
                        "ui.online.title",
                        "",
                        List.of(
                                entry("join", "ui.online.join", UiIntent.open(UiPage.ONLINE_JOIN)),
                                entry("host", "ui.online.host", UiIntent.open(UiPage.ONLINE_HOST)),
                                back()),
                        0));
        PAGES.put(UiPage.OPTIONS, new MenuPage("ui.options.title", "ui.options.body", List.of(back()), 0));
        PAGES.put(
                UiPage.EXIT_CONFIRM,
                new MenuPage(
                        "",
                        "ui.exit.body",
                        List.of(
                                entry("stay", "ui.common.no", UiIntent.BACK),
                                entry("quit", "ui.common.yes", UiIntent.QUIT_APP)),
                        0));
        PAGES.put(
                UiPage.PAUSE,
                new MenuPage(
                        "ui.pause.title",
                        "",
                        List.of(
                                entry("resume", "ui.pause.resume", UiIntent.RESUME),
                                entry("restart", "ui.pause.restart", UiIntent.RESTART),
                                entry("leave", "ui.pause.leave", UiIntent.open(UiPage.LEAVE_CONFIRM))),
                        0));
        PAGES.put(
                UiPage.LEAVE_CONFIRM,
                new MenuPage(
                        "",
                        "ui.leave.body",
                        List.of(
                                entry("stay", "ui.common.no", UiIntent.BACK),
                                entry("leave", "ui.common.yes", UiIntent.LEAVE)),
                        0));
        PAGES.put(
                UiPage.RESULT,
                // The title is the outcome's: ui.result.won here, ui.result.lost for a loss - the screen's choice.
                new MenuPage(
                        "ui.result.won",
                        "",
                        List.of(
                                entry("retry", "ui.result.retry", UiIntent.RESTART),
                                entry("menu", "ui.result.menu", UiIntent.LEAVE)),
                        0));
    }

    private MenuTree() {}

    /** The page's fixed content. Throws for a page that has none - a scenario list or a state. */
    public static MenuPage page(UiPage page) {
        MenuPage found = PAGES.get(page);
        if (found == null) {
            throw new IllegalArgumentException(page + " has no fixed entries");
        }
        return found;
    }

    public static boolean hasFixedEntries(UiPage page) {
        return PAGES.containsKey(page);
    }

    /** Whether an entry can be confirmed right now: RESTART only offline or on the host. */
    public static boolean enabled(MenuEntry entry, UiFlow flow) {
        return entry.intent().kind() != UiIntent.Kind.RESTART || flow.canRestart();
    }

    private static MenuEntry entry(String id, String labelKey, UiIntent intent) {
        return new MenuEntry(id, labelKey, intent);
    }

    private static MenuEntry back() {
        return entry("back", "ui.common.back", UiIntent.BACK);
    }
}
