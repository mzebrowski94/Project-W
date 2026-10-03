package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeView;
import pl.mzebrows.pwt.ui.hud.CrateBar;
import pl.mzebrows.pwt.ui.hud.DraftBar;
import pl.mzebrows.pwt.ui.hud.DraftLayout;
import pl.mzebrows.pwt.ui.hud.Goal;
import pl.mzebrows.pwt.ui.hud.GoalLine;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.hud.Terminal;
import pl.mzebrows.pwt.ui.hud.TextFit;
import pl.mzebrows.pwt.ui.hud.TopBarLayout;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;

/**
 * The top bar ({@code UI:hud/top/...}): the mission, dim - read once; the current goal, bright - the one
 * line always current; the terminal's lines under them, the newest at the bottom, bright and typed out,
 * the older ones dim above it; and on the right the aside - what the side holds, with rank pips - or the
 * dev overlay's panel when that is stepped on.
 *
 * <p>While a draft is open ({@code UI:hud/top/draft}, G0049) the whole bar is the draft's: the chooser's
 * portrait and label on the left, his cards across the rest, the highlighted one framed in his colour.
 * While a crate is open ({@code UI:hud/top/crate}, G0056) it is the crate's, laid out the same way with up
 * to four cards - what each is and does, and one he would be turned down for dimmed and saying why.
 *
 * <p>Content sits inside the view's span and hangs from the bar's bottom edge, the one touching the
 * view, so a taller bar grows empty space outwards. Text is rebuilt only when what it says changes.
 */
final class TopBarPainter {

    /** The mission and the goal. */
    private static final int LINES_ABOVE_TERMINAL = 2;

    /** The base a crate's key is written in: every pickup, taken or refused. */
    private static final long CARD_DIGITS = 2L * PickupKind.values().length;

    private final Hud hud;
    private final UiStrings strings;
    private final GraphicConfig graphic;
    private final HudMetrics metrics;
    private final Palette palette;
    private final CardText cardText;
    private final TopBarLayout bar;
    private final DraftLayout draftLayout;

    private String mission = "";
    private String goal = "";
    private String builtMission = "";
    private String builtPlanet = "";
    private int builtRevision = -1;

    /** The terminal's lines, fitted to the bar, newest first; and the part of the newest typed so far. */
    private final String[] said;

    private int builtTerminal = -1;
    private String typed = "";
    private int typedLength = -1;
    private ScreenLayout builtFor = ScreenLayout.NONE;

    /** The aside's rows: whose, and the upgrade's name. Rebuilt when what is held changes. */
    private final String[] aside;

    /**
     * The army (G0055), centred on the top bar's first line: the field against its most and the reserve.
     * Rebuilt only when a number on it changes; the mission is fitted to end before the widest it can be.
     */
    private String army = "";

    private int builtOnField = -1;
    private int builtMost = -1;
    private int builtReserve = -1;
    private final int armyColumns;

    private int asideRows;
    private long builtAside = -1L;
    private ScreenLayout asideFor = ScreenLayout.NONE;

    /** The draft on show, as text: each card's title and effect lines, and the hints. */
    private final String[][] cards = new String[PlayerReadout.CARDS][2 + CardText.EFFECT_LINES];

    private String chooser = "";
    private String count = "";
    private long builtDraft = -1L;
    private int builtSerial = -1;
    private int builtHighlight = -1;
    private int builtSeat = -1;
    private ScreenLayout draftFor = ScreenLayout.NONE;

    /** The crate on show, as text: each card's name, what it does, and the hint. */
    private final String[][] crateCards = new String[PlayerReadout.CRATE_CARDS][3];

    private String opener = "";
    private int builtCrateSeat = -1;
    private int builtCrateHighlight = -1;
    private long builtCrateKey = -1L;
    private ScreenLayout crateFor = ScreenLayout.NONE;

