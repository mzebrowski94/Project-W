package pl.mzebrows.pwt.sim.def;

import java.util.List;
import pl.mzebrows.pwt.map.Randomness;

/**
 * The shape of an emplacement's cycle (G0113), one line of config: {@code dealt 4 6 1 4} - a cycle of four to six
 * steps, each one to four of the four directions (north, east, south, west) fired together, dealt when the level is
 * built - or {@code volley 0,90,180,270} - every bearing named fired together on every step. A bearing is in degrees
 * clockwise from north, one of the eight compass points.
 */
public sealed interface CycleDef {

    /** The cycle one emplacement fires for the whole level, dealt from {@code rng} if it is dealt at all. */
    Cycle deal(Randomness rng);

    /**
     * A cycle of {@code cycleMin} to {@code cycleMax} steps, each {@code fewestDirections} to {@code mostDirections}
     * different ones of the four directions, fired together, in the order dealt.
     */
    record Dealt(int cycleMin, int cycleMax, int fewestDirections, int mostDirections) implements CycleDef {

        /** North, east, south and west, as compass points: what a dealt step fires along. */
        private static final int[] DIRECTIONS = {0, 2, 4, 6};

        public Dealt {
            if (cycleMin < 1 || cycleMax < cycleMin || cycleMax > Cycle.MOST) {
                throw new IllegalArgumentException(
                        "a dealt cycle has from 1 to " + Cycle.MOST + " steps, was " + cycleMin + ".." + cycleMax);
            }
            if (fewestDirections < 1 || mostDirections < fewestDirections || mostDirections > DIRECTIONS.length) {
                throw new IllegalArgumentException("a dealt step fires along 1 to " + DIRECTIONS.length
                        + " directions, was " + fewestDirections + ".." + mostDirections);
            }
        }

        @Override
        public Cycle deal(Randomness rng) {
            int length = cycleMin + rng.nextInt(cycleMax - cycleMin + 1);
            int[] directions = DIRECTIONS.clone();
            long steps = 0L;
            for (int step = 0; step < length; step++) {
                int count = fewestDirections + rng.nextInt(mostDirections - fewestDirections + 1);
                int points = 0;
                for (int taken = 0; taken < count; taken++) {
                    // A partial shuffle: each direction of a step one no earlier pick of that step took.
                    int pick = taken + rng.nextInt(directions.length - taken);
                    int direction = directions[pick];
                    directions[pick] = directions[taken];
                    directions[taken] = direction;
                    points |= 1 << direction;
                }
                steps = Cycle.with(steps, step, points);
            }
            return new Cycle(steps, length);
        }
    }

    /** Every one of these bearings fired together, every step: one step, never dealt. */
    record Volley(List<Integer> bearings) implements CycleDef {

        private static final int DEGREES_A_POINT = 360 / Cycle.POINTS;

        public Volley {
            bearings = List.copyOf(bearings);
            if (bearings.isEmpty()) {
                throw new IllegalArgumentException("a volley fires along at least one bearing");
            }
            for (int bearing : bearings) {
                if (bearing < 0 || bearing >= 360 || bearing % DEGREES_A_POINT != 0) {
                    throw new IllegalArgumentException("a volley's bearings are compass points, not " + bearing);
                }
            }
        }

        @Override
        public Cycle deal(Randomness rng) {
            int points = 0;
            for (int bearing : bearings) {
                points |= 1 << (bearing / DEGREES_A_POINT);
            }
            return new Cycle(points, 1);
        }
    }
}
