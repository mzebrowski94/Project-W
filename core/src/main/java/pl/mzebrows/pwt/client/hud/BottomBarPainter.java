package pl.mzebrows.pwt.client.hud;

import com.badlogic.gdx.graphics.Color;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.draw.Palette;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.ui.hud.PanelAlarms;
import pl.mzebrows.pwt.ui.hud.PanelAssignment;
import pl.mzebrows.pwt.ui.hud.PanelLayout;
import pl.mzebrows.pwt.ui.hud.PanelStatus;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;
import pl.mzebrows.pwt.ui.hud.Posture;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.hud.TallyIcon;
import pl.mzebrows.pwt.ui.hud.TextFit;
import pl.mzebrows.pwt.ui.hud.UpgradeTell;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;

/**
 * The bottom bar ({@code UI:hud/bottom/...}): four fixed quarters, each holding one player's panel -
 * frame and label in his colour, his status, his gun and its heat with his grenades at the row's end (G0077), a
 * block where his portrait will be, and a row kept for his tally. Who is in which quarter is {@link PanelAssignment}'s: everybody while
 * there are four or fewer, this machine's seats otherwise. A quarter nobody holds stays black; a player
 * whose pad or link is gone is drawn grey and says OFFLINE.
 *
 * <p>Status ({@link PanelStatus}) is empty while he stands: quiet until it matters, and always in the same place
 * when it does. DOWN, REDEPLOY, PINNED and OVERHEAT are the only red, drawn as black text on it (plan 7.0); STUNNED
 * is plain, with what is left of the stun as a bar in the neutral text colour. While his slot
 * holds upgrade drafts, the tell ({@code UI:hud/bottom/upgrade}) says so at the row's right end, in his
 * colour, with the key that opens them.
 */
public final class BottomBarPainter {

    private static final int HEAT_BAR_WIDTH = 68;

    private static final float DIM_ALERT = 0.55f;

    /** The tally row's marks, in order. */
    private static final TallyIcon[] TALLY_ICONS = {TallyIcon.KILLS, TallyIcon.GOALS, TallyIcon.STABILISED};

    /** Characters a count takes at most: {@code 999+}. */
    private static final int TALLY_COLUMNS = 4;

    private final Hud hud;
    private final GraphicConfig graphic;
    private final HudMetrics metrics;
    private final Palette palette;
    private final PanelLayout panels;
    private final String[] labels = new String[Players.MAX];
    private final Map<WeaponKind, String> weapons = new EnumMap<>(WeaponKind.class);
    private final String shieldText;
    private final String stunnedText;
    private final String down;
    private final String pinned;
    private final String redeployTemplate;
    private final String noReserve;
    private final String recall;
    private final String aboard;
    private final String overheat;
    private final String detonate;
    private final String offline;
    private final String rollCall;
    private final String[] redeploy = new String[Players.MAX];
    private final int[] redeployTenths = new int[Players.MAX];
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

