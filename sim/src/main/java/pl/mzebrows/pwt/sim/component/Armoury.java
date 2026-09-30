package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * The armoury deck (G0056): which of its cards have been dealt into crates since it was last shuffled,
 * and the counts it was shuffled at - and the bag a crate's size is drawn from. Level state on the level
 * entity (G0007), so a snapshot, a drop-in and a heal carry it. Written only by {@code CrateSystem}, on a
 * crate's first opening.
 *
 * <p>The deck itself is not here: its counts are each cache's {@code armoury.cards}, folded through
 * {@code Modifiers.armouryCards} on every deal. When the deck comes back a different size from {@link
 * #shuffledAt} - an upgrade has stacked it - it is shuffled afresh, so it never reads the ledger and never
 * deals from a deck that no longer exists. The size is enough to notice that by, because a deal only
 * ever adds cards ({@code Effect.Deal}).
 */
public final class Armoury implements Component, Pool.Poolable {

    /**
     * Which cards have come out of the current shuffle, one bit per card ({@code map.Bag}). Zero is a
     * full deck, which is what a new level starts with.
     */
    public long drawn;

    /** How many cards the deck held when it was last shuffled. Zero before the first deal. */
    public int shuffledAt;

    /** Which crate sizes have come out of the current bag, one bit per size from the fewest ({@code map.Bag}). */
    public long sizesDrawn;

    /** The fewest a crate held when the size bag was last filled: an upgrade that raises it refills it. */
    public int sizesFrom;

    @Override
    public void reset() {
        drawn = 0L;
        shuffledAt = 0;
        sizesDrawn = 0L;
        sizesFrom = 0;
    }
}
