package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How often a trooper throws a grenade, and when he may (G0051). How far and how hard is the commander's
 * throw ({@code sim.grenade.*}, G0019); only the <em>how often</em> and the rules are a trooper's.
 *
 * <p>Whether he ever throws is a chance, {@code AiChance.THROW_A_GRENADE}, with a base of zero: every
 * trooper carries the throw from spawn, and only an upgrade turns it up.
 *
 * @param cooldownSeconds how long one man waits after a throw before his next
 * @param minCluster how many aliens must stand within the blast at the landing point for a throw to be
 *     worth it; at least one
 * @param safetyMargin how far past the blast the nearest man on the player's side must stand, in world
 *     units - the ground a man can walk in the second the grenade flies
 * @param squadGapSeconds how long after any trooper's throw before the next man's: at most one trooper
 *     grenade per gap, however many see a pack at once
 */
@With
public record TrooperGrenadeDef(float cooldownSeconds, int minCluster, float safetyMargin, float squadGapSeconds) {

    public TrooperGrenadeDef {
        Require.notNegative("trooper grenade cooldownSeconds", cooldownSeconds);
        Require.atLeastOne("trooper grenade minCluster", minCluster);
        Require.notNegative("trooper grenade safetyMargin", safetyMargin);
        Require.notNegative("trooper grenade squadGapSeconds", squadGapSeconds);
    }
}
