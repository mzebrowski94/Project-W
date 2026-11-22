package pl.mzebrows.pwt.sim.def;

/**
 * An emplacement's cycle as it was dealt (G0113): up to {@link #MOST} steps, each the compass points it fires along on
 * that step, one bit a point - {@link EmplacementDef#bearingX} names them. A dealt cycle is one point a step; a volley
 * is one step of several. Packed into one number so the entity carries it whole and a snapshot writes it (ADR 0014).
 *
 * @param steps step {@code k}'s points in bits {@code 8k} to {@code 8k + 7}
 * @param length how many steps, at least one
 */
public record Cycle(long steps, int length) {

    /** The eight compass points. */
    public static final int POINTS = 8;

    /** The most steps a cycle has: eight bits a step in a long. */
    public static final int MOST = 8;

    private static final int ALL_POINTS = (1 << POINTS) - 1;

    public Cycle {
        if (length < 1 || length > MOST) {
            throw new IllegalArgumentException("a cycle has 1 to " + MOST + " steps, was " + length);
        }
        for (int step = 0; step < length; step++) {
            if (((steps >>> (step * POINTS)) & ALL_POINTS) == 0) {
                throw new IllegalArgumentException("every step of a cycle fires along something, step " + step);
            }
        }
    }

    /** A cycle of one compass point a step, in this order - north 0, clockwise: a scenario's, laid by hand. */
    public static Cycle of(int... points) {
        long steps = 0L;
        for (int step = 0; step < points.length; step++) {
            steps = with(steps, step, 1 << points[step]);
        }
        return new Cycle(steps, points.length);
    }

    /** The compass points step {@code step} fires along, one bit a point. */
    public int points(int step) {
        return points(steps, step);
    }

    /** The same, read off a cycle packed as {@code steps}: what the component holds. */
    public static int points(long steps, int step) {
        return (int) ((steps >>> (step * POINTS)) & ALL_POINTS);
    }

    /** One step of these compass points added after the steps of {@code steps}, its {@code step}-th. */
    static long with(long steps, int step, int points) {
        return steps | ((long) points << (step * POINTS));
    }
}
