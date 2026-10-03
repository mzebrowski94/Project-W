package pl.mzebrows.pwt.client.hud;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.List;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.draw.Palette;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.ui.hud.RankText;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.hud.TextFit;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.page.MenuLayout;
import pl.mzebrows.pwt.ui.page.ScrollList;

/**
 * Every upgrade the side holds, beside the pause menu ({@code UI:play/pause}): the aside in the
 * top bar has room for a few, and a paused player has time to read them all. One row each - a personal
 * one named for its holder and in his colour, an adaptation in the swarm's - with its rank in words and as pips, docked to the right of
 * the view over the same scrim.
 *
 * <p>As wide as its longest row and never wider than the room right of the pause menu, so the two never
 * overlap; under the menu where there is no room beside it (A). More rows than the panel has lines for
 * scroll - a wheel over the list, a pad's right stick - with a bar on its right edge (A.1).
 */
public final class HeldUpgradesPainter {

    private final Hud hud;
    private final GraphicConfig graphic;
    private final HudMetrics metrics;
    private final Palette palette;
    private final UiStrings strings;
    private final MenuLayout layout;
    private final CardText names;
    private final String title;
    private final String none;
    private final ScrollList scroll = new ScrollList();

    /** The narrowest the panel may be beside the menu: its title and its empty line, whole. */
    private final int leastColumns;

    /** Every row in full, built when what is held changes. */
    private final List<String> full = new ArrayList<>();

    /** The same rows cut to the panel's width. */
    private final List<String> rows = new ArrayList<>();

    private final Color text = new Color();
    private final Color dim = new Color();
    private final Color own = new Color();
    private final Color bar = new Color();

    private long built = -1L;
    private int fittedFor = -1;
    private int longest;

    /** How many rows the panel had room for when it was last drawn: what a scroll is measured against. */
    private int room;

    /** The panel as last drawn, so a wheel is read only over it. */
    private Rect panel = Rect.EMPTY;

    public HeldUpgradesPainter(Hud hud, UiStrings strings, GraphicConfig graphic, HudMetrics metrics, Palette palette) {
        this.hud = hud;
        this.graphic = graphic;
        this.metrics = metrics;
        this.palette = palette;
        this.strings = strings;
        this.layout = MenuPainter.menuLayout(metrics);
        this.names = new CardText(strings);
        this.title = strings.get("ui.pause.upgrades");
        this.none = strings.get("ui.pause.upgradesNone");
        this.leastColumns = Math.max(title.length(), none.length());
    }

    /**
     * Draws the list, as opaque as the pause page's arrival has it.
     *
     * @param page where the pause menu's panel rests, which the list keeps clear of
     */
    public void draw(Rect view, Rect page, Readout readout, float alpha) {
        if (readout.upgradeRevision != built) {
            build(readout);
            built = readout.upgradeRevision;
            fittedFor = -1;
        }
        int pips = maxPipColumns(readout);
        int widest = Math.max(leastColumns, full.isEmpty() ? 0 : longest + 1 + pips);
        int lines = 2 + Math.max(1, full.size());
        panel = layout.beside(view, page, lines, widest, leastColumns);
        if (panel.isEmpty()) {
            return;
        }
        room = Math.max(1, layout.linesIn(panel) - 2);
        scroll.keep(full.size(), room);
        int columns = TextFit.columns(panel.width() - 4 * metrics.padding(), UiFont.ADVANCE);
        int textColumns = Math.max(1, columns - 1 - pips);
        if (textColumns != fittedFor) {
            rows.clear();
            for (String row : full) {
                rows.add(TextFit.fit(row, textColumns));
            }
            fittedFor = textColumns;
        }
        int x = layout.lineX(panel);
        faded(text, graphic.hud().color().text(), alpha);
        faded(dim, graphic.hud().color().textDim(), alpha);

        hud.beginCanvas();
        hud.fill(
                panel.x(),
                panel.y(),
                panel.width(),
                panel.height(),
                faded(bar, graphic.hud().color().bar(), alpha));
        hud.frame(panel.x(), panel.y(), panel.width(), panel.height(), dim);
        hud.text(TextFit.fit(title, columns), x, layout.lineTop(panel, 0), text);
        if (rows.isEmpty()) {
            hud.text(TextFit.fit(none, columns), x, layout.lineTop(panel, 2), dim);
        }
        int pipsRight = panel.right() - 2 * metrics.padding();
        int last = Math.min(rows.size(), scroll.first() + room);
        for (int row = scroll.first(); row < last; row++) {
            int top = layout.lineTop(panel, 2 + row - scroll.first());
            int slot = readout.heldSlot[row];
            Color color = readout.heldAlien[row]
                    ? faded(own, graphic.hud().color().swarm(), alpha)
                    : slot < 0 ? text : faded(own, palette.colorFor(Look.COMMANDER, slot), alpha);
            hud.text(rows.get(row), x, top, color);
            int max = readout.heldMaxRanks[row];
            RankPips.draw(
                    hud,
                    metrics,
                    pipsRight - RankPips.width(metrics, max),
                    top,
                    readout.heldRanks[row],
                    max,
                    color,
                    dim);
        }
        if (ScrollList.scrolls(rows.size(), room)) {
            drawBar(rows.size());
        }
        hud.endCanvas();
    }

