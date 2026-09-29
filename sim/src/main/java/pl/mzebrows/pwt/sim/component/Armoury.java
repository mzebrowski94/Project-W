package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * The armoury deck (G0050): which of its cards have been dealt to sealed caches since it was last
 * shuffled, and the counts it was shuffled at. Level state on the level entity (G0007), so a snapshot,
 * a drop-in and a heal carry it. Written only by {@code PickupSystem}'s reveal.
 *
 * <p>The deck itself is not here: its counts are each cache's {@code armoury.cards}, folded through
 * {@code Modifiers.armouryCards} on every reveal. When the deck comes back a different size from {@link
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

    /** How many cards the deck held when it was last shuffled. Zero before the first reveal. */
    public int shuffledAt;

    @Override
    public void reset() {
        drawn = 0L;
        shuffledAt = 0;
    }
}
