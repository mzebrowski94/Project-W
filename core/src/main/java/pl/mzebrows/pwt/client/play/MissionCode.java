package pl.mzebrows.pwt.client.play;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * What names a mission (G0065): eight characters of the room code's alphabet, shown {@code HK3X-Q7RA} - the
 * tier's letter, six characters of mission seed in base 25, and one that checks the seven before it. The
 * mission seed is the simulation's seed; the tier moves the swarm's numbers and never the map.
 *
 * <p>The check is a weighted sum mod 25, with weights 1 2 3 4 6 7 8 - none divisible by 5, nor the difference
 * between any two neighbours - so every single wrong character and every swapped neighbour pair fails it. A
 * code that fails it is refused, never read as the mission it was probably meant to be.
 *
 * <p>A <b>daily mission</b> is a code like any other, its seed a pure function of the UTC day and the tier
 * ({@link #daily}): every machine deals the same three, and they differ by tier.
 *
 * @param tier how hard the mission is; its letter is the first of its name
 * @param seed the mission seed, in {@code [0, SEEDS)}
 */
public record MissionCode(TierKind tier, long seed) {

    /** Characters in a code, the tier's and the check's included. */
    public static final int LENGTH = 8;

    private static final int SEED_LENGTH = LENGTH - 2;

    private static final int BASE = RoomCode.ALPHABET.length();

    /** How many mission seeds there are a tier: six characters' worth. */
    public static final long SEEDS = seeds();

    private static final int[] WEIGHTS = {1, 2, 3, 4, 6, 7, 8};

    private static final int HALF = LENGTH / 2;

    /** How far either side of a machine's own day a daily is looked for: a joiner's day may be the host's neighbour. */
    private static final int DAYS_EITHER_SIDE = 1;

    public MissionCode {
        if (seed < 0 || seed >= SEEDS) {
            throw new IllegalArgumentException("a mission seed is 0 to " + (SEEDS - 1) + ", not " + seed);
        }
    }

    /**
     * What a player typed, as a mission - any case, spaces and dashes ignored - or null if it is none: the
     * wrong length, a character the alphabet lacks, a letter no tier has, or a check that fails.
     */
    public static @Nullable MissionCode parse(String typed) {
        String letters = RoomCode.letters(typed, LENGTH);
        if (letters == null || check(letters) != letters.charAt(LENGTH - 1)) {
            return null;
        }
        long seed = 0L;
        for (int i = 1; i <= SEED_LENGTH; i++) {
            seed = seed * BASE + RoomCode.ALPHABET.indexOf(letters.charAt(i));
        }
        TierKind tier = tierOf(letters.charAt(0));
        return tier == null ? null : new MissionCode(tier, seed);
    }

    /**
     * The day's daily on this tier (G0065): the day mixed with the tier's letter, the way a stream is derived
     * from a seed, onto a mission seed. The same on every machine and every run.
     *
     * @param epochDay the UTC day, as {@link LocalDate#toEpochDay()}
     */
    public static MissionCode daily(long epochDay, TierKind tier) {
        return new MissionCode(
                tier, Math.floorMod(new Rng(epochDay).derive(letter(tier)).nextLong(), SEEDS));
    }

    /**
     * The mission a session plays, from what it names and its seed: a mission when the scenario is a tier's
     * ({@code mission <tier>}), null for any other scenario.
     */
    public static @Nullable MissionCode of(String scenarioName, long seed) {
        if (seed < 0 || seed >= SEEDS) {
            return null;
        }
        for (TierKind tier : TierKind.values()) {
            if (Scenarios.mission(tier).name().equals(scenarioName)) {
                return new MissionCode(tier, seed);
            }
        }
        return null;
    }

    /** The tier whose letter this is in a code, or null if no tier's. */
    public static @Nullable TierKind tierOf(char letter) {
        for (TierKind tier : TierKind.values()) {
            if (letter(tier) == letter) {
                return tier;
            }
        }
        return null;
    }

    /**
     * What each character of a code may be, slot by slot, for a picker: the tiers' letters first, then the
     * room code's alphabet - so a picked code is always of some tier.
     */
    public static List<String> alphabets() {
        StringBuilder tiers = new StringBuilder();
        for (TierKind tier : TierKind.values()) {
            tiers.append(letter(tier));
        }
        List<String> alphabets = new ArrayList<>(LENGTH);
        alphabets.add(tiers.toString());
        for (int i = 1; i < LENGTH; i++) {
            alphabets.add(RoomCode.ALPHABET);
        }
        return List.copyOf(alphabets);
    }

    /**
     * The day this is the daily of, looked for on {@code around} first and then the day either side - or
     * null if it is no daily of those. Where a daily was started, {@code around} is the day its page opened.
     */
    public @Nullable LocalDate dailyNear(LocalDate around) {
        for (int i = 0; i <= 2 * DAYS_EITHER_SIDE; i++) {
            // 0, -1, +1: the day itself before its neighbours.
            int offset = (i + 1) / 2 * (i % 2 == 1 ? -1 : 1);
            LocalDate day = around.plusDays(offset);
            if (equals(daily(day.toEpochDay(), tier))) {
                return day;
            }
        }
        return null;
    }

    /** What a session plays this mission as: {@code mission <tier>}, built from {@link #seed} as its seed (G0065). */
    public Scenario scenario() {
        return Scenarios.mission(tier);
    }

    /** The eight characters, with no dash. */
    public String text() {
        char[] code = new char[LENGTH];
        code[0] = letter(tier);
        long rest = seed;
        for (int i = SEED_LENGTH; i >= 1; i--) {
            code[i] = RoomCode.ALPHABET.charAt((int) (rest % BASE));
            rest /= BASE;
        }
        code[LENGTH - 1] = check(new String(code));
        return new String(code);
    }

    /** As it is shown and read out: {@code HK3X-Q7RA}. */
    @Override
    public String toString() {
        String text = text();
        return text.substring(0, HALF) + "-" + text.substring(HALF);
    }

    /** The tier's letter, first in its codes and in its records' keys. */
    static char letter(TierKind tier) {
        return tier.name().charAt(0);
    }

    /** The check character over the first seven of {@code code}. */
    private static char check(String code) {
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += WEIGHTS[i] * RoomCode.ALPHABET.indexOf(code.charAt(i));
        }
        return RoomCode.ALPHABET.charAt(sum % BASE);
    }

    private static long seeds() {
        long seeds = 1L;
        for (int i = 0; i < SEED_LENGTH; i++) {
            seeds *= BASE;
        }
        return seeds;
    }
}
