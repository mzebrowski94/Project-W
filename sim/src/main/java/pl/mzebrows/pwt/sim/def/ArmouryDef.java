package pl.mzebrows.pwt.sim.def;

/**
 * A weapons cache's part in the armoury deck (G0050): how many cards of it the deck holds, and - for a
 * <em>sealed</em> cache - how near a commander must come before it is dealt one.
 *
 * <p>Two roles, never both. A gun's cache is a card: it has cards and no reveal radius, because it
 * already shows its gun. The sealed cache is not a card: it has a reveal radius and no cards, because a
 * sealed cache dealt out of the deck would be a sealed cache dealt forever. Everything else has no
 * block at all, which is {@link #off()}.
 *
 * @param cards how many of this cache the deck holds before any upgrade adds more, for a gun's cache;
 *     zero for anything else. What an upgrade adds is folded through {@code Modifiers.armouryCards}
 * @param revealRadius how near a commander on his feet must come, in world units, for a sealed cache
 *     to be dealt its gun; zero for anything that is not sealed. Held above the cache's reach plus the
 *     widest commander (G0050), so the gun shows before anybody can touch it
 */
public record ArmouryDef(int cards, float revealRadius) {

    public ArmouryDef {
        Require.notNegative("armoury cards", cards);
        Require.notNegative("armoury revealRadius", revealRadius);
        if (cards > 0 && revealRadius > 0f) {
            throw new IllegalArgumentException(
                    "a cache is a card in the deck or sealed, never both: a sealed card would deal a sealed cache");
        }
    }

    /** Not in the deck and never sealed: what every pickup but a weapons cache declares. */
    public static ArmouryDef off() {
        return new ArmouryDef(0, 0f);
    }

    /** True if this cache shows nothing until a commander comes near, and is then dealt a gun. */
    public boolean sealed() {
        return revealRadius > 0f;
    }

    /** True if the deck deals this cache before any upgrade has added to it. */
    public boolean inDeck() {
        return cards > 0;
    }
}
