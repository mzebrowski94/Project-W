package pl.mzebrows.pwt.net.session;

import pl.mzebrows.pwt.sim.Simulation;

/**
 * How a session turns a {@link SessionSetup} into a world. The client hands in one that parses the host's
 * config with its own loader, which logs what was wrong with it; a test hands in one of its own.
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
