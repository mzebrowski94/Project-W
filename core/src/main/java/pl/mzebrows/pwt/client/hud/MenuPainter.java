package pl.mzebrows.pwt.client.hud;

import com.badlogic.gdx.graphics.Color;
import java.util.List;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.ui.hud.TextFit;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.page.MenuLayout;

/**
 * Draws a page or a modal on the UI canvas: its panel, head, entries, foot and hint - the selected entry
 * ringed, one it cannot confirm dim - and the scrim, the paused banner and the toast over the view.
 *
 * <p>Painted directly, like the bars, rather than built from scene2d widgets (plan D14): the pages are
 * short columns of text in the interface's own pixel font, which a table of widgets would only wrap, and
 * everything that decides - where a line is, which entry is under the pointer - is {@link MenuLayout},
 * tested without a display. Decides nothing itself.
 */
public final class MenuPainter {

    private final Hud hud;
    private final GraphicConfig graphic;
    private final HudMetrics metrics;
    private final MenuLayout layout;
    private final Color scrim = new Color();
    private final Color text = new Color();
    private final Color dim = new Color();
    private final Color bar = new Color();

    public MenuPainter(Hud hud, GraphicConfig graphic, HudMetrics metrics) {
        this.hud = hud;
        this.graphic = graphic;
        this.metrics = metrics;
        this.layout = menuLayout(metrics);
    }

    static MenuLayout menuLayout(HudMetrics metrics) {
        return new MenuLayout(
                metrics.padding(), metrics.fontSize() + metrics.leading(), UiFont.ADVANCE, metrics.fontSize());
    }

    /** Dims the view by {@code alpha}; nothing at zero. */
    public void scrim(Rect view, float alpha) {
        if (alpha <= 0f) {
            return;
        }
        hud.beginCanvas();
        hud.fill(view.x(), view.y(), view.width(), view.height(), scrim.set(0f, 0f, 0f, alpha));
        hud.endCanvas();
    }

    /** One line at the top of the view: the game is paused, and which button opens the menu. */
    public void banner(Rect view, String text) {
        line(
                view,
                text,
                text.length(),
                false,
                graphic.hud().color().textDim(),
                graphic.hud().color().text());
    }

    /**
     * A toast where the banner goes ({@code UI:hud/view/toast}): framed and written in {@code color},
     * {@code columns} wide whatever is typed so far, so the box does not grow as it types.
     */
    void toast(Rect view, String typed, int columns, boolean cursor, Color color) {
        line(view, typed, columns, cursor, color, color);
    }

    /** The widest a line over the view may be, in characters. */
    int lineColumns(Rect view) {
        return TextFit.columns(view.width() - 4 * metrics.padding(), UiFont.ADVANCE);
    }

    private void line(Rect view, String text, int columns, boolean cursor, Color frame, Color ink) {
        int width = columns * UiFont.ADVANCE + 4 * metrics.padding();
        int height = metrics.fontSize() + 2 * metrics.padding() + metrics.leading();
        int x = view.x() + (view.width() - width) / 2;
        int y = view.top() - 2 * metrics.padding() - height;
        int textX = x + 2 * metrics.padding();
        int textTop = y + height - metrics.padding() - metrics.leading();
        hud.beginCanvas();
        hud.fill(x, y, width, height, graphic.hud().color().bar());
        hud.frame(x, y, width, height, frame);
        hud.text(text, textX, textTop, ink);
        if (cursor) {
            hud.text("_", textX + text.length() * UiFont.ADVANCE, textTop, ink);
        }
        hud.endCanvas();
    }

    /**
     * A page's panel.
     *
     * @param head title and body lines, already in words; the first is drawn bright
     * @param entries every entry's words; only {@code visible} of them from {@code scroll} are drawn
     * @param enabled which entries can be confirmed
     * @param selected the selected entry, or -1 for none (pointer mode, nothing hovered)
     * @param foot lines under the entries, dim
     * @param hint the last line: which buttons select and go back
     * @param offsetX where the page's arrival has it, in UI pixels
     * @param alpha how opaque its arrival has it
     */
    public Rect page(
            Rect view,
            MenuLayout.Anchor anchor,
            List<String> head,
            List<String> entries,
            boolean[] enabled,
            int selected,
            int scroll,
            int visible,
            List<String> foot,
            String hint,
            int offsetX,
            float alpha) {
        return page(
                view,
                anchor,
                graphic.ui().page().columns(),
                head,
                entries,
                enabled,
                selected,
                scroll,
                visible,
                foot,
                hint,
                offsetX,
                alpha);
    }

