package pl.mzebrows.pwt.sim.scenario;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;

/**
 * Finds spots in an arena that do not overlap anything already placed.
 *
 * <p>Rejection sampling against the simulation's own seeded {@link Rng}, so the same seed lays out
 * the same arena every run. It allocates freely - scenario setup happens once, not per frame.
 *
 * <p><strong>It can fail.</strong> {@link #find} gives up after a fixed number of attempts and
 * returns null rather than looping forever, so a scenario asking for more circles than fit quietly
 * gets fewer. That is the right behaviour for a test arena and the wrong behaviour for a real level;
 * whatever replaces this for real levels needs to report the shortfall instead of swallowing it.
 */
final class Placement {

    /** A clear position for a circle. */
    record Spot(float x, float y) {}

    private static final int ATTEMPTS = 64;

    private final List<Circle> taken = new ArrayList<>();
    private final Rng rng;
    private final float width;
    private final float height;
    private final float clearance;
    private final float wallMargin;

    /**
     * @param wallMargin how far from the arena edge a placed circle must stay. Placing an obstacle
     *     flush against a wall creates a gap nothing can fit through, and a unit pushed into it ends
     *     up overlapping the obstacle permanently because there is nowhere for the solver to put it.
     *     Callers should pass at least the diameter of the largest thing that must get past.
     */
    Placement(Rng rng, SimConfig.ArenaConfig arena, float clearance, float wallMargin) {
        this.rng = rng;
        this.width = arena.width();
        this.height = arena.height();
        this.clearance = clearance;
        this.wallMargin = wallMargin;
    }

    /** Marks an area as taken without spawning anything - used to keep spawns off the commander. */
    void reserve(float x, float y, float radius) {
        taken.add(new Circle(x, y, radius));
    }

    /**
     * A spot where a circle of this radius clears everything already placed, or null if none was
     * found within the attempt budget. A returned spot is reserved, so callers never place twice.
     */
    @Nullable
    Spot find(float radius) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            float inset = radius + wallMargin;
            float x = rng.nextFloat(inset, width - inset);
            float y = rng.nextFloat(inset, height - inset);
            if (isClear(x, y, radius)) {
                reserve(x, y, radius);
                return new Spot(x, y);
            }
        }
        return null;
    }

    private boolean isClear(float x, float y, float radius) {
        for (Circle circle : taken) {
            float dx = x - circle.x();
            float dy = y - circle.y();
            float required = radius + circle.radius() + clearance;
            if (dx * dx + dy * dy < required * required) {
                return false;
            }
        }
        return true;
    }

    private record Circle(float x, float y, float radius) {}
}
