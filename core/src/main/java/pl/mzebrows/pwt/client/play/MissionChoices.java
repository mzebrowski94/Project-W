package pl.mzebrows.pwt.client.play;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.MissionConfig;
import pl.mzebrows.pwt.sim.Strength;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.ui.nav.CodePicker;

/**
 * What the DAILY MISSIONS page decides (missions ticket 08, G0065): the day's three dailies, RANDOM's tier
 * and its draw, the code line and the picker. <b>Every entry writes into the code line and START MISSION
 * starts what it holds</b>: a daily its code, RANDOM a fresh draw, and the line itself whatever is typed,
 * pasted or picked over it. Kept for the whole run, so RANDOM's tier and the last code played outlive the
 * page; kept out of the screen so it is tested without a window.
 *
 * <p>The date is read when the page opens, in UTC, and kept until it opens again: a daily keeps the day it
 * was started on. <b>A code that fails its check starts nothing</b>, and is refused the moment its eighth
 * character lands; what is said about it never names the character that is wrong - showing the right check
 * would let a typo through.
 */
public final class MissionChoices {

    /** The words for a code that is none: a key in {@code i18n/ui.properties}. */
    public static final String REFUSED = "ui.missions.badCode";

    /** The words for a first character no tier has; {@code {0}} the tiers' letters. */
    public static final String FIRST_LETTER = "ui.missions.badTier";

    private static final int HALF = MissionCode.LENGTH / 2;

    private static final TierKind[] TIERS = TierKind.values();

    /** Where the code in the line came from, as the line says beside it. */
    public enum Origin {
        /** One of the three of the page's day, however it arrived (G0065). */
        DAILY,
        /** RANDOM's last draw, untouched. */
        RANDOM,
        /** Anything else - a code typed, pasted, picked or changed by hand. */
        CUSTOM
    }

    private final Clock clock;
    private final LongSupplier draws;
    private final StringBuilder typed = new StringBuilder();
    private final MissionConfig mission;
    private final CodePicker picker;
    private @Nullable LocalDate day;
    private TierKind randomTier = TierKind.MEDIUM;

    /** The missions page's length row: how many biomes RANDOM draws at, and what the line's code holds when it holds one. */
    private int length;

    private @Nullable MissionCode lastPlayed;

    /** The strength rows' values (G0106): beside the code, never in it. A daily is always at a hundred. */
    private int healthPercent = Strength.NORMAL.healthPercent();

    private int countPercent = Strength.NORMAL.countPercent();

    private Strength lastStrength = Strength.NORMAL;

    /**
     * CUSTOM is on (G0106): the length and the strength rows are the host's to change, on the code the line holds.
     * Session only - choosing a daily, drawing a RANDOM or changing the code any other way puts everything back.
     */
    private boolean custom;

    private boolean lastCustom;

    /** The line is being rewritten by CUSTOM itself, which keeps it on. */
    private boolean rewriting;

    /** RANDOM's last draw, or null before the first: the line holding it is RANDOM, not CUSTOM. */
    private @Nullable MissionCode drawn;

    private String problem = "";

    /**
     * @param clock where today is read, in UTC
     * @param draws the client's own randomness, for RANDOM's seeds - never the simulation's
     * @param mission the lengths a mission may be, and the default - a daily's - the length row starts at (G0102)
     */
    public MissionChoices(Clock clock, LongSupplier draws, MissionConfig mission) {
        this.clock = clock;
        this.draws = draws;
        this.mission = mission;
        this.length = mission.biomes();
        this.picker = new CodePicker(MissionCode.alphabets(mission));
    }

    /** The page opens from the menu: the date is read now, and the code line starts empty - nothing chosen. */
    public void open() {
        day = LocalDate.now(clock);
        length = mission.biomes();
        healthPercent = Strength.NORMAL.healthPercent();
        countPercent = Strength.NORMAL.countPercent();
        custom = false;
        typed.setLength(0);
        problem = "";
    }

    /**
     * The page opens again on the way back from a mission it started: the date is read now, and the line
     * holds what was played, so playing it again is START.
     */
    public void reopen() {
        open();
        MissionCode played = lastPlayed;
        if (played != null) {
            typed.append(played.text());
            length = played.length();
            healthPercent = lastStrength.healthPercent();
            countPercent = lastStrength.countPercent();
            custom = lastCustom;
        }
    }

    /** The day the page opened on - today, if it has not opened this run. */
    public LocalDate day() {
        LocalDate opened = day;
        return opened == null ? LocalDate.now(clock) : opened;
    }

    public MissionCode daily(TierKind tier) {
        return MissionCode.daily(day().toEpochDay(), tier, mission.biomes());
    }

    /**
     * The day {@code code} is the daily of, or null: one of the page's day's three, however it arrived -
     * else one of the day either side's, as a machine that joined someone else's daily finds it.
     */
    public @Nullable LocalDate dailyDay(MissionCode code) {
        return code.dailyNear(day(), mission.biomes());
    }

    public TierKind randomTier() {
        return randomTier;
    }

