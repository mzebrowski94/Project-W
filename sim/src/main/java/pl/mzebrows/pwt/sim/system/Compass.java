package pl.mzebrows.pwt.sim.system;

/**
 * Twelve directions, thirty degrees apart, as unit vectors written out as literals.
 *
 * <p>A table rather than an angle because {@code Math.sin} and {@code Math.cos} are banned in the
 * simulation - they are specified only to within an ulp and may differ between JVM implementations,
 * which is enough to make a replay stop replaying on somebody else's machine. Twelve is enough
 * resolution to slip past a circle and to split a circle between a handful of men, and few enough to
 * score for every unit every tick without thinking about it.
 *
 * <p>Slot zero points along positive x and the slots turn anticlockwise.
 */
final class Compass {

    /** How many directions there are. */
    static final int SLOTS = 12;

    private static final float[] DIRECTIONS = {
        1f,
        0f,
        0.8660254f,
        0.5f,
        0.5f,
        0.8660254f,
        0f,
        1f,
        -0.5f,
        0.8660254f,
        -0.8660254f,
        0.5f,
        -1f,
        0f,
        -0.8660254f,
        -0.5f,
        -0.5f,
        -0.8660254f,
        0f,
        -1f,
        0.5f,
        -0.8660254f,
        0.8660254f,
        -0.5f
    };

    private Compass() {}

    /** The x component of a slot's direction. Any slot, wrapped round the circle. */
    static float x(int slot) {
        return DIRECTIONS[Math.floorMod(slot, SLOTS) * 2];
    }

    /** The y component of a slot's direction. Any slot, wrapped round the circle. */
    static float y(int slot) {
        return DIRECTIONS[Math.floorMod(slot, SLOTS) * 2 + 1];
    }
}
