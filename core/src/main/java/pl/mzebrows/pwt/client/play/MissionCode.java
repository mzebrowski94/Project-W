package pl.mzebrows.pwt.client.play;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.sim.MissionConfig;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * What names a mission (G0065, G0102): eight characters of the room code's alphabet, shown {@code HK3X-Q7RA} - the
 * tier's letter, the mission's length (the alphabet's place of that number), five characters of mission seed in base
 * 25, and one that checks the seven before it. The mission seed is the simulation's seed; the tier moves the swarm's
 * numbers and never the map; the length is how many biomes it marches through.
 *
 * <p>Whether a length is one a mission may be is the config's to say ({@code MissionConfig.hasLength}), not the
 * code's: a code that reads here is refused where the config is known.
 *
 * <p>The check is a weighted sum mod 25, with weights 1 2 3 4 6 7 8 - none divisible by 5, nor the difference
 * between any two neighbours - so every single wrong character and every swapped neighbour pair fails it. A
 * code that fails it is refused, never read as the mission it was probably meant to be.
 *
 * <p>A <b>daily mission</b> is a code like any other, its seed a pure function of the UTC day and the tier
 * ({@link #daily}): every machine deals the same three, and they differ by tier.
 *
 * @param tier how hard the mission is; its letter is the first of its name
 * @param length how many biomes it marches through before its finale, at most the alphabet's last place
 * @param seed the mission seed, in {@code [0, SEEDS)}
 */
public record MissionCode(TierKind tier, int length, long seed) {

    /** Characters in a code, the tier's and the check's included. */
    public static final int LENGTH = 8;

    /** Where the length's character stands: after the tier's letter. */
    private static final int LENGTH_AT = 1;

    private static final int SEED_LENGTH = LENGTH - 3;

    private static final int BASE = RoomCode.ALPHABET.length();

    /** How many mission seeds there are a tier and length: five characters' worth. */
    public static final long SEEDS = seeds();

    private static final int[] WEIGHTS = {1, 2, 3, 4, 6, 7, 8};

    private static final int HALF = LENGTH / 2;

    /** How far either side of a machine's own day a daily is looked for: a joiner's day may be the host's neighbour. */
    private static final int DAYS_EITHER_SIDE = 1;

    public MissionCode {
        if (length < 1 || length >= RoomCode.ALPHABET.length()) {
            throw new IllegalArgumentException(
                    "a mission length is 1 to " + (RoomCode.ALPHABET.length() - 1) + " biomes, not " + length);
        }
        if (seed < 0 || seed >= SEEDS) {
            throw new IllegalArgumentException("a mission seed is 0 to " + (SEEDS - 1) + ", not " + seed);
        }
    }

    /**
     * What a player typed, as a mission - any case, spaces and dashes ignored - or null if it is none: the
     * wrong length, a character the alphabet lacks, a letter no tier has, a length character that is the
     * alphabet's first, or a check that fails.
     */
    public static @Nullable MissionCode parse(String typed) {
        String letters = RoomCode.letters(typed, LENGTH);
        if (letters == null || check(letters) != letters.charAt(LENGTH - 1)) {
            return null;
        }
        long seed = 0L;
        for (int i = LENGTH_AT + 1; i <= LENGTH_AT + SEED_LENGTH; i++) {
            seed = seed * BASE + RoomCode.ALPHABET.indexOf(letters.charAt(i));
        }
        TierKind tier = tierOf(letters.charAt(0));
        int length = lengthOf(letters.charAt(LENGTH_AT));
        return tier == null || length < 1 ? null : new MissionCode(tier, length, seed);
    }

    /**
     * The day's daily on this tier (G0065): the day mixed with the tier's letter, the way a stream is derived
     * from a seed, onto a mission seed. The same on every machine and every run. A daily is always the default
     * length (G0102).
     *
     * @param epochDay the UTC day, as {@link LocalDate#toEpochDay()}
     * @param length the default length, {@code sim.mission.biomes}
     */
    public static MissionCode daily(long epochDay, TierKind tier, int length) {
        return new MissionCode(
                tier,
                length,
                Math.floorMod(new Rng(epochDay).derive(letter(tier)).nextLong(), SEEDS));
    }

    /**
     * The mission a session plays, from what it names and its seed: a mission when the scenario is a tier's
     * ({@code mission <tier> <length>}, or {@code mission <tier>} for {@code configured} biomes), null for any other
     * scenario.
     */
    public static @Nullable MissionCode of(String scenarioName, long seed, int configured) {
        if (seed < 0 || seed >= SEEDS) {
            return null;
        }
        for (TierKind tier : TierKind.values()) {
            String prefix = Scenarios.mission(tier).name();
            if (prefix.equals(scenarioName)) {
                return new MissionCode(tier, configured, seed);
            }
            if (scenarioName.startsWith(prefix + " ")) {
                try {
                    return new MissionCode(tier, Integer.parseInt(scenarioName.substring(prefix.length() + 1)), seed);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** The length a code's length character stands for, or -1 if it is none of the alphabet's. */
    public static int lengthOf(char character) {
        return RoomCode.ALPHABET.indexOf(character);
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
     * What each character of a code may be, slot by slot, for a picker: the tiers' letters first, then the lengths
     * {@code mission} allows, then the room code's alphabet - so a picked code is always of some tier and length.
     */
    public static List<String> alphabets(MissionConfig mission) {
        StringBuilder tiers = new StringBuilder();
        for (TierKind tier : TierKind.values()) {
            tiers.append(letter(tier));
        }
        StringBuilder lengths = new StringBuilder();
        for (int length = mission.biomesMin(); length <= mission.biomesMax(); length++) {
            lengths.append(RoomCode.ALPHABET.charAt(length));
        }
        List<String> alphabets = new ArrayList<>(LENGTH);
        alphabets.add(tiers.toString());
        alphabets.add(lengths.toString());
        for (int i = LENGTH_AT + 1; i < LENGTH; i++) {
            alphabets.add(RoomCode.ALPHABET);
        }
        return List.copyOf(alphabets);
    }

    /**
     * The day this is the daily of, looked for on {@code around} first and then the day either side - or
     * null if it is no daily of those. Where a daily was started, {@code around} is the day its page opened.
     *
     * @param dailyLength the default length, a daily's
     */
    public @Nullable LocalDate dailyNear(LocalDate around, int dailyLength) {
        for (int i = 0; i <= 2 * DAYS_EITHER_SIDE; i++) {
            // 0, -1, +1: the day itself before its neighbours.
            int offset = (i + 1) / 2 * (i % 2 == 1 ? -1 : 1);
            LocalDate day = around.plusDays(offset);
            if (equals(daily(day.toEpochDay(), tier, dailyLength))) {
                return day;
            }
        }
        return null;
    }

    /** What a session plays this mission as: {@code mission <tier> <length>}, built from {@link #seed} as its seed. */
    public Scenario scenario() {
        return Scenarios.mission(tier, length);
    }

    /** The eight characters, with no dash. */
    public String text() {
        char[] code = new char[LENGTH];
        code[0] = letter(tier);
        code[LENGTH_AT] = RoomCode.ALPHABET.charAt(length);
        long rest = seed;
        for (int i = LENGTH_AT + SEED_LENGTH; i > LENGTH_AT; i--) {
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
