package pl.mzebrows.pwt.sim.def;

/**
 * Which side a thing is on. Damage only ever crosses sides, never stays within one.
 *
 * <p>This exists so combat asks a question about <em>data</em> rather than about what an entity is.
 * It replaced a hard-coded "is this the commander?" check that stopped a commander shooting itself;
 * with a side on the projectile, the same rule now covers troopers, friendly fire and anything else
 * with a team, without another branch.
 *
 * <p>It lives with the definitions rather than on the {@code Faction} component because definitions
 * need to answer questions about sides - what a pickup offers each of them - and a definition may
 * not depend on a component. That arrow points one way on purpose.
 */
public enum Side {
    /** Not set, or scenery. Never friendly with anything, including other neutrals' attacks. */
    NEUTRAL,
    /** The commander and, when they exist, troopers. */
    PLAYER,
    /** Everything trying to kill them. */
    ALIEN
}
