package pl.mzebrows.pwt.net.session;

import pl.mzebrows.pwt.sim.Simulation;

/**
 * How a session turns a {@link SessionSetup} into a world. {@code net} cannot parse a config - that is
 * the client's loader - so the client hands one of these in, and a test hands in one of its own.
 */
public interface WorldFactory {

    /** The scenario's world before its first tick: what the host starts, and what a restart rebuilds. */
    Simulation fresh(SessionSetup setup);

    /**
     * The scenario's map with nothing on it but the level - what a snapshot is restored into
     * ({@code Scenarios.blank}, ADR 0014).
     */
    Simulation blank(SessionSetup setup);
}