    TopBarPainter(Hud hud, UiStrings strings, GraphicConfig graphic, HudMetrics metrics, Palette palette) {
        this.hud = hud;
        this.strings = strings;
        this.graphic = graphic;
        this.metrics = metrics;
        this.palette = palette;
        this.cardText = new CardText(strings);
        this.bar = new TopBarLayout(metrics.padding(), graphic.hud().overlay().widthShare());
        this.draftLayout = new DraftLayout(metrics.padding(), metrics.portraitSize(), UiFont.ADVANCE);
        this.said = new String[Math.max(1, metrics.topLines() - LINES_ABOVE_TERMINAL)];
        this.aside = new String[metrics.topLines()];
        this.armyColumns = strings.format("ui.hud.army", 999, 999, 999).length();
        Arrays.fill(said, "");
        Arrays.fill(aside, "");
        for (String[] card : cards) {
            Arrays.fill(card, "");
        }
        for (String[] card : crateCards) {
            Arrays.fill(card, "");
        }
    }

    /**
     * Draws the bar. {@code overlay} is the dev overlay's lines, most useful first, or empty when it is
     * off; {@code mode} says whether they go in the panel or over the view's corner. While {@code draft}
     * is open the bar is the draft's; {@code hints} names the keys its seat works it with.
     */
    void render(
            ScreenLayout layout,
            Readout readout,
            GoalLine goalLine,
            Terminal terminal,
            List<String> overlay,
            OverlayMode mode,
            DraftBar draft,
            UpgradeView upgrades,
            DraftHints hints,
            CrateBar crate,
            CrateHints crateHints,
            SimConfig config) {
        Rect top = layout.topBar();
        int span = layout.contentRight() - layout.contentLeft();
        boolean resized = !layout.equals(builtFor);
        rebuild(layout, readout, goalLine, span);
        rebuildTerminal(terminal, resized, TextFit.columns(bar.textWidth(span), UiFont.ADVANCE) - 1);
        int padding = metrics.padding();
        int line = metrics.fontSize() + metrics.leading();
        int left = layout.contentLeft();
        int blockTop = top.y() + Math.min(top.height(), metrics.topMin()) - padding;

        hud.beginCanvas();
        if (!top.isEmpty() && crate.open()) {
            crate(layout, readout, crate, crateHints, config, left, span, top.y(), blockTop, line);
        } else if (!top.isEmpty() && draft.open()) {
            draft(layout, readout, draft, upgrades, hints, left, span, top.y(), blockTop, line);
        } else if (!top.isEmpty()) {
            hud.text(mission, left + padding, blockTop, graphic.hud().color().textDim());
            army(readout);
            hud.text(
                    army,
                    armyLeft(left, span) + (armyColumns - army.length()) * UiFont.ADVANCE / 2,
                    blockTop,
                    graphic.hud().color().text());
            hud.text(
                    goal,
                    left + padding,
                    blockTop - line,
                    goalLine.bright()
                            ? graphic.hud().color().text()
                            : graphic.hud().color().textDim());
            terminal(terminal, left + padding, blockTop - LINES_ABOVE_TERMINAL * line, line);
            int asideX = left + span - bar.overlayWidth(span);
            if (mode == OverlayMode.PANEL) {
                panel(overlay, asideX, top.y(), bar.overlayWidth(span), blockTop, line);
            } else {
                aside(layout, readout, asideX, bar.overlayWidth(span), blockTop, line);
            }
        }
        if (mode == OverlayMode.EXPANDED) {
            expanded(overlay, layout.view(), line);
        }
        hud.endCanvas();
    }