    public BottomBarPainter(Hud hud, UiStrings strings, GraphicConfig graphic, HudMetrics metrics, Palette palette) {
        this.hud = hud;
        this.graphic = graphic;
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
            redeploy[slot] = "";
            redeployTenths[slot] = -1;
            Arrays.fill(tallyShown[slot], -1);
            Arrays.fill(tallyText[slot], "");
        }
        this.down = strings.get("ui.hud.down");
        this.shieldText = strings.get("ui.hud.shield");
        this.stunnedText = strings.get("ui.hud.stunned");
        this.pinned = strings.get("ui.hud.pinned");
        this.redeployTemplate = strings.get("ui.hud.redeploy");
        this.noReserve = strings.get("ui.hud.noReserve");
        this.recall = strings.get("ui.hud.recall");
        this.aboard = strings.get("ui.hud.aboard");
        this.overheat = strings.get("ui.hud.overheat");
        this.detonate = strings.get("ui.hud.detonate");
        this.offline = strings.get("ui.hud.offline");
        this.rollCall = strings.get("ui.hud.rollCall");
        this.dimAlert = new Color(graphic.hud().color().alert()).mul(DIM_ALERT, DIM_ALERT, DIM_ALERT, 1f);
        this.tell = new UpgradeTell(
                strings.get("ui.hud.upgrade"), strings.get("ui.hud.upgrades"), strings.get("ui.hud.upgradeBrief"));
        Arrays.fill(tellText, "");
        Arrays.fill(tellKey, "");
        Arrays.fill(tellDrafts, -1);
    }

    /**
     * Paints the bottom bar, a panel for each player.
     *
     * @param connected by slot: false for a player whose pad or link is gone, whose panel is greyed
     * @param draftKeys by slot: what that player presses to open his drafts, or empty when he is not
     *     played here
     */
    public void render(
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
        Color own = connected
                ? palette.colorFor(Look.COMMANDER, player.variant)
                : graphic.hud().color().textDim();
        Color text =
                connected ? graphic.hud().color().text() : graphic.hud().color().textDim();
        int padding = metrics.padding();
        int height = metrics.bottomMin();
        int line = metrics.fontSize() + metrics.leading();
        hud.frame(
                x + 1,
                top - height + 1,
                width - 2,
                height - 2,
                alarms.flashing(slot) ? graphic.hud().color().alert() : own);
        if (panels.showsPortrait(width)) {
            int portrait = metrics.portraitSize();
            hud.fill(x + padding, top - padding - portrait, portrait, portrait, own);
        }
        int textX = x + panels.textOffset(width);
        int textRight = x + width - padding;
        Color alert = alarms.alertBright() ? graphic.hud().color().alert() : dimAlert;

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
            lit(
                    upgrade,
                    tellX,
                    row,
                    own,
                    connected ? alarms.swell(graphic.hud().upgrade().pulseHz()) : 0f);
        }
        int barRight = upgrade.isEmpty() ? textRight : tellX - UiFont.ADVANCE;
        PanelStatus status = PanelStatus.of(player);
        switch (status) {
            case REDEPLOY -> word(status, redeployText(slot, player), statusX, row, alert, text);
            case DOWN -> {
                word(status, down, statusX, row, alert, text);
                barAfter(
                        down,
                        statusX,
                        row,
                        barRight,
                        player.bleedOut,
                        graphic.hud().color().alert());
            }
            case PINNED -> word(status, pinned, statusX, row, alert, text);
            case STUNNED -> {
                if (!connected) {
                    hud.text(offline, statusX, row, text);
                } else {
                    word(status, stunnedText, statusX, row, alert, text);
                    barAfter(stunnedText, statusX, row, barRight, player.stunLeft, text);
                }
            }
            case ABOARD -> hud.text(connected ? aboard : offline, statusX, row, text);
            case NONE -> {
                if (!connected) {
                    hud.text(offline, statusX, row, text);
                } else if (player.posture == Posture.STANDING) {
                    standing(player, statusX, row);
                }
            }
        }

        row -= line;
        if (player.posture == Posture.STANDING || player.posture == Posture.ABOARD) {
            hud.text(weapons.getOrDefault(player.weapon, ""), textX, row, text);
            int heatX = textX + (weaponColumns + 1) * UiFont.ADVANCE;
            int heatRight = grenades(player, textRight, row, palette.colorFor(Look.GRENADE));
            if (player.detonating) {
                // His next press sets his limpet off (G0100): the word, on the heat's colour - news, not an alarm.
                inverse(
                        TextFit.fit(detonate, TextFit.columns(heatRight - heatX, UiFont.ADVANCE)),
                        heatX,
                        row,
                        palette.colorFor(Look.HEAT));
            } else if (player.overheated) {
                inverse(TextFit.fit(overheat, TextFit.columns(heatRight - heatX, UiFont.ADVANCE)), heatX, row, alert);
            } else {
                bar(
                        heatX,
                        row,
                        Math.max(0, Math.min(HEAT_BAR_WIDTH, heatRight - heatX)),
                        player.heat,
                        palette.colorFor(Look.HEAT));
            }
        } else if (player.posture == Posture.CASUALTY) {
            hud.text(
                    weapons.getOrDefault(player.weapon, ""),
                    textX,
                    row,
                    graphic.hud().color().textDim());
            // Down keeps them (G0077), so they are still shown - dim, as his gun is.
            grenades(player, textRight, row, graphic.hud().color().textDim());
        }

        row -= line;
        if (player.posture != Posture.ABSENT) {
            tally(slot, player, textX, row);
        }
    }

    /** Columns row 1's status takes after the label, a bar's least width included. */
    private int statusColumns(int slot, PlayerReadout player, boolean connected) {
        return switch (PanelStatus.of(player)) {
            case REDEPLOY -> redeployText(slot, player).length();
            case DOWN -> down.length() + 1 + PanelLayout.LEAST_BAR_COLUMNS;
            case PINNED -> pinned.length();
            case STUNNED -> connected ? stunnedText.length() + 1 + PanelLayout.LEAST_BAR_COLUMNS : offline.length();
            case ABOARD -> connected ? aboard.length() : offline.length();
            case NONE -> connected ? standingColumns(player) : offline.length();
        };
    }

    /** Row 1 while he stands free: his wounds, then his dashes (G0107), then a shield in front of him (G0053). */
    private void standing(PlayerReadout player, int x, int row) {
        wounds(player, x, row);
        int after = x + player.maxWounds * UiFont.ADVANCE;
        if (player.dashMost > 0) {
            after += UiFont.ADVANCE;
            pips(
                    player.dashes,
                    player.dashMost,
                    after,
                    row,
                    TallyIcon.DASH,
                    TallyIcon.DASH_SPARE,
                    graphic.hud().color().dash());
            after += player.dashMost * UiFont.ADVANCE;
        }
        if (player.shielded) {
            after += UiFont.ADVANCE;
            hud.text(shieldText, after, row, palette.colorFor(Look.COMMANDER, player.variant));
        }
    }

    /** Columns that takes. */
    private int standingColumns(PlayerReadout player) {
        int columns = player.maxWounds;
        if (player.dashMost > 0) {
            columns += 1 + player.dashMost;
        }
        if (player.shielded) {
            columns += 1 + shieldText.length();
        }
        return columns;
    }

    /**
     * Row 2's right end, ending at {@code right}: a grenade per one he can carry, filled for each he has and hollow for
     * the rest (G0077) - his count is shown, never remembered (pillar 5). Nothing for a man whose throws are not
     * counted.
     *
     * @return where what goes before them on the row must end
     */
    private int grenades(PlayerReadout player, int right, int row, Color color) {
        if (player.grenadeMost <= 0) {
            return right;
        }
        int x = right - player.grenadeMost * UiFont.ADVANCE;
        pips(player.grenades, player.grenadeMost, x, row, TallyIcon.GRENADE, TallyIcon.GRENADE_SPARE, color);
        return x - UiFont.ADVANCE;
    }

    /** A row of {@code most} icons from {@code x}, the first {@code filled} lit in {@code color} and the rest hollow and dim. */
    private void pips(int filled, int most, int x, int row, TallyIcon lit, TallyIcon spare, Color color) {
        for (int i = 0; i < most; i++) {
            int left = x + i * UiFont.ADVANCE;
            if (i < filled) {
                hud.icon(lit, left, row, color);
            } else {
                hud.icon(spare, left, row, graphic.hud().color().textDim());
            }
        }
    }

    /**
     * Row 1 while he stands: a narrow diamond per wound he can carry, filled in the wound colour for each
     * he is carrying and hollow for the rest (G0053) - the count is shown, never guessed (pillar 5).
     * Nothing at all for a man who can carry none.
     */
    private void wounds(PlayerReadout player, int x, int row) {
        pips(
                player.wounds,
                player.maxWounds,
                x,
                row,
                TallyIcon.WOUND,
                TallyIcon.WOUND_SPARE,
                graphic.hud().color().wound());
    }

    /**
     * The tell, lit so it is not overlooked: a dim backing in his colour, and his colour breathing
     * towards white over it. {@code swell} is where the pulse is, 0 to 1.
     */
    private void lit(String text, int x, int top, Color own, float swell) {
        float glow = graphic.hud().upgrade().glow();
        tellBacking.set(own).mul(glow, glow, glow, 1f);
        hud.fill(
                x - 1,
                top - metrics.fontSize(),
                text.length() * UiFont.ADVANCE + 1,
                metrics.fontSize() + 1,
                tellBacking);
        tellLit.set(own).lerp(Color.WHITE, graphic.hud().upgrade().lift() * swell);
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
            Color tint = icon == TallyIcon.STABILISED
                    ? palette.colorFor(Look.MENDING)
                    : graphic.hud().color().textDim();
            hud.icon(icon, x + i * group, row, tint);
            hud.text(
                    text[i],
                    x + i * group + TallyIcon.SIZE + 2,
                    row,
                    graphic.hud().color().textDim());
        }
    }

    /**
     * The roll call ({@code UI:hud/bottom/roll-call}): a compact row per player - colour, status, tally
     * - for games of more than four. A placeholder until its design is settled.
     */
    public void renderRollCall(ScreenLayout layout) {
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
                graphic.hud().color().textDim());
        hud.text(
                rollCall,
                layout.contentLeft() + padding,
                bottom.top() - padding,
                graphic.hud().color().textDim());
        hud.endCanvas();
    }

    /**
     * {@code REDEPLOY} alone while he is dying, with the seconds to his return once he is gone - or that there is
     * nobody in reserve to return as (G0055), or that a trooper was recalled to bring him back (G0121).
     */
    private String redeployText(int slot, PlayerReadout player) {
        if (player.posture == Posture.RESPAWNING && player.respawnWaiting) {
            return noReserve;
        }
        if (player.posture == Posture.RESPAWNING && player.respawnRecalled) {
            return recall;
        }
        return redeployIn(slot, player.posture == Posture.RESPAWNING ? player.respawnSeconds : -1f);
    }

    /** {@code REDEPLOY} and these seconds - none when negative - formatted only when the tenth changes. */
    private String redeployIn(int slot, float seconds) {
        int tenths = seconds < 0f ? -1 : (int) Math.ceil(seconds * 10f);
        if (tenths != redeployTenths[slot] || redeploy[slot].isEmpty()) {
            redeployTenths[slot] = tenths;
            redeploy[slot] = tenths < 0
                    ? redeployTemplate.replace("{0}", "").trim()
                    : redeployTemplate.replace("{0}", String.format(Locale.ROOT, "%.1f", tenths / 10f));
        }
        return redeploy[slot];
    }

    /** A status's word: in alert when the status is one ({@link PanelStatus#alert()}), plain otherwise. */
    private void word(PanelStatus status, String word, int x, int top, Color alert, Color plain) {
        if (status.alert()) {
            inverse(word, x, top, alert);
        } else {
            hud.text(word, x, top, plain);
        }
    }

    /** A status's draining bar, after its word and up to {@code right}. */
    private void barAfter(String word, int x, int top, int right, float fill, Color color) {
        int barX = x + (word.length() + 1) * UiFont.ADVANCE;
        bar(barX, top, right - barX, fill, color);
    }

    /** Black text on a colour: the alert's - the only way alert red is ever drawn - or the heat's, for DETONATE. */
    private void inverse(String text, int x, int top, Color fill) {
        hud.fill(x - 1, top - metrics.fontSize(), text.length() * UiFont.ADVANCE + 1, metrics.fontSize() + 1, fill);
        hud.text(text, x, top, graphic.hud().color().bar());
    }

    /** A bar the height of a capital, filled from the left: heat, or a bleed-out running down. */
    private void bar(int x, int top, int width, float fill, Color color) {
        if (width < 3) {
            return;
        }
        int height = metrics.fontSize() - 2;
        hud.frame(x, top - height, width, height, graphic.hud().color().textDim());
        float clamped = Math.max(0f, Math.min(1f, fill));
        hud.fill(x + 1, top - height + 1, Math.round((width - 2) * clamped), height - 2, color);
    }
}
