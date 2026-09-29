package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.ui.hud.PanelAlarms;
import pl.mzebrows.pwt.ui.hud.PanelAssignment;
import pl.mzebrows.pwt.ui.hud.PanelLayout;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;
import pl.mzebrows.pwt.ui.hud.Posture;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.hud.TallyIcon;
import pl.mzebrows.pwt.ui.hud.UpgradeTell;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;

/**
 * The bottom bar ({@code UI:hud/bottom/...}): four fixed quarters, each holding one player's panel -
 * frame and label in his colour, his status, his gun and its heat, a block where his portrait will be,
 * and a row kept for his tally. Who is in which quarter is {@link PanelAssignment}'s: everybody while
 * there are four or fewer, this machine's seats otherwise. A quarter nobody holds stays black; a player
 * whose pad or link is gone is drawn grey and says OFFLINE.
 *
 * <p>Status is empty while he stands: quiet until it matters, and always in the same place when it
 * does. DOWN, KIA and OVERHEAT are the only red, drawn as black text on it (plan 7.0). While his slot
 * holds upgrade drafts, the tell ({@code UI:hud/bottom/upgrade}) says so at the row's right end, in his
 * colour, with the key that opens them.
 */
final class BottomBarPainter {

    private static final int HEAT_BAR_WIDTH = 68;

    /** Columns a bleed-out bar keeps however much the tell wants. */
    private static final int MIN_BLEED_COLUMNS = 4;

    private static final float DIM_ALERT = 0.55f;

    /** The tally row's marks, in order. */
    private static final TallyIcon[] TALLY_ICONS = {TallyIcon.KILLS, TallyIcon.GOALS, TallyIcon.STABILISED};

    /** Characters a count takes at most: {@code 999+}. */
    private static final int TALLY_COLUMNS = 4;

    private final Hud hud;
    private final HudStyle style;
    private final HudMetrics metrics;
    private final Palette palette;
    private final PanelLayout panels;
    private final String[] labels = new String[Players.MAX];
    private final Map<WeaponKind, String> weapons = new EnumMap<>(WeaponKind.class);
    private final String shieldText;
    private final String stunnedText;
    private final String down;
    private final String kiaTemplate;
    private final String kiaWaiting;
    private final String aboard;
    private final String overheat;
    private final String offline;
    private final String rollCall;
    private final String[] kia = new String[Players.MAX];
    private final int[] kiaTenths = new int[Players.MAX];
    private final int[][] tallyShown = new int[Players.MAX][3];
    private final String[][] tallyText = new String[Players.MAX][3];
    private final Color dimAlert;
    private final int weaponColumns;
    private final UpgradeTell tell;
    private final String[] tellText = new String[Players.MAX];
    private final int[] tellDrafts = new int[Players.MAX];
    private final int[] tellColumns = new int[Players.MAX];
    private final String[] tellKey = new String[Players.MAX];

    /** Scratch for the tell's glow and text, so drawing it allocates nothing per frame. */
    private final Color tellBacking = new Color();

    private final Color tellLit = new Color();