    /**
     * The draft ({@code UI:hud/top/draft}): the chooser's portrait and {@code P{n}}, and {@code 1/2} under
     * it while he holds more than one; then his cards, each its name with the rank it will have, up to two
     * effect lines and a hint, the highlighted one framed in his colour.
     */
    private void draft(
            ScreenLayout layout,
            Readout readout,
            DraftBar draft,
            UpgradeView upgrades,
            DraftHints hints,
            int left,
            int span,
            int bottom,
            int blockTop,
            int line) {
        PlayerReadout player = readout.player(draft.slot());
        int cardsShown = player.draftSerial == draft.serial() ? player.cardCount : 0;
        rebuildDraft(layout, readout, player, draft, upgrades, hints, span, cardsShown);
        Color own = palette.colorFor(Look.COMMANDER, draft.slot());
        int padding = metrics.padding();
        int portrait = metrics.portraitSize();
        hud.fill(left + padding, blockTop - portrait, portrait, portrait, own);
        int labelX = left + padding + portrait + padding;
        hud.text(chooser, labelX, blockTop, own);
        hud.text(count, labelX, blockTop - line, graphic.hud().color().textDim());

        int width = draftLayout.cardWidth(span, cardsShown);
        int height = metrics.topMin() - 2;
        for (int i = 0; i < cardsShown; i++) {
            int x = left + draftLayout.cardX(span, cardsShown, i);
            boolean lit = i == draft.highlighted();
            hud.frame(
                    x,
                    bottom + 1,
                    width - padding,
                    height,
                    lit ? own : graphic.hud().color().overlayFrame());
            String[] card = cards[i];
            for (int row = 0; row < card.length; row++) {
                Color color = !lit
                        ? graphic.hud().color().textDim()
                        : row == card.length - 1 ? own : graphic.hud().color().text();
                hud.text(card[row], x + padding, blockTop - row * line, color);
            }
        }
    }

    /**
     * The crate ({@code UI:hud/top/crate}, G0056): the opener's portrait and {@code P{n}}, then the crate's
     * cards, each what it is, what it does and a hint, the highlighted one framed in his colour. A card he
     * would be turned down for is dim, and says why.
     */
    private void crate(
            ScreenLayout layout,
            Readout readout,
            CrateBar crate,
            CrateHints hints,
            SimConfig config,
            int left,
            int span,
            int bottom,
            int blockTop,
            int line) {
        PlayerReadout player = readout.player(crate.slot());
        int shown = player.crateCount;
        rebuildCrate(layout, player, crate, hints, config, span, shown);
        Color own = palette.colorFor(Look.COMMANDER, crate.slot());
        int padding = metrics.padding();
        int portrait = metrics.portraitSize();
        hud.fill(left + padding, blockTop - portrait, portrait, portrait, own);
        hud.text(opener, left + padding + portrait + padding, blockTop, own);

        int width = draftLayout.cardWidth(span, shown);
        int height = metrics.topMin() - 2;
        for (int i = 0; i < shown; i++) {
            int x = left + draftLayout.cardX(span, shown, i);
            boolean lit = i == crate.highlighted();
            boolean refused = player.crateRefused[i];
            hud.frame(
                    x,
                    bottom + 1,
                    width - padding,
                    height,
                    lit ? own : graphic.hud().color().overlayFrame());
            String[] card = crateCards[i];
            for (int row = 0; row < card.length; row++) {
                Color color = !lit || refused
                        ? graphic.hud().color().textDim()
                        : row == card.length - 1 ? own : graphic.hud().color().text();
                hud.text(card[row], x + padding, blockTop - row * line, color);
            }
        }
    }

