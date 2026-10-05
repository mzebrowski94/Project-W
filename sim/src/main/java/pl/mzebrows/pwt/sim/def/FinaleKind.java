package pl.mzebrows.pwt.sim.def;

/**
 * A kind of finale a mission ends on (G0064): one set piece under {@code sim.finale.<name>} in {@code
 * finales.properties} - ground, a hold, a wave schedule and a lair - laid the same every time but for one flip
 * north-south. Drawn from its own stream, equally, whatever the tier. A new one is a constant and a block, through
 * the {@code new-biome} skill.
 */
public enum FinaleKind {
    /** A bare square to see across: four guns round the post, mines across the corners, waves from all four. */
    REDOUBT
}