    BottomBarPainter(Hud hud, UiStrings strings, HudStyle style, HudMetrics metrics, Palette palette) {
        this.hud = hud;
        this.style = style;
        this.metrics = metrics;
        this.palette = palette;
        this.panels = new PanelLayout(metrics.padding(), metrics.portraitSize(), UiFont.ADVANCE);
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
            Arrays.fill(tallyShown[slot], -1);
            Arrays.fill(tallyText[slot], "");
        }
        this.down = strings.get("ui.hud.down");
        this.shieldText = strings.get("ui.hud.shield");
        this.stunnedText = strings.get("ui.hud.stunned");
        this.kiaTemplate = strings.get("ui.hud.kia");
        this.kiaWaiting = strings.get("ui.hud.kiaNoReserve");
        this.aboard = strings.get("ui.hud.aboard");
        this.overheat = strings.get("ui.hud.overheat");
        this.offline = strings.get("ui.hud.offline");
        this.rollCall = strings.get("ui.hud.rollCall");
        this.dimAlert = new Color(style.alert()).mul(DIM_ALERT, DIM_ALERT, DIM_ALERT, 1f);
        this.tell = new UpgradeTell(
                strings.get("ui.hud.upgrade"), strings.get("ui.hud.upgrades"), strings.get("ui.hud.upgradeBrief"));
        Arrays.fill(tellText, "");
        Arrays.fill(tellKey, "");
        Arrays.fill(tellDrafts, -1);
    }

    /**
     * @param connected by slot: false for a player whose pad or link is gone, whose panel is greyed
     * @param draftKeys by slot: what that player presses to open his drafts, or empty when he is not
     *     played here
     */
    void render(
            ScreenLayout layout,
            Readout readout,
            PanelAssignment seating,
            boolean[] connected,
            PanelAlarms alarms,
            String[] draftKeys) {
        Rect bottom = layout.bottomBar();
        if (bottom.isEmpty()) {
            return;
        }
        hud.beginCanvas();
        for (int quarter = 0; quarter < PanelLayout.QUARTERS; quarter++) {
            int slot = seating.slotIn(quarter);
            if (slot < 0 || slot >= Players.MAX) {
                continue;
            }
            int x = panels.quarterX(quarter, layout.contentLeft(), layout.view().width()) + alarms.shakeOffset(slot);
            int width = panels.quarterWidth(quarter, layout.view().width());
            panel(slot, readout.player(slot), connected[slot], x, width, bottom.top(), alarms, draftKeys[slot]);
        }
        hud.endCanvas();
    }

    private void panel(
            int slot,
            PlayerReadout player,
            boolean connected,
            int x,
            int width,
            int top,
            PanelAlarms alarms,
            String draftKey) {
        Color own = connected ? palette.colorFor(Look.COMMANDER, player.variant) : style.textDim();
        Color text = connected ? style.text() : style.textDim();
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
        int statusX = textX + (labels[slot].length() + 1) * UiFont.ADVANCE;
        String upgrade = tell(
                slot,
                player.drafts,
                draftKey,
                panels.textColumns(width) - labels[slot].length() - 1 - statusColumns(slot, player, connected) - 1);
        int tellX = textRight - upgrade.length() * UiFont.ADVANCE;
        if (!upgrade.isEmpty()) {
            lit(upgrade, tellX, row, own, connected ? alarms.swell(style.tellPulseHz()) : 0f);
        }
        switch (player.posture) {
            case CASUALTY -> {
                inverse(down, statusX, row, alert);
                int barX = statusX + (down.length() + 1) * UiFont.ADVANCE;
                int barRight = upgrade.isEmpty() ? textRight : tellX - UiFont.ADVANCE;
                bar(barX, row, barRight - barX, player.bleedOut, style.alert());
            }
            case DYING -> inverse(kiaText(slot, -1f), statusX, row, alert);
            case RESPAWNING -> inverse(respawnText(slot, player), statusX, row, alert);
            case ABOARD -> hud.text(connected ? aboard : offline, statusX, row, text);
            default -> {
                if (!connected) {
                    hud.text(offline, statusX, row, text);
                } else if (player.posture == Posture.STANDING) {
                    standing(player, statusX, row, alert);
                }
            }
        }

        row -= line;
        if (player.posture == Posture.STANDING || player.posture == Posture.ABOARD) {
            hud.text(weapons.getOrDefault(player.weapon, ""), textX, row, text);
            int heatX = textX + (weaponColumns + 1) * UiFont.ADVANCE;
            if (player.overheated) {
                inverse(overheat, heatX, row, alert);
            } else {
                bar(heatX, row, Math.min(HEAT_BAR_WIDTH, textRight - heatX), player.heat, palette.colorFor(Look.HEAT));
            }
        } else if (player.posture == Posture.CASUALTY) {
            hud.text(weapons.getOrDefault(player.weapon, ""), textX, row, style.textDim());
        }

        row -= line;
        if (player.posture != Posture.ABSENT) {
            tally(slot, player, textX, row);
        }
    }

    /** Columns row 1's status takes after the label, a bleed-out bar's least width included. */
    private int statusColumns(int slot, PlayerReadout player, boolean connected) {
        return switch (player.posture) {
            case CASUALTY -> down.length() + 1 + MIN_BLEED_COLUMNS;
            case DYING -> kiaText(slot, -1f).length();
            case RESPAWNING -> respawnText(slot, player).length();
            case ABOARD -> connected ? aboard.length() : offline.length();
            default -> connected ? standingColumns(player) : offline.length();
        };
    }

    /** Row 1 while he stands: his wounds, then a shield in front of him, then a stun he is under (G0053). */
    private void standing(PlayerReadout player, int x, int row, Color alert) {
        wounds(player, x, row);
        int after = x + player.maxWounds * UiFont.ADVANCE;
        if (player.shielded) {
            after += UiFont.ADVANCE;
            hud.text(shieldText, after, row, palette.colorFor(Look.COMMANDER, player.variant));
            after += shieldText.length() * UiFont.ADVANCE;
        }
        if (player.stunned) {
            after += UiFont.ADVANCE;
            inverse(stunnedText, after, row, alert);
        }
    }

    /** Columns that takes. */
    private int standingColumns(PlayerReadout player) {
        int columns = player.maxWounds;
        if (player.shielded) {
            columns += 1 + shieldText.length();
        }
        if (player.stunned) {
            columns += 1 + stunnedText.length();
        }
        return columns;
    }

    /**
     * Row 1 while he stands: a narrow diamond per wound he can carry, filled in the wound colour for each
     * he is carrying and hollow for the rest (G0053) - the count is shown, never guessed (pillar 5).
     * Nothing at all for a man who can carry none.
     */
    private void wounds(PlayerReadout player, int x, int row) {
        for (int wound = 0; wound < player.maxWounds; wound++) {
            int left = x + wound * UiFont.ADVANCE;
            if (wound < player.wounds) {
                hud.icon(TallyIcon.WOUND, left, row, style.wound());
            } else {
                hud.icon(TallyIcon.WOUND_SPARE, left, row, style.textDim());
            }
        }
    }

    /**
     * The tell, lit so it is not overlooked: a dim backing in his colour, and his colour breathing
     * towards white over it. {@code swell} is where the pulse is, 0 to 1.
     */
    private void lit(String text, int x, int top, Color own, float swell) {
        float glow = style.tellGlow();
        tellBacking.set(own).mul(glow, glow, glow, 1f);
        hud.fill(
                x - 1,
                top - metrics.fontSize(),
                text.length() * UiFont.ADVANCE + 1,
                metrics.fontSize() + 1,
                tellBacking);
        tellLit.set(own).lerp(Color.WHITE, style.tellLift() * swell);
        tellLit.a = 1f;
        hud.text(text, x, top, tellLit);
    }

    /** The tell, formatted only when the count, the key or the room for it changes. */
    private String tell(int slot, int drafts, String key, int columns) {
        if (drafts != tellDrafts[slot] || columns != tellColumns[slot] || !key.equals(tellKey[slot])) {
            tellDrafts[slot] = drafts;
            tellColumns[slot] = columns;
            tellKey[slot] = key;
            tellText[slot] = tell.text(drafts, key, columns);
        }
        return tellText[slot];
    }

    private static int count(PlayerReadout player, int icon) {
        return switch (icon) {
            case 0 -> player.kills;
            case 1 -> player.goals;
            default -> player.stabilised;
        };
    }

    /**
     * Row 3: the tally, dim - a skull and his kills, a flag and his goals, a green plus and the men he
     * put back on their feet. Icons and numbers only; the words are the pause menu's (plan 7.2). Counts
     * are turned into text only when they change.
     */
    private void tally(int slot, PlayerReadout player, int x, int row) {
        int[] shown = tallyShown[slot];
        String[] text = tallyText[slot];
        int group = TallyIcon.SIZE + 2 + TALLY_COLUMNS * UiFont.ADVANCE + UiFont.ADVANCE;
        for (int i = 0; i < TALLY_ICONS.length; i++) {
            int now = count(player, i);
            if (shown[i] != now) {
                shown[i] = now;
                text[i] = TallyIcon.count(now);
            }
            TallyIcon icon = TALLY_ICONS[i];
            Color tint = icon == TallyIcon.STABILISED ? palette.colorFor(Look.MENDING) : style.textDim();
            hud.icon(icon, x + i * group, row, tint);
            hud.text(text[i], x + i * group + TallyIcon.SIZE + 2, row, style.textDim());
        }
    }

    /**
     * The roll call ({@code UI:hud/bottom/roll-call}): a compact row per player - colour, status, tally
     * - for games of more than four. A placeholder until its design is settled.
     */
    void renderRollCall(ScreenLayout layout) {
        Rect bottom = layout.bottomBar();
        if (bottom.isEmpty()) {
            return;
        }
        int padding = metrics.padding();
        hud.beginCanvas();
        hud.frame(
                layout.contentLeft() + 1,
                bottom.top() - metrics.bottomMin() + 1,
                layout.view().width() - 2,
                metrics.bottomMin() - 2,
                style.textDim());
        hud.text(rollCall, layout.contentLeft() + padding, bottom.top() - padding, style.textDim());
        hud.endCanvas();
    }

    /** KIA and the seconds to his return - or that there is nobody in reserve to return as (G0055). */
    private String respawnText(int slot, PlayerReadout player) {
        return player.respawnWaiting ? kiaWaiting : kiaText(slot, player.respawnSeconds);
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
        hud.fill(x - 1, top - metrics.fontSize(), text.length() * UiFont.ADVANCE + 1, metrics.fontSize() + 1, alert);
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
