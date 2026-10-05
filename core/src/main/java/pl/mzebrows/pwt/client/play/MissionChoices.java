package pl.mzebrows.pwt.client.play;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.ui.nav.CodePicker;

/**
 * What the DAILY MISSIONS page decides (missions ticket 08, G0065): the day's three dailies, RANDOM's tier
 * and its draw, the code line and the picker - which mission each entry starts. Kept for the whole run, so
 * RANDOM's tier and the last code played outlive the page; kept out of the screen so it is tested without a
 * window.
 *
 * <p>The date is read when the page opens, in UTC, and kept until it opens again: a daily keeps the day it
 * was started on. <b>A code that fails its check starts nothing</b>, and what is said about it never names
 * the character that is wrong - showing the right check would let a typo through.
 */
public final class MissionChoices {

    /** The words for a code that is none: a key in {@code i18n/ui.properties}. */
    public static final String REFUSED = "ui.missions.badCode";

    /** The words for a first character no tier has; {@code {0}} the tiers' letters. */
    public static final String FIRST_LETTER = "ui.missions.badTier";

    private static final int HALF = MissionCode.LENGTH / 2;

    private static final TierKind[] TIERS = TierKind.values();

    private final Clock clock;
    private final LongSupplier draws;
    private final StringBuilder typed = new StringBuilder();
    private final CodePicker picker = new CodePicker(MissionCode.alphabets());
    private @Nullable LocalDate day;
    private TierKind randomTier = TierKind.MEDIUM;
    private @Nullable MissionCode lastPlayed;
    private String problem = "";

    /**
     * @param clock where today is read, in UTC
     * @param draws the client's own randomness, for RANDOM's seeds - never the simulation's
     */
    public MissionChoices(Clock clock, LongSupplier draws) {
        this.clock = clock;
        this.draws = draws;
    }

    /** The page opens: the date is read now, and the code line starts empty. */
    public void open() {
        day = LocalDate.now(clock);
        typed.setLength(0);
        problem = "";
    }

    /** The day the page opened on - today, if it has not opened this run. */
    public LocalDate day() {
        LocalDate opened = day;
        return opened == null ? LocalDate.now(clock) : opened;
    }

    public MissionCode daily(TierKind tier) {
        return MissionCode.daily(day().toEpochDay(), tier);
    }

    /**
     * The day {@code code} is the daily of, or null: one of the page's day's three, however it arrived -
     * else one of the day either side's, as a machine that joined someone else's daily finds it.
     */
    public @Nullable LocalDate dailyDay(MissionCode code) {
        return code.dailyNear(day());
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

    /** A mission on RANDOM's tier, its seed drawn afresh: a new one each time. */
    public MissionCode random() {
        return new MissionCode(randomTier, Math.floorMod(draws.getAsLong(), MissionCode.SEEDS));
    }

    /**
     * One typed character, into the next of a code's eight places. Backspace deletes; a letter or a digit is
     * kept, in capitals - but in the first place only a tier's letter, any other refused with {@link
     * #FIRST_LETTER}. The dash is the line's own and a space is read past, so neither is kept; Enter arrives
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
            problem = "";
            return true;
        }
        if (!codey(c)) {
            return false;
        }
        char upper = Character.toUpperCase(c);
        if (typed.isEmpty() && MissionCode.tierOf(upper) == null) {
            problem = FIRST_LETTER;
            return true;
        }
        if (typed.length() < MissionCode.LENGTH) {
            typed.append(upper);
            problem = "";
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
        typed.setLength(0);
        typed.append(letters, 0, Math.min(letters.length(), MissionCode.LENGTH));
        problem = "";
    }

    /** Replaces the code line with what the picker shows, whose first place is always a tier's. */
    public void replaceTyped(String text) {
        String letters = letters(text);
        typed.setLength(0);
        typed.append(letters, 0, Math.min(letters.length(), MissionCode.LENGTH));
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
        String letters = MissionCode.alphabets().getFirst();
        StringBuilder text = new StringBuilder(2 * letters.length());
        for (int i = 0; i < letters.length(); i++) {
            text.append(i == 0 ? "" : "/").append(letters.charAt(i));
        }
        return text.toString();
    }

    /**
     * What is wrong, as a key: {@link #REFUSED} for the last {@link #choose}, {@link #FIRST_LETTER} for the last
     * first letter - or nothing.
     */
    public String problem() {
        return problem;
    }

    /** The mission the code line names, or null - with {@link #problem()} saying so when something was typed. */
    public @Nullable MissionCode choose() {
        if (typed.toString().isBlank()) {
            return null;
        }
        MissionCode code = MissionCode.parse(typed.toString());
        problem = code == null ? REFUSED : "";
        return code;
    }

    /**
     * The pad's picker, opened on the last code played this run, else today's MEDIUM daily - so a code close
     * to a known one is a few presses away.
     */
    public CodePicker openPicker() {
        MissionCode start = lastPlayed;
        picker.show((start == null ? daily(TierKind.MEDIUM) : start).text());
        return picker;
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
    }
}