    /** Moves the list by a wheel's notches, when the pointer is over it. */
    public void wheel(float notches, int pointerX, int pointerY) {
        if (notches == 0f
                || pointerX < panel.x()
                || pointerX >= panel.right()
                || pointerY < panel.y()
                || pointerY >= panel.top()) {
            return;
        }
        scroll.step(notches > 0f ? 1 : -1, full.size(), room);
    }

    /** Moves the list by a stick's push, down for positive. */
    public void glide(float push, float linesPerSecond, float seconds) {
        scroll.glide(push, linesPerSecond, seconds, full.size(), room);
    }

    /** Back to the top, for the next time the pause menu opens. */
    public void home() {
        scroll.home();
    }

    /** The scrollbar: a thin track down the right padding, the thumb as long as the share on show. */
    private void drawBar(int count) {
        int pad = metrics.padding();
        int x = panel.right() - pad - pad / 2 - 1;
        int trackTop = layout.lineTop(panel, 2);
        int trackBottom = panel.y() + pad;
        int track = Math.max(1, trackTop - trackBottom);
        int thumb = ScrollList.thumb(track, count, room);
        int offset = ScrollList.thumbOffset(track, scroll.first(), count, room);
        hud.fill(x, trackBottom, 2, track, dim);
        hud.fill(x, trackTop - offset - thumb, 2, thumb, text);
    }

    /** Built when what is held changes: every row whole, and how long the longest is. */
    private void build(Readout readout) {
        full.clear();
        longest = 0;
        for (int i = 0; i < readout.heldCount; i++) {
            String row = whose(
                    strings, readout, i, names.name(readout.heldKind[i]) + " " + RankText.roman(readout.heldRanks[i]));
            full.add(row);
            longest = Math.max(longest, row.length());
        }
    }

    /**
     * A held entry's name with whose it is in front: {@code P2} for a personal upgrade, {@code SWARM} for
     * an adaptation (ADR 0017), nothing for the side's. Shared with the aside, so the two cannot disagree.
     */
    static String whose(UiStrings strings, Readout readout, int index, String name) {
        if (readout.heldAlien[index]) {
            return strings.get("ui.hud.swarm") + " " + name;
        }
        int slot = readout.heldSlot[index];
        return slot < 0 ? name : strings.format("ui.hud.player", slot + 1) + " " + name;
    }

    private int maxPipColumns(Readout readout) {
        int most = 0;
        for (int i = 0; i < readout.heldCount; i++) {
            most = Math.max(most, readout.heldMaxRanks[i]);
        }
        return most == 0 ? 0 : (RankPips.width(metrics, most) + UiFont.ADVANCE - 1) / UiFont.ADVANCE;
    }

    private static Color faded(Color into, Color from, float alpha) {
        return into.set(from.r, from.g, from.b, from.a * alpha);
    }
}
