package pl.mzebrows.pwt.net.session;

/**
 * What a world is built from, as the host ships it to everyone who joins (ADR 0012, "the host ships its
 * config"): the text of its {@code assets/sim/*.properties}, which scenario, which seed. Raw text rather than a
 * parsed config, so that peers parse it with their own loader - which the build id already says is the
 * same - and tuning a playtest needs no file copied to anyone.
 */
public record SessionSetup(String configText, String scenario, long seed) {}
