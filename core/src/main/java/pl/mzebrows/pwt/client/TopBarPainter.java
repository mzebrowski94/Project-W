package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.upgrade.UpgradeView;
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
 * <p>While a draft is open ({@code UI:hud/top/draft}, D18) the whole bar is the draft's: the chooser's
 * portrait and label on the left, his cards across the rest, the highlighted one framed in his colour.
 *
 * <p>Content sits inside the view's span and hangs from the bar's bottom edge, the one touching the
 * view, so a taller bar grows empty space outwards. Text is rebuilt only when what it says changes.
 */
final class TopBarPainter {

    /** The mission and the goal. */
    private static final int LINES_ABOVE_TERMINAL = 2;

    private final Hud hud;
    private final UiStrings strings;
    private final HudStyle style;
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

    TopBarPainter(Hud hud, UiStrings strings, HudStyle style, HudMetrics metrics, Palette palette) {
        this.hud = hud;
        this.strings = strings;
        this.style = style;
        this.metrics = metrics;
        this.palette = palette;
        this.cardText = new CardText(strings);
        this.bar = new TopBarLayout(metrics.padding(), style.overlayWidthShare());
        this.draftLayout = new DraftLayout(metrics.padding(), metrics.portraitSize(), UiFont.ADVANCE);
        this.said = new String[Math.max(1, metrics.topLines() - LINES_ABOVE_TERMINAL)];
        this.aside = new String[metrics.topLines()];
        Arrays.fill(said, "");
        Arrays.fill(aside, "");
        for (String[] card : cards) {
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
            DraftHints hints) {
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
        if (!top.isEmpty() && draft.open()) {
            draft(layout, readout, draft, upgrades, hints, left, span, top.y(), blockTop, line);
        } else if (!top.isEmpty()) {
            hud.text(mission, left + padding, blockTop, style.textDim());
            hud.text(goal, left + padding, blockTop - line, goalLine.bright() ? style.text() : style.textDim());
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
        hud.text(count, labelX, blockTop - line, style.textDim());

        int width = draftLayout.cardWidth(span, cardsShown);
        int height = metrics.topMin() - 2;
        for (int i = 0; i < cardsShown; i++) {
            int x = left + draftLayout.cardX(span, cardsShown, i);
            boolean lit = i == draft.highlighted();
            hud.frame(x, bottom + 1, width - padding, height, lit ? own : style.overlayFrame());
            String[] card = cards[i];
            for (int row = 0; row < card.length; row++) {
                Color color = !lit ? style.textDim() : row == card.length - 1 ? own : style.text();
                hud.text(card[row], x + padding, blockTop - row * line, color);
            }
        }
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
     * The aside ({@code UI:hud/top/aside}, a first answer to plan Q30): what the side holds, one upgrade a
     * row with its rank as pips - a personal one named for its holder, in his colour. More than fits ends
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
            Color color = slot < 0 ? style.textDim() : palette.colorFor(Look.COMMANDER, slot);
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
                    slot < 0 ? style.text() : color,
                    style.textDim());
        }
    }

    private void buildAside(Readout readout, int columns) {
        asideRows = Math.min(readout.heldCount, aside.length);
        for (int row = 0; row < asideRows; row++) {
            if (row == aside.length - 1 && readout.heldCount > aside.length) {
                aside[row] = TextFit.fit(strings.format("ui.hud.aside.more", readout.heldCount - row), columns);
                continue;
            }
            String name = cardText.name(readout.heldKind[row]);
            int slot = readout.heldSlot[row];
            aside[row] = TextFit.fit(slot < 0 ? name : strings.format("ui.hud.player", slot + 1) + " " + name, columns);
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
            hud.text(said[age], x, firstTop - (bottomRow - age) * line, style.textDim());
        }
        if (terminal.showing() == 0) {
            return;
        }
        int top = firstTop - bottomRow * line;
        hud.text(typed, x, top, style.text());
        if (terminal.cursor()) {
            hud.text("_", x + typed.length() * UiFont.ADVANCE, top, style.text());
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
        hud.frame(x, bottom + 1, width - 1, height, style.overlayFrame());
        int columns = TextFit.columns(width - 3 * padding, UiFont.ADVANCE);
        int rows = Math.min(lines.size(), metrics.topLines());
        for (int i = 0; i < rows; i++) {
            hud.text(TextFit.fit(lines.get(i), columns), x + padding, blockTop - i * line, style.textDim());
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
        hud.fill(x, y, width, height, style.bar());
        hud.frame(x, y, width, height, style.overlayFrame());
        int columns = TextFit.columns(width - 2 * padding, UiFont.ADVANCE);
        for (int i = 0; i < lines.size() && (i + 1) * line <= height - padding; i++) {
            hud.text(TextFit.fit(lines.get(i), columns), x + padding, view.top() - padding - i * line, style.text());
        }
    }

    private void rebuild(ScreenLayout layout, Readout readout, GoalLine goalLine, int span) {
        boolean resized = !layout.equals(builtFor);
        int columns = TextFit.columns(bar.textWidth(span), UiFont.ADVANCE);
        if (resized || !readout.mission.equals(builtMission) || !readout.planet.equals(builtPlanet)) {
            String named = strings.format("ui.hud.mission", readout.mission);
            mission = readout.planet.isEmpty()
                    ? TextFit.fit(strings.get("ui.hud.mission"), readout.mission, columns)
                    : TextFit.fit(strings.format("ui.hud.planet", readout.planet) + "  " + named, columns);
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
        String what = shown == Goal.COMPLETE
                ? strings.get("ui.hud.goal.complete")
                : strings.format("ui.goal." + shown.name(), goalLine.first(), goalLine.second());
        return TextFit.fit(strings.get("ui.hud.goal"), what, columns);
    }
}
