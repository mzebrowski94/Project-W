package pl.mzebrows.pwt.ui.page;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import pl.mzebrows.pwt.sim.def.TierKind;

/**
 * Every fixed page's entries, as {@code docs/ui/menu_layout_agent.md} section 4 lists them. The scenario
 * lists - the campaign and hosting - are built from the registry instead, and the proving ground's
 * upgrades from the catalogue ({@link ProvingRows#upgrades()}); neither is here. The campaign's page here
 * is what it shows while its scenarios are not listed: that it is not open yet, and BACK.
 */
public final class MenuTree {

    /** The missions page's RANDOM, after one daily per tier in {@link TierKind} order. */
    public static final int MISSION_RANDOM = TierKind.values().length;

    /**
     * The missions page's length row, after RANDOM: a choice row `< 6 BIOMES >` whose left and right step how many
     * biomes a mission has (G0102). It writes into the code line like the entries above it, and RANDOM draws at it.
     */
    public static final int MISSION_LENGTH = MISSION_RANDOM + 1;

    /** The missions page's ENTER CODE, after the length row: what every entry above writes into. */
    public static final int MISSION_CODE = MISSION_LENGTH + 1;

    /** The missions page's START MISSION, after ENTER CODE: the one entry that starts anything. */
    public static final int MISSION_START = MISSION_CODE + 1;

    /** The controls page's PROMPTS switch, its first entry. */
    public static final int CONTROLS_PROMPTS = 0;

    private static final Map<UiPage, MenuPage> PAGES = new EnumMap<>(UiPage.class);

    /** The result of a mission: COPY CODE between RETRY and MENU (G0066). */
    private static final MenuPage MISSION_RESULT;

    static {
        PAGES.put(
                UiPage.MAIN,
                new MenuPage(
                        "ui.main.title",
                        "",
                        List.of(
                                entry("missions", "ui.main.missions", UiIntent.open(UiPage.MISSIONS)),
                                entry("campaign", "ui.main.campaign", UiIntent.open(UiPage.CAMPAIGN)),
                                entry("proving-ground", "ui.main.provingGround", UiIntent.open(UiPage.PROVING_GROUND)),
                                entry("online", "ui.main.online", UiIntent.open(UiPage.ONLINE)),
                                entry("controls", "ui.main.controls", UiIntent.open(UiPage.CONTROLS)),
                                entry("options", "ui.main.options", UiIntent.open(UiPage.OPTIONS)),
                                entry("exit", "ui.main.exit", UiIntent.open(UiPage.EXIT_CONFIRM))),
                        0));
        MenuPage missions = missions();
        PAGES.put(UiPage.MISSIONS, missions);
        PAGES.put(UiPage.HOST_MISSIONS, missions);
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
        PAGES.put(UiPage.CAMPAIGN, new MenuPage("ui.campaign.title", "ui.campaign.closed", List.of(back()), 0));
        // Its table of bindings is the screen's to write, from the bindings themselves, and so is the
        // switch's ON or OFF; the switch is the one thing on the page that changes anything.
        MenuPage controls = new MenuPage(
                "ui.controls.title",
                "",
                List.of(entry("prompts", "ui.controls.prompts", UiIntent.choose(CONTROLS_PROMPTS)), back()),
                0);
        PAGES.put(UiPage.CONTROLS, controls);
        PAGES.put(UiPage.PAUSE_CONTROLS, controls);
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
                                entry("controls", "ui.main.controls", UiIntent.open(UiPage.PAUSE_CONTROLS)),
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
        MISSION_RESULT = new MenuPage(
                "ui.result.won",
                "",
                List.of(
                        entry("retry", "ui.result.retry", UiIntent.RESTART),
                        entry("copy", "ui.result.copy", UiIntent.COPY_CODE),
                        entry("menu", "ui.result.menu", UiIntent.LEAVE)),
                0);
    }

    /**
     * The day's three missions, one a tier, then RANDOM, the length row and ENTER CODE - each writes into the code line - and
     * START MISSION, which starts what it holds; MEDIUM selected. Its title takes the date, which the screen
     * fills in.
     */
    private static MenuPage missions() {
        List<MenuEntry> entries = new ArrayList<>();
        int medium = 0;
        TierKind[] tiers = TierKind.values();
        for (int i = 0; i < tiers.length; i++) {
            TierKind tier = tiers[i];
            entries.add(entry(tier.name().toLowerCase(Locale.ROOT), "ui.tier." + tier, UiIntent.mission(i)));
            medium = tier == TierKind.MEDIUM ? i : medium;
        }
        entries.add(entry("random", "ui.missions.random", UiIntent.mission(MISSION_RANDOM)));
        entries.add(entry("length", "ui.missions.length", UiIntent.mission(MISSION_LENGTH)));
        entries.add(entry("code", "ui.missions.code", UiIntent.mission(MISSION_CODE)));
        entries.add(entry("start", "ui.missions.start", UiIntent.mission(MISSION_START)));
        entries.add(back());
        return new MenuPage("ui.missions.title", "", entries, medium);
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

    /** The result's entries: a mission's add COPY CODE. */
    public static MenuPage result(boolean mission) {
        return mission ? MISSION_RESULT : page(UiPage.RESULT);
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
