package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import java.util.List;
import pl.mzebrows.pwt.ui.hud.TextFit;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.page.MenuLayout;

/**
 * Draws a page or a modal on the UI canvas: its panel, head, entries, foot and hint - the selected entry
 * ringed, one it cannot confirm dim - and the scrim and paused banner over the view.
 *
 * <p>Painted directly, like the bars, rather than built from scene2d widgets (plan D14): the pages are
 * short columns of text in the interface's own pixel font, which a table of widgets would only wrap, and
 * everything that decides - where a line is, which entry is under the pointer - is {@link MenuLayout},
 * tested without a display. Decides nothing itself.
 */
final class MenuPainter {

    private final Hud hud;
    private final HudStyle style;
    private final HudMetrics metrics;
    private final MenuLayout layout;
    private final Color scrim = new Color();

    MenuPainter(Hud hud, HudStyle style, HudMetrics metrics) {
        this.hud = hud;
        this.style = style;
        this.metrics = metrics;
        this.layout = menuLayout(metrics);
    }

    static MenuLayout menuLayout(HudMetrics metrics) {
        return new MenuLayout(
                metrics.padding(), metrics.fontSize() + metrics.leading(), UiFont.ADVANCE, metrics.fontSize());
    }

    /** Dims the view by {@code alpha}; nothing at zero. */
    void scrim(Rect view, float alpha) {
        if (alpha <= 0f) {
            return;
        }
        hud.beginCanvas();
        hud.fill(view.x(), view.y(), view.width(), view.height(), scrim.set(0f, 0f, 0f, alpha));
        hud.endCanvas();
    }

    /** One line at the top of the view: the game is paused, and which button opens the menu. */
    void banner(Rect view, String text) {
        int width = text.length() * UiFont.ADVANCE + 4 * metrics.padding();
        int height = metrics.fontSize() + 2 * metrics.padding() + metrics.leading();
        int x = view.x() + (view.width() - width) / 2;
        int y = view.top() - 2 * metrics.padding() - height;
        hud.beginCanvas();
        hud.fill(x, y, width, height, style.bar());
        hud.frame(x, y, width, height, style.textDim());
        hud.text(text, x + 2 * metrics.padding(), y + height - metrics.padding() - metrics.leading(), style.text());
        hud.endCanvas();
    }

    /**
     * A page's panel.
     *
     * @param head title and body lines, already in words; the first is drawn bright
     * @param entries the entries' words
     * @param enabled which entries can be confirmed
     * @param selected the selected entry, or -1 for none (pointer mode, nothing hovered)
     * @param foot lines under the entries, dim
     * @param hint the last line: which buttons select and go back
     */
    Rect page(
            Rect view,
            MenuLayout.Anchor anchor,
            List<String> head,
            List<String> entries,
            boolean[] enabled,
            int selected,
            List<String> foot,
            String hint) {
        int lines = firstEntryLine(head) + entries.size() + foot.size() + 2;
        int widest = Math.max(hint.length(), Math.max(longest(head), Math.max(longest(entries), longest(foot))));
        Rect panel = layout.panel(view, anchor, lines, widest);
        int columns = TextFit.columns(panel.width() - 4 * metrics.padding(), UiFont.ADVANCE);
        int x = layout.lineX(panel);

        hud.beginCanvas();
        hud.fill(panel.x(), panel.y(), panel.width(), panel.height(), style.bar());
        hud.frame(panel.x(), panel.y(), panel.width(), panel.height(), style.textDim());
        int line = 0;
        for (int i = 0; i < head.size(); i++, line++) {
            hud.text(
                    TextFit.fit(head.get(i), columns),
                    x,
                    layout.lineTop(panel, line),
                    i == 0 ? style.text() : style.textDim());
        }
        line = firstEntryLine(head);
        for (int i = 0; i < entries.size(); i++, line++) {
            int top = layout.lineTop(panel, line);
            if (i == selected) {
                hud.frame(
                        panel.x() + metrics.padding(),
                        top - metrics.fontSize() - metrics.leading() / 2 - 1,
                        panel.width() - 2 * metrics.padding(),
                        metrics.fontSize() + metrics.leading() + 2,
                        style.text());
            }
            hud.text(TextFit.fit(entries.get(i), columns), x, top, enabled[i] ? style.text() : style.textDim());
        }
        line++;
        for (int i = 0; i < foot.size(); i++, line++) {
            hud.text(TextFit.fit(foot.get(i), columns), x, layout.lineTop(panel, line), style.textDim());
        }
        hud.text(TextFit.fit(hint, columns), x, layout.lineTop(panel, line), style.textDim());
        hud.endCanvas();
        return panel;
    }

    /** The line the first entry is on - a blank line under the head - for finding what the pointer is over. */
    static int firstEntryLine(List<String> head) {
        return head.isEmpty() ? 0 : head.size() + 1;
    }

    MenuLayout layout() {
        return layout;
    }

    private static int longest(List<String> lines) {
        int longest = 0;
        for (String line : lines) {
            longest = Math.max(longest, line.length());
        }
        return longest;
    }
}
