package pl.mzebrows.pwt.sim.def;

/**
 * Which side a thing is on. Damage only ever crosses sides, never stays within one.
 *
 * <p>Combat asks a question about <em>data</em>, not about what an entity is. Here rather than on
 * {@code Faction} because definitions - what a pickup offers each side - may not depend on a component.
 */
public enum Side {
    /** Not set, or scenery. Never friendly with anything, including other neutrals' attacks. */
    NEUTRAL,
    /** The commander and, when they exist, troopers. */
    PLAYER,
    /** Everything trying to kill them. */
    ALIEN
}
