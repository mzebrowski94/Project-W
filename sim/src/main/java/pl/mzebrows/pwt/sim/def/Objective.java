package pl.mzebrows.pwt.sim.def;

/**
 * A condition a level requires before it counts as done.
 *
 * <p>A level names a <em>set</em> of these and requires all of it. Two objectives that can coexist is
 * the whole point: "clear the map and take both points" is a different level from either half, and it
 * is one that costs no new code once both exist separately.
 *
 * <p>What a completed level then <em>does</em> is deliberately not decided here, and in the alpha it
 * does nothing at all: the level entity knows it is done and nothing reads the answer. Extraction, a
 * cut to the next level and a lull before another wave are all still open questions, and any of them
 * would be a bad thing to guess at from inside a system about counting.
 */
public enum Objective {
    /** Nothing hostile left standing. A body still in its throes does not count against it. */
    CLEAR_ENEMIES,
    /** Every control point on the map taken and held. A map with no points can never meet this. */
    CAPTURE_POINTS,
    /**
     * The post held: every wave the level had to send has been sent, and nothing hostile is left
     * standing. A map with no post can never meet this, exactly as one with no points cannot meet
     * {@link #CAPTURE_POINTS}.
     *
     * <p>It has to ask about the schedule as well as about the field, and that is the whole shape of
     * it: an empty field means you have won if there is nothing left to come, and means you are
     * between waves if there is. Both halves are level state, recomputed every tick like everything
     * else here - nothing is latched, so "the last wave is dead" can stop being true if a level ever
     * grows a way to send another.
     */
    HOLD_POINT
}
