package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.ui.hud.PanelAlarms;
import pl.mzebrows.pwt.ui.hud.PanelLayout;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;
import pl.mzebrows.pwt.ui.hud.Posture;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;

/**
 * The bottom bar ({@code UI:hud/bottom/...}): four fixed quarters, one per local seat in seat order,
 * each holding that player's panel - frame and label in his colour, his status, his gun and its heat,
 * a block where his portrait will be, and a row kept for his tally. A seat with no commander and no
 * respawn pending stays black.
 *
 * <p>Status is empty while he stands: quiet until it matters, and always in the same place when it
 * does. DOWN, KIA and OVERHEAT are the only red, drawn as black text on it (plan 7.0).
 */
final class BottomBarPainter {

    private static final int HEAT_BAR_WIDTH = 68;
    private static final float DIM_ALERT = 0.55f;

    private final Hud hud;
    private final HudStyle style;
    private final HudMetrics metrics;
    private final Palette palette;
    private final PanelLayout panels;
    private final String[] labels = new String[Players.MAX];
    private final Map<WeaponKind, String> weapons = new EnumMap<>(WeaponKind.class);
    private final String down;
    private final String kiaTemplate;
    private final String aboard;
    private final String overheat;
    private final String[] kia = new String[Players.MAX];
    private final int[] kiaTenths = new int[Players.MAX];
    private final Color dimAlert;
    private final int weaponColumns;

    BottomBarPainter(Hud hud, UiStrings strings, HudStyle style, HudMetrics metrics, Palette palette) {
        this.hud = hud;
        this.style = style;
        this.metrics = metrics;
        this.palette = palette;
        this.panels = new PanelLayout(metrics.padding(), metrics.portraitSize(), PixelFont.ADVANCE);
        int longest = 0;
        for (WeaponKind kind : WeaponKind.values()) {
            String name = strings.get("ui.weapon." + kind.name());
            weapons.put(kind, name);
            longest = Math.max(longest, name.length());
        }
        this.weaponColumns = longest;
        for (int slot = 0; slot < Players.MAX; slot++) {
            labels[slot] = strings.format("ui.hud.player", slot + 1);
            kia[slot] = "";
            kiaTenths[slot] = -1;
        }
        this.down = strings.get("ui.hud.down");
        this.kiaTemplate = strings.get("ui.hud.kia");
        this.aboard = strings.get("ui.hud.aboard");
        this.overheat = strings.get("ui.hud.overheat");
        this.dimAlert = new Color(style.alert()).mul(DIM_ALERT, DIM_ALERT, DIM_ALERT, 1f);
    }

    void render(ScreenLayout layout, Readout readout, ScenarioPlay play, PanelAlarms alarms) {
        Rect bottom = layout.bottomBar();
        if (bottom.isEmpty()) {
            return;
        }
        hud.beginCanvas();
        for (int seat = 0; seat < PanelLayout.QUARTERS; seat++) {
            int slot = play.slotOf(seat);
            if (slot < 0 || slot >= Players.MAX || readout.player(slot).posture == Posture.ABSENT) {
                continue;
            }
            int x = panels.quarterX(seat, layout.contentLeft(), layout.view().width()) + alarms.shakeOffset(slot);
            int width = panels.quarterWidth(seat, layout.view().width());
            panel(slot, readout.player(slot), x, width, bottom.top(), alarms);
        }
        hud.endCanvas();
    }

    private void panel(int slot, PlayerReadout player, int x, int width, int top, PanelAlarms alarms) {
        Color own = palette.colorFor(Look.COMMANDER, player.variant);
        int padding = metrics.padding();
        int height = metrics.bottomMin();
        int line = metrics.fontSize() + metrics.leading();
        hud.frame(x + 1, top - height + 1, width - 2, height - 2, alarms.flashing(slot) ? style.alert() : own);
        if (panels.showsPortrait(width)) {
            int portrait = metrics.portraitSize();
            hud.fill(x + padding, top - padding - portrait, portrait, portrait, own);
        }
        int textX = x + panels.textOffset(width);
        int textRight = x + width - padding;
        Color alert = alarms.alertBright() ? style.alert() : dimAlert;

        int row = top - padding;
        hud.text(labels[slot], textX, row, own);
        int statusX = textX + (labels[slot].length() + 1) * PixelFont.ADVANCE;
        switch (player.posture) {
            case CASUALTY -> {
                inverse(down, statusX, row, alert);
                int barX = statusX + (down.length() + 1) * PixelFont.ADVANCE;
                bar(barX, row, textRight - barX, player.bleedOut, style.alert());
            }
            case DYING -> inverse(kiaText(slot, -1f), statusX, row, alert);
            case RESPAWNING -> inverse(kiaText(slot, player.respawnSeconds), statusX, row, alert);
            case ABOARD -> hud.text(aboard, statusX, row, style.text());
            default -> {
                // Standing: nothing to say.
            }
        }

        row -= line;
        if (player.posture == Posture.STANDING || player.posture == Posture.ABOARD) {
            hud.text(weapons.getOrDefault(player.weapon, ""), textX, row, style.text());
            int heatX = textX + (weaponColumns + 1) * PixelFont.ADVANCE;
            if (player.overheated) {
                inverse(overheat, heatX, row, alert);
            } else {
                bar(heatX, row, Math.min(HEAT_BAR_WIDTH, textRight - heatX), player.heat, palette.colorFor(Look.HEAT));
            }
        } else if (player.posture == Posture.CASUALTY) {
            hud.text(weapons.getOrDefault(player.weapon, ""), textX, row, style.textDim());
        }
        // Row 3 is the tally's, which arrives with stage 7.
    }

    /** {@code KIA} and the seconds to his return, formatted only when the tenth changes. */
    private String kiaText(int slot, float seconds) {
        int tenths = seconds < 0f ? -1 : (int) Math.ceil(seconds * 10f);
        if (tenths != kiaTenths[slot] || kia[slot].isEmpty()) {
            kiaTenths[slot] = tenths;
            kia[slot] = tenths < 0
                    ? kiaTemplate.replace("{0}", "").trim()
                    : kiaTemplate.replace("{0}", String.format(Locale.ROOT, "%.1f", tenths / 10f));
        }
        return kia[slot];
    }

    /** Black text on the alert colour: the only way alert red is ever drawn. */
    private void inverse(String text, int x, int top, Color alert) {
        hud.fill(x - 1, top - metrics.fontSize(), text.length() * PixelFont.ADVANCE + 1, metrics.fontSize() + 1, alert);
        hud.text(text, x, top, style.bar());
    }

    /** A bar the height of a capital, filled from the left: heat, or a bleed-out running down. */
    private void bar(int x, int top, int width, float fill, Color color) {
        if (width < 3) {
            return;
        }
        int height = metrics.fontSize() - 2;
        hud.frame(x, top - height, width, height, style.textDim());
        float clamped = Math.max(0f, Math.min(1f, fill));
        hud.fill(x + 1, top - height + 1, Math.round((width - 2) * clamped), height - 2, color);
    }
}
