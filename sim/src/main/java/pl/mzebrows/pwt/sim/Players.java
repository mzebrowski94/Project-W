package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Dying;

/**
 * How many commanders there can be, and which one is in a given slot.
 *
 * <p>A <strong>slot</strong> is a player in the simulation: a number, on the {@code Commander}
 * component, and the only thing that ties a player to a commander - so a commander that dies and comes
 * back is the same player without anything having to remember him. Which device drives a slot is not
 * the simulation's business: offline a slot is the number of the seat the device is in, and online
 * the host hands slots out (G0038).
 *
 * <p>{@link #inSlot} and {@link #standing} live here rather than in the places that need them, which is
 * what makes "ascending slot order" a fact rather than something each system rediscovers from whatever
 * order a family happens to be in.
 */
public final class Players {

    /**
     * The ceiling: at most eight players in one game, of whom any one machine seats four (G0038).
     *
     * <p>A ceiling rather than a size. The simulation and the wire are sized from a capacity value
     * handed to them, which defaults to this and which the tests set to something else to prove that
     * nothing has eight written into it. Raising the ceiling is this constant and a commander colour
     * per new player. Not a key in {@code game.properties}: a number every peer must hold identically
     * is a refused handshake waiting to happen (G0038).
     */
    public static final int MAX = 8;

    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);

    private Players() {}

    /**
     * The commander sitting in this slot, or null if nobody is.
     *
     * <p>A scan rather than an index, because the alternative is a table of entities held across
     * ticks - and a kept entity reference in a pooled engine comes back aimed at something else
     * (G0016). There are at most {@link #MAX} commanders, so the scan is a handful of comparisons.
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
     * <p>The one answer to "where is the player side", asked by the flow field's goal, by a leaper
     * choosing where to land, by a man coming back from the dead and by a player joining mid-fight.
     * Four copies of it would be four chances to disagree about whether a man bleeding out counts.
     *
     * <p><strong>By slot, not by whatever order Ashley holds the family in.</strong> Insertion order
     * was the same answer while there was one commander; with several it changes every time somebody is
     * killed and respawned, so the answer would quietly swap between players mid-fight.
     */
    public static @Nullable Entity standing(ImmutableArray<Entity> commanders) {
        @Nullable Entity lowest = null;
        int lowestSlot = Integer.MAX_VALUE;
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            int slot = COMMANDER.get(commander).slot;
            if (slot < lowestSlot && !down(commander)) {
                lowest = commander;
                lowestSlot = slot;
            }
        }
        return lowest;
    }

    /** Off his feet: on the ground bleeding, or on it for good. Neither drives, and neither is a target. */
    public static boolean down(Entity entity) {
        if (CASUALTY.has(entity) && CASUALTY.get(entity).begun) {
            return true;
        }
        return DYING.has(entity) && DYING.get(entity).begun;
    }
}