    /** The same, {@code columns} characters wide rather than a page's width - a panel one of a pair. */
    public Rect page(
            Rect view,
            MenuLayout.Anchor anchor,
            int columns,
            List<String> head,
            List<String> entries,
            boolean[] enabled,
            int selected,
            int scroll,
            int visible,
            List<String> foot,
            String hint,
            int offsetX,
            float alpha) {
        int shown = Math.min(visible, entries.size() - scroll);
        int lines = firstEntryLine(head) + shown + foot.size() + 2;
        Rect placed = layout.panel(view, anchor, lines, columns);
        Rect panel = new Rect(placed.x() + offsetX, placed.y(), placed.width(), placed.height());
        int fits = TextFit.columns(panel.width() - 4 * metrics.padding(), UiFont.ADVANCE);
        int x = layout.lineX(panel);
        Color text = faded(this.text, graphic.hud().color().text(), alpha);
        Color dim = faded(this.dim, graphic.hud().color().textDim(), alpha);

        hud.beginCanvas();
        hud.fill(
                panel.x(),
                panel.y(),
                panel.width(),
                panel.height(),
                faded(this.bar, graphic.hud().color().bar(), alpha));
        hud.frame(panel.x(), panel.y(), panel.width(), panel.height(), dim);
        int line = 0;
        for (int i = 0; i < head.size(); i++, line++) {
            hud.text(TextFit.fit(head.get(i), fits), x, layout.lineTop(panel, line), i == 0 ? text : dim);
        }
        line = firstEntryLine(head);
        for (int i = scroll; i < scroll + shown; i++, line++) {
            int top = layout.lineTop(panel, line);
            if (i == selected) {
                hud.frame(
                        panel.x() + metrics.padding(),
                        top - metrics.fontSize() - metrics.leading() / 2 - 1,
                        panel.width() - 2 * metrics.padding(),
                        metrics.fontSize() + metrics.leading() + 2,
                        text);
            }
            hud.text(TextFit.fit(entries.get(i), fits), x, top, enabled[i] ? text : dim);
        }
        if (scroll > 0) {
            hud.text(
                    "^",
                    panel.right() - 2 * metrics.padding() - UiFont.ADVANCE,
                    layout.lineTop(panel, firstEntryLine(head)),
                    dim);
        }
        if (scroll + shown < entries.size()) {
            hud.text("v", panel.right() - 2 * metrics.padding() - UiFont.ADVANCE, layout.lineTop(panel, line - 1), dim);
        }
        line++;
        for (int i = 0; i < foot.size(); i++, line++) {
            hud.text(TextFit.fit(foot.get(i), fits), x, layout.lineTop(panel, line), dim);
        }
        hud.text(TextFit.fit(hint, fits), x, layout.lineTop(panel, line), dim);
        hud.endCanvas();
        return panel;
    }

    /** How many entries a panel in this view has room for, with this much head and foot. */
    public int visibleEntries(Rect view, List<String> head, int entries, int footLines) {
        int pitch = metrics.fontSize() + metrics.leading();
        int room = (view.height() - 3 * metrics.padding()) / pitch - firstEntryLine(head) - footLines - 2;
        return Math.max(1, Math.min(entries, room));
    }

    private static Color faded(Color into, Color from, float alpha) {
        return into.set(from.r, from.g, from.b, from.a * alpha);
    }

    /** The line the first entry is on - a blank line under the head - for finding what the pointer is over. */
    public static int firstEntryLine(List<String> head) {
        return head.isEmpty() ? 0 : head.size() + 1;
    }

    public MenuLayout layout() {
        return layout;
    }

    /**
     * Adds {@code text} to {@code lines} wrapped to a page's width, on at most {@code maxLines} lines -
     * the last one cut with an ellipsis if it runs over. For body lines and descriptions, which are
     * sentences; titles, entries and hints are short enough for one line.
     */
    public void addWrapped(List<String> lines, String text, int maxLines) {
        lines.addAll(TextFit.wrap(text, graphic.ui().page().columns(), maxLines));
    }
}
