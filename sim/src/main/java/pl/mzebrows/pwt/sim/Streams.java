package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.sim.def.Defs;

/**
 * Every random stream a level draws from, held in one place (ADR 0014).
 *
 * <p>A stream's state is one {@code long}, and it is state like any component's: a snapshot has to
 * carry it or a peer that has just dropped in draws different numbers from the host. Scattered across
 * the systems that use them, the streams were thirteen private fields a snapshot could not see. Here
 * they are one table, built once from the level seed, and a system is handed the one it draws from.
 *
 * <p>One instance per concern and never two. {@code RngStream} already says a concern has one stream;
 * two instances built from the same salt would be two copies of one sequence, and the second consumer
 * would replay the first one's numbers.
 */
final class Streams {

    private final Rng root;
    private final Defs<RngStream, Rng> byConcern;

    Streams(long seed) {
        this.root = new Rng(seed);
        Defs.Builder<RngStream, Rng> builder = Defs.builder(RngStream.class);
        for (RngStream concern : RngStream.values()) {
            builder.put(concern, Rng.stream(seed, concern));
        }
        this.byConcern = builder.build();
    }

    /** What a scenario scatters its contents with while it populates the world. */
    Rng root() {
        return root;
    }

    /** The one stream for a concern. */
    Rng of(RngStream concern) {
        return byConcern.of(concern);
    }

    /** Every stream, root first and then in {@code RngStream} order - the order a snapshot writes them in. */
    Rng[] inOrder() {
        RngStream[] concerns = RngStream.values();
        Rng[] all = new Rng[concerns.length + 1];
        all[0] = root;
        for (int i = 0; i < concerns.length; i++) {
            all[i + 1] = byConcern.of(concerns[i]);
        }
        return all;
    }
}
