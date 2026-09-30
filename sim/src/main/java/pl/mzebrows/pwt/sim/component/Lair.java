package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.def.Arrival;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * What the level is keeping out of sight until the players are all in one place, and the place (G0048).
 *
 * <p>Carried by the level entity, like the wave schedule beside it (G0007): when the boss rises is a
 * fact about the level, not about anything standing in it. <strong>Nothing of it is on the field
 * until it wakes</strong> - not asleep, not hidden, simply not there - so pulling the view back from
 * the next biome shows nothing that is waiting at the end.
 *
 * <p>It wakes once, the first tick every commander still on his feet is standing on its ground, and
 * puts down everything it holds on that tick. Waking latches.
 */
public final class Lair implements Component, Pool.Poolable {

    /** What rises, in the order it was described. Filled by a scenario before the first tick. */
    private final Array<Arrival> pending = new Array<>();

    /** The ground every commander must be standing on. Meaningless while nothing is pending. */
    private TileRect ground = TileRect.ofTile(0, 0);

    /** Whether it has woken. */
    private boolean woken;

    /**
     * While it waits: how many commanders on their feet stand on its ground, and how many it is waiting
     * for (B.1). Published for the interface - a boss held back by a player the others cannot see is a
     * number the game must show (pillar 5). Written by {@code LairSystem} every tick it waits.
     */
    private int present;

    private int needed;

    /** Says where the players have to be for it to wake, and returns this, for a scenario. */
    public Lair at(TileRect where) {
        this.ground = where;
        return this;
    }

    /** One creature of this strain, put down here when it wakes. */
    public Lair unit(UnitKind kind, float x, float y) {
        pending.add(Arrival.unit(kind, x, y));
        return this;
    }

    /** One hole sending out this strain, dug here when it wakes. */
    public Lair nest(UnitKind kind, float x, float y) {
        pending.add(Arrival.nest(kind, x, y));
        return this;
    }

    /** Something lying here for the first thing that touches it, put down when it wakes. */
    public Lair pickup(PickupKind kind, float x, float y) {
        pending.add(Arrival.pickup(kind, x, y));
        return this;
    }

    public TileRect ground() {
        return ground;
    }

    /** True while it has something to put down and has not yet done so. */
    public boolean waiting() {
        return !woken && pending.size > 0;
    }

    /** True once it has woken. */
    public boolean woken() {
        return woken;
    }

    /** How many things it holds. */
    public int size() {
        return pending.size;
    }

    /** The one at this place in the order. */
    public Arrival get(int index) {
        return pending.get(index);
    }

    /** Commanders on their feet standing on its ground, as of the last tick it waited. */
    public int present() {
        return present;
    }

    /** Commanders on their feet it is waiting for, as of the last tick it waited. */
    public int needed() {
        return needed;
    }

    /** Says how many are on its ground of how many it waits for. */
    public void count(int newPresent, int newNeeded) {
        this.present = newPresent;
        this.needed = newNeeded;
    }

    /** Marks it woken; it never wakes again. */
    public void wake() {
        woken = true;
    }

    @Override
    public void reset() {
        pending.clear();
        ground = TileRect.ofTile(0, 0);
        woken = false;
        present = 0;
        needed = 0;
    }
}
