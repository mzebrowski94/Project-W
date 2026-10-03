package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Tally;

/**
 * How many commanders there can be, and which one is in a given slot.
 *
 * <p>A <strong>slot</strong> is a player in the simulation: a number on {@code Commander}, the only tie
 * between a player and a commander. Which device drives it is not the simulation's business (G0038).
 */
public final class Players {

    /**
     * The ceiling: at most eight players in one game, of whom any one machine seats four (G0038).
     *
     * <p>A ceiling, not a size: everything is sized from a capacity. Not a {@code assets/sim/*.properties} key -
     * every peer must hold it identically (G0038).
     */
    public static final int MAX = 8;

    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);

    private Players() {}

    /**
     * The slot a blow from this entity is credited to (G0044): a commander's own slot, and {@link
     * Tally#NOBODY} for anything else - a trooper's kills credit no player.
     */
    public static int credit(@Nullable Entity entity) {
        return entity != null && COMMANDER.has(entity) ? COMMANDER.get(entity).slot : Tally.NOBODY;
    }

    /**
     * The commander sitting in this slot, or null if nobody is.
     *
     * <p>A scan of at most {@link #MAX}, rather than a table of entities held across ticks.
     */
    public static @Nullable Entity inSlot(ImmutableArray<Entity> commanders, int slot) {
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (COMMANDER.get(commander).slot == slot) {
                return commander;
            }
        }
        return null;
    }

    /**
     * The lowest-numbered player still on his feet, or null if none is.
     *
     * <p>The one answer to "where is the player side". <strong>By slot</strong>, not family order,
     * which changes on every respawn.
     */
    public static @Nullable Entity standing(ImmutableArray<Entity> commanders) {
        @Nullable Entity lowest = null;
        int lowestSlot = Integer.MAX_VALUE;
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            int slot = COMMANDER.get(commander).slot;
            if (slot < lowestSlot && !Life.down(commander)) {
                lowest = commander;
                lowestSlot = slot;
            }
        }
        return lowest;
    }
}
