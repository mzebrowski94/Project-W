package pl.mzebrows.pwt.client.play;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.lan.RoomCode;
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
        for (TierKind tier : TierKind.values()) {
            if (letter(tier) == letters.charAt(0)) {
                return new MissionCode(tier, seed);
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

    private static char letter(TierKind tier) {
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