    private void rebuildCrate(
            ScreenLayout layout,
            PlayerReadout player,
            CrateBar crate,
            CrateHints hints,
            SimConfig config,
            int span,
            int shown) {
        long key = crateKey(player, shown);
        if (layout.equals(crateFor)
                && key == builtCrateKey
                && crate.highlighted() == builtCrateHighlight
                && crate.seat() == builtCrateSeat) {
            return;
        }
        crateFor = layout;
        builtCrateKey = key;
        builtCrateHighlight = crate.highlighted();
        builtCrateSeat = crate.seat();
        opener = strings.format("ui.hud.player", crate.slot() + 1);
        int columns = draftLayout.cardColumns(span, Math.max(1, shown));
        for (int i = 0; i < PlayerReadout.CRATE_CARDS; i++) {
            String[] card = crateCards[i];
            Arrays.fill(card, "");
            if (i >= shown) {
                continue;
            }
            Grant grant = config.pickup(player.crate[i]).playerGrant();
            card[0] = TextFit.fit(crateName(grant, player.crate[i]), columns);
            card[1] =
                    TextFit.fit(player.crateRefused[i] ? strings.get("ui.crate.refused") : crateEffect(grant), columns);
            String hint = i == crate.highlighted()
                    ? strings.format("ui.crate.take", hints.take(), hints.close())
                    : hints.card(i).isEmpty() ? "" : strings.format("ui.draft.choose", hints.card(i));
            card[2] = TextFit.fit(hint.toUpperCase(Locale.ROOT), columns);
        }
    }

    /** What a crate's card is called: the gun it arms you with, or the pickup's own name. */
    private String crateName(Grant grant, PickupKind kind) {
        return grant instanceof Grant.Arm(WeaponKind gun)
                ? strings.get("ui.weapon." + gun.name())
                : strings.get("ui.crate." + kind.name());
    }

    /** What taking it does, in a line. */
    private String crateEffect(Grant grant) {
        return switch (grant) {
            case Grant.Arm _ -> strings.get("ui.crate.effect.arm");
            case Grant.Mend _ -> strings.get("ui.crate.effect.mend");
            case Grant.None _, Grant.Detonate _, Grant.Feed _, Grant.Mutate _, Grant.Reinforce _ -> "";
        };
    }

    /**
     * What the crate's cards are, packed, so they are rebuilt only when that changes: one digit per card,
     * in a base with room for every pickup twice over (taken or refused), however many pickups there are.
     */
    @SuppressWarnings("EnumOrdinal") // a cache key held for a frame, never stored or sent
    static long crateKey(PlayerReadout player, int shown) {
        long key = shown;
        for (int i = 0; i < shown; i++) {
            key = key * CARD_DIGITS + player.crate[i].ordinal() * 2L + (player.crateRefused[i] ? 1 : 0);
        }
        return key;
    }

    private void rebuildDraft(
            ScreenLayout layout,
            Readout readout,
            PlayerReadout player,
            DraftBar draft,
            UpgradeView upgrades,
            DraftHints hints,
            int span,
            int cardsShown) {
        if (layout.equals(draftFor)
                && readout.upgradeRevision == builtDraft
                && draft.serial() == builtSerial
                && draft.highlighted() == builtHighlight
                && draft.seat() == builtSeat) {
            return;
        }
        draftFor = layout;
        builtDraft = readout.upgradeRevision;
        builtSerial = draft.serial();
        builtHighlight = draft.highlighted();
        builtSeat = draft.seat();
        chooser = strings.format("ui.hud.player", draft.slot() + 1);
        count = player.drafts > 1 ? strings.format("ui.draft.count", 1, player.drafts) : "";
        int columns = draftLayout.cardColumns(span, Math.max(1, cardsShown));
        for (int i = 0; i < PlayerReadout.CARDS; i++) {
            String[] card = cards[i];
            Arrays.fill(card, "");
            if (i >= cardsShown) {
                continue;
            }
            UpgradeKind kind = player.cards[i];
            card[0] = TextFit.fit(cardText.title(kind, player.cardRanks[i]), columns);
            for (int e = 0; e < CardText.EFFECT_LINES; e++) {
                card[1 + e] = TextFit.fit(cardText.effect(upgrades, kind, e), columns);
            }
            String hint = i == draft.highlighted()
                    ? strings.format("ui.draft.take", hints.take(), hints.close())
                    : hints.card(i).isEmpty() ? "" : strings.format("ui.draft.choose", hints.card(i));
            card[card.length - 1] = TextFit.fit(hint.toUpperCase(Locale.ROOT), columns);
        }
    }

