package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.ui.hud.DispatchKind;
import pl.mzebrows.pwt.ui.hud.TextFit;
import pl.mzebrows.pwt.ui.hud.Toast;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;

/**
 * The toast ({@code UI:hud/view/toast}) where the paused banner goes: an upgrade earned or taken in that
 * player's colour - the text colour while he is the only player - and the swarm adapting in the swarm's. The box
 * is as wide as the whole line from the first character, and the text is cut only when the toast or the
 * view changes.
 */
final class ToastPainter {

    private final MenuPainter painter;
    private final HudStyle style;
    private final Palette palette;

    private String fitted = "";
    private String typed = "";
    private int builtRevision = -1;
    private int builtWidth = -1;
    private int typedLength = -1;

    ToastPainter(Hud hud, HudStyle style, HudMetrics metrics, Palette palette) {
        this.painter = new MenuPainter(hud, style, metrics);
        this.style = style;
        this.palette = palette;
    }

    void draw(Rect view, Toast toast) {
        if (!toast.showing()) {
            return;
        }
        if (toast.revision() != builtRevision || view.width() != builtWidth) {
            // A column kept for the cursor.
            fitted = TextFit.fit(toast.line(), Math.max(1, painter.lineColumns(view) - 1));
            builtRevision = toast.revision();
            builtWidth = view.width();
            typedLength = -1;
        }
        int length = Math.min(toast.revealed(), fitted.length());
        if (length != typedLength) {
            typed = fitted.substring(0, length);
            typedLength = length;
        }
        painter.toast(view, typed, fitted.length() + 1, toast.cursor(), color(toast));
    }

    private Color color(Toast toast) {
        if (toast.kind() == DispatchKind.SWARM_ADAPTS) {
            return style.swarm();
        }
        return toast.slot() < 0 ? style.text() : palette.colorFor(Look.COMMANDER, toast.slot());
    }
}