    /** RANDOM's tier, one along - left or right - and round. */
    public void stepRandomTier(int direction) {
        int at = 0;
        while (TIERS[at] != randomTier) {
            at++;
        }
        randomTier = TIERS[Math.floorMod(at + Integer.signum(direction), TIERS.length)];
    }

    /** How many biomes the length row shows, and RANDOM draws at (G0102). */
    public int length() {
        return length;
    }

    /**
     * The length row stepped, left or right and round. When the line holds a whole code it is written afresh at the
     * new length - the same tier and seed, another mission, so a daily becomes a custom one; else the row alone moves.
     */
    public void stepLength(int direction) {
        if (!custom) {
            return;
        }
        int span = mission.biomesMax() - mission.biomesMin() + 1;
        length = mission.biomesMin() + Math.floorMod(length - mission.biomesMin() + Integer.signum(direction), span);
        MissionCode held = code();
        if (held != null) {
            rewriting = true;
            replaceTyped(new MissionCode(held.tier(), length, held.seed()).text());
            rewriting = false;
        }
    }

    /** Whether CUSTOM is on: the length and strength rows may be changed. */
    public boolean custom() {
        return custom;
    }

    /**
     * CUSTOM pressed (G0106): off to on or on to off, starting off. On, the mission the line holds - else the last one
     * played, else a fresh draw - becomes the one the host edits: its length and strength rows may be changed, the
     * strength starting at a hundred per cent. Off, the length and the strength go back to their defaults - the code
     * the line holds rewritten at the default length, its tier and seed kept - and the rows are locked again.
     */
    public void toggleCustom() {
        if (custom) {
            custom = false;
            healthPercent = Strength.NORMAL.healthPercent();
            countPercent = Strength.NORMAL.countPercent();
            length = mission.biomes();
            MissionCode held = code();
            if (held != null && held.length() != length) {
                rewriting = true;
                replaceTyped(new MissionCode(held.tier(), length, held.seed()).text());
                rewriting = false;
            }
            return;
        }
        MissionCode base = code();
        if (base == null) {
            base = lastPlayed != null ? lastPlayed : random();
        }
        rewriting = true;
        replaceTyped(base.text());
        rewriting = false;
        custom = true;
        healthPercent = Strength.NORMAL.healthPercent();
        countPercent = Strength.NORMAL.countPercent();
    }

    /** The tier the rows speak of: the line's code's, else RANDOM's. */
    public TierKind shownTier() {
        MissionCode held = code();
        return held == null ? randomTier : held.tier();
    }

    /**
     * The enemy health the row shows: the tier's own percentage while CUSTOM is off - easy 85, hard 115 - and, once it
     * is on, the strength the host has stepped it to, which starts at a hundred and is laid on the tier's.
     */
    public int shownHealth() {
        return custom ? healthPercent : mission.healthPercentOf(shownTier(), Strength.NORMAL);
    }

    /** The enemy count the row shows, the same way. */
    public int shownCount() {
        return custom ? countPercent : mission.countPercentOf(shownTier(), Strength.NORMAL);
    }

    /** The strength the rows show: what a mission started now is played with (G0106). */
    public Strength strength() {
        return new Strength(healthPercent, countPercent);
    }

    /** The enemy health row stepped, left or right and round the strength range (G0106). */
    public void stepHealth(int direction) {
        if (!custom) {
            return;
        }
        healthPercent = stepped(healthPercent, direction);
    }

    /** The enemy count row stepped, left or right and round the strength range (G0106). */
    public void stepCount(int direction) {
        if (!custom) {
            return;
        }
        countPercent = stepped(countPercent, direction);
    }

    private int stepped(int percent, int direction) {
        int step = mission.strengthStepPercent();
        int places = (mission.strengthMaxPercent() - mission.strengthMinPercent()) / step + 1;
        int at = (percent - mission.strengthMinPercent()) / step;
        return mission.strengthMinPercent() + Math.floorMod(at + Integer.signum(direction), places) * step;
    }

    /** What the host tells the session the briefing is of: the code's line, and the strength after it when not normal. */
    public String note() {
        Strength held = strength();
        if (held.normal()) {
            return line();
        }
        return line() + " h" + held.healthPercent() + " c" + held.countPercent();
    }

    /** A mission on RANDOM's tier at the row's length, its seed drawn afresh: a new one each time. */
    public MissionCode random() {
        return new MissionCode(randomTier, mission.biomes(), Math.floorMod(draws.getAsLong(), MissionCode.SEEDS));
    }

    /** A daily chosen: its code in the line, in place of what was there, and the length row at its default (G0102). */
    public void chooseDaily(TierKind tier) {
        replaceTyped(daily(tier).text());
    }

    /** RANDOM chosen: a fresh draw on its tier in the line, in place of what was there. */
    public void drawRandom() {
        MissionCode fresh = random();
        drawn = fresh;
        replaceTyped(fresh.text());
    }

