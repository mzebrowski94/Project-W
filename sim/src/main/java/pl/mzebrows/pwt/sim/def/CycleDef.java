package pl.mzebrows.pwt.sim.def;

import java.util.List;
import pl.mzebrows.pwt.map.Randomness;

/**
 * The shape of an emplacement's cycle (G0113), one line of config: {@code dealt 4 6} - a cycle of four to six
 * different compass points, dealt when the level is built and one shot a step - or {@code volley 0,90,180,270} - every
 * bearing named fired together on every step. A bearing is in degrees clockwise from north, one of the eight compass
 * points.
 */
public sealed interface CycleDef {

    /** The cycle one emplacement fires for the whole level, dealt from {@code rng} if it is dealt at all. */
    Cycle deal(Randomness rng);

    /** A cycle of {@code cycleMin} to {@code cycleMax} different compass points, one a step, in the order dealt. */
    record Dealt(int cycleMin, int cycleMax) implements CycleDef {

        public Dealt {
            if (cycleMin < 1 || cycleMax < cycleMin || cycleMax > Cycle.POINTS) {
                throw new IllegalArgumentException(
                        "a dealt cycle has from 1 to " + Cycle.POINTS + " bearings, was " + cycleMin + ".." + cycleMax);
            }
        }

        @Override
        public Cycle deal(Randomness rng) {
            int length = cycleMin + rng.nextInt(cycleMax - cycleMin + 1);
            int[] points = new int[Cycle.POINTS];
            for (int point = 0; point < points.length; point++) {
                points[point] = point;
            }
            long steps = 0L;
            for (int step = 0; step < length; step++) {
                // A partial shuffle: each step one of the points no earlier step took.
                int pick = step + rng.nextInt(points.length - step);
                int point = points[pick];
                points[pick] = points[step];
                points[step] = point;
                steps = Cycle.with(steps, step, 1 << point);
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
