package pl.mzebrows.pwt.sim.upgrade;

import pl.mzebrows.pwt.sim.Rng;

/**
 * Deals cards: distinct kinds, drawn by weight without replacement, in deal order - the order they
 * are drawn is the order they are shown. Pure, and drawing nothing when there is nothing to deal.
 *
 * <p><strong>Anti-streak.</strong> Kinds the chooser passed over last time are dealt only if the
 * others run out, and after them.
 */
final class Dealer {

    private Dealer() {}

    /**
     * Refuses a catalogue of more kinds than a pass-over can name: it is one bit of a {@code long} per
     * kind, and a sixty-fifth would share the first one's bit.
     *
     * @throws IllegalArgumentException when {@code kinds} is past {@link Long#SIZE}
     */
    static void requireFits(int kinds) {
        if (kinds > Long.SIZE) {
            throw new IllegalArgumentException(
                    "a pass-over names at most " + Long.SIZE + " upgrade kinds, one bit of a long each, not " + kinds);
        }
    }

    /**
     * Deals up to {@code options} kinds into {@code out}, by index into {@code weights}, and returns how
     * many. A weight of zero is a kind that cannot be dealt; {@code weights} is spent as scratch.
     *
     * @param avoid a bit per index the chooser passed over last time
     */
    static int deal(Rng rng, int[] weights, long avoid, int options, int[] out) {
        int dealt = draw(rng, weights, avoid, false, options, out, 0);
        return draw(rng, weights, avoid, true, options, out, dealt);
    }

    private static int draw(Rng rng, int[] weights, long avoid, boolean avoided, int options, int[] out, int dealt) {
        int count = dealt;
        while (count < options) {
            int total = 0;
            for (int k = 0; k < weights.length; k++) {
                if (open(weights, avoid, avoided, k)) {
                    total += weights[k];
                }
            }
            if (total <= 0) {
                break;
            }
            int roll = rng.nextInt(total);
            for (int k = 0; k < weights.length; k++) {
                if (!open(weights, avoid, avoided, k)) {
                    continue;
                }
                roll -= weights[k];
                if (roll < 0) {
                    out[count++] = k;
                    weights[k] = 0;
                    break;
                }
            }
        }
        return count;
    }

    private static boolean open(int[] weights, long avoid, boolean avoided, int k) {
        return weights[k] > 0 && ((avoid >>> k & 1L) != 0L) == avoided;
    }
}