    /**
     * The aside ({@code UI:hud/top/aside}, a first answer to plan Q30): what each side holds, one upgrade a
     * row with its rank as pips - a personal one named for its holder, in his colour, and an adaptation
     * for the swarm, in the swarm's. More than fits ends
     * in a count of the rest.
     */
    private void aside(ScreenLayout layout, Readout readout, int x, int width, int blockTop, int line) {
        int padding = metrics.padding();
        int pipsWidth = RankPips.width(metrics, maxRanks(readout));
        int columns = TextFit.columns(width - 3 * padding - pipsWidth, UiFont.ADVANCE);
        if (readout.upgradeRevision != builtAside || !layout.equals(asideFor)) {
            buildAside(readout, columns);
            builtAside = readout.upgradeRevision;
            asideFor = layout;
        }
        int pipsX = x + width - padding - pipsWidth;
        for (int row = 0; row < asideRows; row++) {
            int top = blockTop - row * line;
            boolean more = row == aside.length - 1 && readout.heldCount > aside.length;
            int slot = more ? -1 : readout.heldSlot[row];
            boolean swarm = !more && readout.heldAlien[row];
            Color color = swarm
                    ? graphic.hud().color().swarm()
                    : slot < 0 ? graphic.hud().color().textDim() : palette.colorFor(Look.COMMANDER, slot);
            hud.text(aside[row], x + padding, top, color);
            if (more) {
                continue;
            }
            RankPips.draw(
                    hud,
                    metrics,
                    pipsX,
                    top,
                    readout.heldRanks[row],
                    readout.heldMaxRanks[row],
                    swarm || slot >= 0 ? color : graphic.hud().color().text(),
                    graphic.hud().color().textDim());
        }
    }

    /** {@code ARMY 12/16 RES 20}, formatted only when a number on it changes. */
    private void army(Readout readout) {
        if (readout.squadOnField == builtOnField && readout.squadMost == builtMost && readout.reserve == builtReserve) {
            return;
        }
        builtOnField = readout.squadOnField;
        builtMost = readout.squadMost;
        builtReserve = readout.reserve;
        army = strings.format("ui.hud.army", readout.squadOnField, readout.squadMost, readout.reserve);
    }

    /** Where the army's slot starts: its widest, centred on the bar. */
    private int armyLeft(int left, int span) {
        return left + (span - armyColumns * UiFont.ADVANCE) / 2;
    }

    private void buildAside(Readout readout, int columns) {
        asideRows = Math.min(readout.heldCount, aside.length);
        for (int row = 0; row < asideRows; row++) {
            if (row == aside.length - 1 && readout.heldCount > aside.length) {
                aside[row] = TextFit.fit(strings.format("ui.hud.aside.more", readout.heldCount - row), columns);
                continue;
            }
            aside[row] = TextFit.fit(
                    HeldUpgradesPainter.whose(strings, readout, row, cardText.name(readout.heldKind[row])), columns);
        }
    }

    private static int maxRanks(Readout readout) {
        int most = 0;
        for (int i = 0; i < readout.heldCount; i++) {
            most = Math.max(most, readout.heldMaxRanks[i]);
        }
        return most;
    }

    /**
     * The terminal's lines, newest on the bottom row so a new line pushes the others up; the newest as far
     * as it is typed, with the cursor after it while it blinks.
     */
    private void terminal(Terminal terminal, int x, int firstTop, int line) {
        int bottomRow = said.length - 1;
        for (int age = 1; age < terminal.showing(); age++) {
            hud.text(
                    said[age],
                    x,
                    firstTop - (bottomRow - age) * line,
                    graphic.hud().color().textDim());
        }
        if (terminal.showing() == 0) {
            return;
        }
        int top = firstTop - bottomRow * line;
        hud.text(typed, x, top, graphic.hud().color().text());
        if (terminal.cursor()) {
            hud.text(
                    "_",
                    x + typed.length() * UiFont.ADVANCE,
                    top,
                    graphic.hud().color().text());
        }
    }