    /**
     * One typed character, into the next of a code's eight places - over a whole line, the first of a new code.
     * Backspace deletes; a letter or a digit is kept, in capitals - but in the first place only a tier's letter,
     * any other refused with {@link #FIRST_LETTER} and the line kept. The dash is the line's own and a space is read past, so neither is kept; Enter arrives
     * typed as well, and is no character of a code.
     *
     * @return whether the key was for the line - kept or refused - and so takes the selection there
     */
    public boolean type(char c) {
        if (c == '\b') {
            if (typed.isEmpty()) {
                return false;
            }
            typed.setLength(typed.length() - 1);
            check();
            return true;
        }
        if (!codey(c)) {
            return false;
        }
        char upper = Character.toUpperCase(c);
        boolean starts = typed.isEmpty() || typed.length() == MissionCode.LENGTH;
        if (starts && MissionCode.tierOf(upper) == null) {
            problem = FIRST_LETTER;
            return true;
        }
        if (typed.length() == MissionCode.LENGTH) {
            typed.setLength(0);
        }
        if (typed.length() < MissionCode.LENGTH) {
            typed.append(upper);
            check();
        }
        return true;
    }

    /**
     * What the clipboard held, as a whole code in place of the line - what is not a letter or a digit read
     * past. One that does not start with a tier's letter is refused with {@link #FIRST_LETTER} and the line kept.
     */
    public void paste(String text) {
        String letters = letters(text);
        if (letters.isEmpty()) {
            return;
        }
        if (MissionCode.tierOf(letters.charAt(0)) == null) {
            problem = FIRST_LETTER;
            return;
        }
        replaceTyped(letters);
    }

    /** Replaces the code line - with what the picker shows, a daily, a draw - whose first place is a tier's. */
    public void replaceTyped(String text) {
        if (!rewriting) {
            custom = false;
            healthPercent = Strength.NORMAL.healthPercent();
            countPercent = Strength.NORMAL.countPercent();
        }
        String letters = letters(text);
        typed.setLength(0);
        typed.append(letters, 0, Math.min(letters.length(), MissionCode.LENGTH));
        check();
    }

    /** The code's characters typed so far, in capitals, with no dash. */
    public String typed() {
        return typed.toString();
    }

    /**
     * The line as shown: a code's eight places in two halves, {@code MJGF-6___} - the shape there before the
     * first key, the dash never typed.
     */
    public String line() {
        StringBuilder line = new StringBuilder(MissionCode.LENGTH + 1);
        for (int i = 0; i < MissionCode.LENGTH; i++) {
            if (i == HALF) {
                line.append('-');
            }
            line.append(i < typed.length() ? typed.charAt(i) : '_');
        }
        return line.toString();
    }

    /** The tiers' letters, for {@link #FIRST_LETTER}'s words: {@code E/M/H}. */
    public static String tierLetters() {
        TierKind[] tiers = TIERS;
        StringBuilder text = new StringBuilder(2 * tiers.length);
        for (int i = 0; i < tiers.length; i++) {
            text.append(i == 0 ? "" : "/").append(MissionCode.letter(tiers[i]));
        }
        return text.toString();
    }

    /**
     * What is wrong, as a key: {@link #REFUSED} while the line holds a whole code that fails its check, {@link
     * #FIRST_LETTER} for the last first letter - or nothing.
     */
    public String problem() {
        return problem;
    }

    /** The mission the code line names, or null while it is not whole or fails its check. */
    public @Nullable MissionCode code() {
        if (typed.length() != MissionCode.LENGTH) {
            return null;
        }
        MissionCode parsed = MissionCode.parse(typed.toString());
        return parsed != null && mission.hasLength(parsed.length()) ? parsed : null;
    }

    /** Where the line's code came from, or null while the line names no mission. */
    public @Nullable Origin origin() {
        MissionCode code = code();
        if (code == null) {
            return null;
        }
        if (code.equals(daily(code.tier()))) {
            return Origin.DAILY;
        }
        return code.equals(drawn) ? Origin.RANDOM : Origin.CUSTOM;
    }

    /**
     * The picker, opened on what the line holds - the places not typed yet at their alphabet's first - else, on
     * an empty line, the last code played this run, else today's MEDIUM daily: a code close to a known one is a
     * few presses away.
     */
    public CodePicker openPicker() {
        if (!typed.isEmpty()) {
            StringBuilder shown = new StringBuilder(typed);
            while (shown.length() < MissionCode.LENGTH) {
                shown.append('_');
            }
            picker.show(shown.toString());
            return picker;
        }
        MissionCode start = lastPlayed;
        picker.show((start == null ? daily(TierKind.MEDIUM) : start).text());
        return picker;
    }

    /** The line was changed: a whole code that fails its check is refused at once; anything else is not. */
    private void check() {
        MissionCode held = code();
        problem = typed.length() == MissionCode.LENGTH && held == null ? REFUSED : "";
        if (held != null) {
            length = held.length();
        }
    }

    private static boolean codey(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /** The letters and digits of {@code text}, in capitals. */
    private static String letters(String text) {
        StringBuilder letters = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            if (codey(text.charAt(i))) {
                letters.append(Character.toUpperCase(text.charAt(i)));
            }
        }
        return letters.toString();
    }

    /** A mission was started from the page, however it was chosen. */
    public void played(MissionCode code) {
        lastPlayed = code;
        lastStrength = strength();
        lastCustom = custom;
    }
}
