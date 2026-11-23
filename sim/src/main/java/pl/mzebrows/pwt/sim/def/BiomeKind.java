package pl.mzebrows.pwt.sim.def;

/**
 * A kind of biome a mission deals (missions ticket 03): one block of ground, roster and furniture under {@code
 * sim.biome.<name>} in {@code biomes.properties}. Dealt from the pool equally - none twice in a mission, never two
 * of one base side by side - and populated by rules, never places (G0068). A new one is a constant and a block,
 * through the {@code new-biome} skill.
 */
public enum BiomeKind {
    /** Open ground and grunts with rabbits to deny: where nothing is a problem yet. */
    PLAIN,
    /** Heavy rock bent round corners, brutes with spitters behind them and barrels among them, a house or two. */
    QUARRY,
    /** Undergrowth over a fifth of it, and runners and ants through it: what you cannot outrun, in ground you wade. */
    BRAKE,
    /** Low crags mounded round the holes, ants, and food lying about for them. */
    ANTHILL,
    /** Heavy crags on burnt ground - masses to fight round and behind - and spitters on them. */
    BADLANDS,
    /** Straight slabs of rock on sand, corridors through them, brutes and spitters and ants in the corridors. */
    CUT,
    /** Mire with islands of reeds, frogs ruining the ground and ants on it: where walking round something costs. */
    FEN,
    /**
     * Ash with pools of lava nothing stands on, geysers among them and magma spitters whose spit leaves fire (G0091,
     * provisional, for review): where the ground itself is the danger - read it, and time it.
     */
    CALDERA,
    /**
     * Open sand with few obstacles - stones from a man's size to five times it, and patches of mud that slow whoever
     * wades them - runners and sludgers on it, and the snake going through all of it (G0095).
     */
    DESERT,
    /**
     * Frost cut into a maze by fissures, its narrow ways shut by ice to be broken through, and two clearings in it, each
     * with a control point and more of the swarm than the maze (G0115, provisional, for review): sludgers and jaguars
     * from nests, thumpers and sludgers on the loose.
     */
    RIFT
}
