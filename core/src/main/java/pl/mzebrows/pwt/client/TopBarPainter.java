package pl.mzebrows.pwt.client;

import java.util.Arrays;
import java.util.List;
import pl.mzebrows.pwt.ui.hud.Goal;
import pl.mzebrows.pwt.ui.hud.GoalLine;
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
 * the older ones dim above it; and the dev overlay's panel on the right.
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
    private final TopBarLayout bar;

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

    TopBarPainter(Hud hud, UiStrings strings, HudStyle style, HudMetrics metrics) {
        this.hud = hud;
        this.strings = strings;
        this.style = style;
        this.metrics = metrics;
        this.bar = new TopBarLayout(metrics.padding(), style.overlayWidthShare());
        this.said = new String[Math.max(1, metrics.topLines() - LINES_ABOVE_TERMINAL)];
        Arrays.fill(said, "");
    }

    /**
     * Draws the bar. {@code overlay} is the dev overlay's lines, most useful first, or empty when it is
     * off; {@code mode} says whether they go in the panel or over the view's corner.
     */
    void render(
            ScreenLayout layout,
            Readout readout,
            GoalLine goalLine,
            Terminal terminal,
            List<String> overlay,
            OverlayMode mode) {
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
        if (!top.isEmpty()) {
            hud.text(mission, left + padding, blockTop, style.textDim());
            hud.text(goal, left + padding, blockTop - line, goalLine.bright() ? style.text() : style.textDim());
            terminal(terminal, left + padding, blockTop - LINES_ABOVE_TERMINAL * line, line);
            if (mode == OverlayMode.PANEL) {
                panel(overlay, left + span - bar.overlayWidth(span), top.y(), bar.overlayWidth(span), blockTop, line);
            }
        }
        if (mode == OverlayMode.EXPANDED) {
            expanded(overlay, layout.view(), line);
        }
        hud.endCanvas();
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
