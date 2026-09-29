package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.List;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.ui.hud.RankText;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.hud.TextFit;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.page.MenuLayout;

/**
 * Every upgrade the side holds, beside the pause menu ({@code UI:play/pause}): the aside in the
 * top bar has room for a few, and a paused player has time to read them all. One row each - a personal
 * one named for its holder and in his colour - with its rank in words and as pips, docked to the right of
 * the view over the same scrim. More than the view has lines for ends in a count of the rest.
 */
final class HeldUpgradesPainter {

    private final Hud hud;
    private final HudStyle style;
    private final HudMetrics metrics;
    private final Palette palette;
    private final UiStrings strings;
    private final MenuLayout layout;
    private final CardText names;
    private final String title;
    private final String none;
    private final String more;
    private final List<String> rows = new ArrayList<>();
    private final Color text = new Color();
    private final Color dim = new Color();
    private final Color own = new Color();
    private final Color bar = new Color();

    private long built = -1L;
    private int builtFor = -1;
    private int shown;

    HeldUpgradesPainter(Hud hud, UiStrings strings, HudStyle style, HudMetrics metrics, Palette palette) {
        this.hud = hud;
        this.style = style;
        this.metrics = metrics;
        this.palette = palette;
        this.strings = strings;
        this.layout = MenuPainter.menuLayout(metrics);
        this.names = new CardText(strings);
        this.title = strings.get("ui.pause.upgrades");
        this.none = strings.get("ui.pause.upgradesNone");
        this.more = strings.get("ui.hud.aside.more");
    }

    /** Draws the list, as opaque as the pause page's arrival has it. */
    void draw(Rect view, Readout readout, float alpha) {
        int pitch = metrics.fontSize() + metrics.leading();
        int room = Math.max(1, (view.height() - 7 * metrics.padding()) / pitch - 2);
        if (readout.upgradeRevision != built || room != builtFor) {
            build(readout, room);
            built = readout.upgradeRevision;
            builtFor = room;
        }
        int lines = 2 + Math.max(1, rows.size());
        Rect panel = layout.panel(view, MenuLayout.Anchor.DOCKED_RIGHT, lines, style.pageColumns());
        int x = layout.lineX(panel);
        int columns = TextFit.columns(panel.width() - 4 * metrics.padding(), UiFont.ADVANCE);
        faded(text, style.text(), alpha);
        faded(dim, style.textDim(), alpha);

        hud.beginCanvas();
        hud.fill(panel.x(), panel.y(), panel.width(), panel.height(), faded(bar, style.bar(), alpha));
        hud.frame(panel.x(), panel.y(), panel.width(), panel.height(), dim);
        hud.text(TextFit.fit(title, columns), x, layout.lineTop(panel, 0), text);
        if (rows.isEmpty()) {
            hud.text(TextFit.fit(none, columns), x, layout.lineTop(panel, 2), dim);
        }
        int pipsRight = panel.right() - 2 * metrics.padding();
        for (int row = 0; row < rows.size(); row++) {
            int top = layout.lineTop(panel, 2 + row);
            boolean rest = row == shown;
            int slot = rest ? -1 : readout.heldSlot[row];
            Color color = slot < 0 ? (rest ? dim : text) : faded(own, palette.colorFor(Look.COMMANDER, slot), alpha);
            hud.text(rows.get(row), x, top, color);
            if (!rest) {
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
        }
        hud.endCanvas();
    }

    /** Built when what is held changes; the pips' room comes off the text's. */
    private void build(Readout readout, int room) {
        rows.clear();
        int columns = style.pageColumns() - 1 - maxPipColumns(readout);
        shown = readout.heldCount <= room ? readout.heldCount : room - 1;
        for (int i = 0; i < shown; i++) {
            int slot = readout.heldSlot[i];
            String name = names.name(readout.heldKind[i]) + " " + RankText.roman(readout.heldRanks[i]);
            rows.add(TextFit.fit(slot < 0 ? name : strings.format("ui.hud.player", slot + 1) + " " + name, columns));
        }
        if (shown < readout.heldCount) {
            rows.add(TextFit.fit(more.replace("{0}", Integer.toString(readout.heldCount - shown)), columns));
        }
    }

    private int maxPipColumns(Readout readout) {
        int most = 0;
        for (int i = 0; i < readout.heldCount; i++) {
            most = Math.max(most, readout.heldMaxRanks[i]);
        }
        return (RankPips.width(metrics, most) + UiFont.ADVANCE - 1) / UiFont.ADVANCE;
    }

    private static Color faded(Color into, Color from, float alpha) {
        return into.set(from.r, from.g, from.b, from.a * alpha);
    }
}