    private void rebuildTerminal(Terminal terminal, boolean resized, int columns) {
        if (resized || terminal.revision() != builtTerminal) {
            for (int age = 0; age < said.length; age++) {
                said[age] = age < terminal.showing() ? TextFit.fit(terminal.line(age), columns) : "";
            }
            builtTerminal = terminal.revision();
            typedLength = -1;
        }
        int length = Math.min(terminal.revealed(), said[0].length());
        if (length != typedLength) {
            typed = said[0].substring(0, length);
            typedLength = length;
        }
    }

    private void panel(List<String> lines, int x, int bottom, int width, int blockTop, int line) {
        int padding = metrics.padding();
        int height = metrics.topMin() - 2;
        hud.frame(x, bottom + 1, width - 1, height, graphic.hud().color().overlayFrame());
        int columns = TextFit.columns(width - 3 * padding, UiFont.ADVANCE);
        int rows = Math.min(lines.size(), metrics.topLines());
        for (int i = 0; i < rows; i++) {
            hud.text(
                    TextFit.fit(lines.get(i), columns),
                    x + padding,
                    blockTop - i * line,
                    graphic.hud().color().textDim());
        }
    }

    /** Every line, over the view's top-right corner, on the bar's black so it reads over anything. */
    private void expanded(List<String> lines, Rect view, int line) {
        int padding = metrics.padding();
        int longest = 0;
        for (String text : lines) {
            longest = Math.max(longest, text.length());
        }
        int width = Math.min(view.width(), longest * UiFont.ADVANCE + 2 * padding);
        int height = Math.min(view.height(), lines.size() * line + 2 * padding);
        int x = view.right() - width;
        int y = view.top() - height;
        hud.fill(x, y, width, height, graphic.hud().color().bar());
        hud.frame(x, y, width, height, graphic.hud().color().overlayFrame());
        int columns = TextFit.columns(width - 2 * padding, UiFont.ADVANCE);
        for (int i = 0; i < lines.size() && (i + 1) * line <= height - padding; i++) {
            hud.text(
                    TextFit.fit(lines.get(i), columns),
                    x + padding,
                    view.top() - padding - i * line,
                    graphic.hud().color().text());
        }
    }

    private void rebuild(ScreenLayout layout, Readout readout, GoalLine goalLine, int span) {
        boolean resized = !layout.equals(builtFor);
        int columns = TextFit.columns(bar.textWidth(span), UiFont.ADVANCE);
        if (resized || !readout.mission.equals(builtMission) || !readout.planet.equals(builtPlanet)) {
            // The mission shares its line with the army, centred on the bar: it ends a column short of it.
            int missionColumns =
                    Math.max(0, Math.min(columns, (armyLeft(0, span) - metrics.padding()) / UiFont.ADVANCE - 1));
            String named = strings.format("ui.hud.mission", readout.mission);
            mission = readout.planet.isEmpty()
                    ? TextFit.fit(strings.get("ui.hud.mission"), readout.mission, missionColumns)
                    : TextFit.fit(strings.format("ui.hud.planet", readout.planet) + "  " + named, missionColumns);
            builtMission = readout.mission;
            builtPlanet = readout.planet;
        }
        if (resized || goalLine.revision() != builtRevision) {
            goal = goalText(goalLine, columns);
            builtRevision = goalLine.revision();
        }
        builtFor = layout;
    }

    private String goalText(GoalLine goalLine, int columns) {
        Goal shown = goalLine.shown();
        if (shown == Goal.NONE) {
            return "";
        }
        String what =
                switch (shown) {
                    case COMPLETE -> strings.get("ui.hud.goal.complete");
                    case FAILED -> strings.get("ui.hud.goal.failed");
                    default -> strings.format("ui.goal." + shown.name(), goalLine.first(), goalLine.second());
                };
        return TextFit.fit(strings.get("ui.hud.goal"), what, columns);
    }
}
