package pl.mzebrows.pwt.sim;

/**
 * Tunables the simulation reads at construction time. Loaded from {@code game.properties} by the
 * client module - the simulation itself never touches the filesystem.
 *
 * @param seed seed for the simulation's {@link Rng}
 * @param tickSeconds length of one fixed simulation step
 * @param maxTicksPerFrame ceiling on catch-up steps, so a stalled frame cannot spiral
 * @param worldWidth world width in world units
 * @param worldHeight world height in world units
 */
public record SimConfig(long seed, float tickSeconds, int maxTicksPerFrame, float worldWidth, float worldHeight) {

    public SimConfig {
        if (tickSeconds <= 0f) {
            throw new IllegalArgumentException("tickSeconds must be positive, was " + tickSeconds);
        }
        if (maxTicksPerFrame < 1) {
            throw new IllegalArgumentException("maxTicksPerFrame must be at least 1, was " + maxTicksPerFrame);
        }
        if (worldWidth <= 0f || worldHeight <= 0f) {
            throw new IllegalArgumentException("world size must be positive, was " + worldWidth + "x" + worldHeight);
        }
    }

    /** Values used when {@code game.properties} is missing or unreadable. */
    public static SimConfig defaults() {
        return new SimConfig(1L, 1f / 60f, 5, 160f, 90f);
    }
}
