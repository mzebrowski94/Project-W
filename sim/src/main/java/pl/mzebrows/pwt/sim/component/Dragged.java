package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Something that can be hauled about: a man who is down, or a barrel.
 *
 * <p>Carrying this says only that it <em>could</em> be dragged. Whether it may be right now is
 * {@code DragSystem}'s question, and the answer is that it must have no will of its own - a casualty
 * is one because he is down, a barrel because it never had one. A trooper on his feet carries this
 * component and cannot be grabbed, which is the same shape every other capability in the game has:
 * the component is permanent and a field decides.
 *
 * <p>{@link #by} is the other half of the id pair described on {@link Drag}. Zero means nobody has
 * hold of it, and {@link #reset()} returning it to zero is what makes a recycled component safe:
 * whoever was hauling the previous occupant of this instance can never match it again.
 */
public final class Dragged implements Component, Pool.Poolable {

    /** The id of the drag holding this, or {@link Drag#NOTHING}. */
    public long by = Drag.NOTHING;

    /**
     * Whether this has ever been hauled.
     *
     * <p>It latches rather than tracking the current state, because what it pays for is the work
     * afterwards: a man dragged out of a firefight takes far longer to patch up than one treated
     * where he fell, and letting go of him a moment before you start does not undo the journey.
     *
     * <p>It latches for <em>one</em> time on the ground, though, not for the rest of the level -
     * {@code DamageSystem} clears it as a man goes down. A trooper who was carried out of one
     * firefight and patched up is not still expensive an hour later, and the alternative reads as
     * the game holding a grudge about something the player did right.
     */
    public boolean hauled;

    /**
     * Whether this was a body the solver would move, before somebody picked it up.
     *
     * <p>A barrel is scenery: solid so you walk into it, immovable so a crowd cannot shuffle it
     * across the level. But the solver only keeps <em>movable</em> solids out of rock and inside the
     * arena, so a barrel hauled about while still immovable would go through a wall - and terrain
     * being absolute is one of the rules everything else is built on. So being held makes it movable
     * and letting go puts it back, and this remembers which it was. A man who is down was movable
     * already and nothing changes for him.
     */
    public boolean wasMovable;

    /** True while something has hold of this. */
    public boolean held() {
        return by != Drag.NOTHING;
    }

    /** Takes hold, and remembers that this has been hauled at all. */
    public void take(long dragId, boolean movable) {
        by = dragId;
        hauled = true;
        wasMovable = movable;
    }

    @Override
    public void reset() {
        by = Drag.NOTHING;
        hauled = false;
        wasMovable = false;
    }
}
