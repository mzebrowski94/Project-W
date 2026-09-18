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
 * <p>A <strong>slot</strong> is a player's seat at the game: the keyboard holds slot zero and a
 * gamepad claims the lowest free one. It is the only thing that ties a device to a commander, and it
 * is a number on the {@code Commander} component rather than a roster anywhere, so a commander that
 * dies and comes back is the same player without anything having to remember him.
 *
 * <p>Both halves live here rather than in the four places that need them. {@link #MAX} is the number
 * every caller must honour - a fifth gamepad has nowhere to sit, and a client that decided that for
 * itself would disagree with the simulation the first time anyone changed one of them. {@link
 * #inSlot} is what makes "ascending slot order" a fact rather than something each system rediscovers
 * from whatever order Ashley happens to hold the family in - and that order changes the moment a
 * commander is killed and respawned, which is exactly when two commanders are most likely to be
 * shouting at the same trooper.
 */
public final class Players {

    /**
     * Four players, and four is structure rather than tuning.
     *
     * <p>Not a key in {@code game.properties}: the pad map, the slot colours and the leash are all
     * sized against it, and a config file that could say five would be a config key that lies.
     */
    public static final int MAX = 4;

    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);

    private Players() {}

    /** Whether this is a seat anybody could sit in. */
    public static boolean isSlot(int slot) {
        return slot >= 0 && slot < MAX;
    }

    /**
     * The commander sitting in this slot, or null if nobody is.
     *
     * <p>A scan rather than an index, because the alternative is a table of entities held across
     * ticks - and a kept entity reference in a pooled engine comes back aimed at something else
     * (G0016). There are at most {@link #MAX} commanders, so the scan is four comparisons.
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
     * was the same answer while there was one commander; with four it changes every time somebody is
     * killed and respawned, so the answer would quietly swap between players mid-fight.
     */
    public static @Nullable Entity standing(ImmutableArray<Entity> commanders) {
        for (int slot = 0; slot < MAX; slot++) {
            Entity commander = inSlot(commanders, slot);
            if (commander != null && !down(commander)) {
                return commander;
            }
        }
        return null;
    }

    /** Off his feet: on the ground bleeding, or on it for good. Neither drives, and neither is a target. */
    public static boolean down(Entity entity) {
        if (CASUALTY.has(entity) && CASUALTY.get(entity).begun) {
            return true;
        }
        return DYING.has(entity) && DYING.get(entity).begun;
    }
}
