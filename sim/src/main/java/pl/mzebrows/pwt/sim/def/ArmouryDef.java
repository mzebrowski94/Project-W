package pl.mzebrows.pwt.sim.def;

/**
 * A pickup's part in the armoury deck: how many cards of it the deck holds, or - for a <em>crate</em> -
 * that it is dealt its contents from the deck the first time it is opened (G0056).
 *
 * <p>Two roles, never both. A gun's cache or a medkit is a card: it has cards and is no crate, because it
 * already is what it is. A crate is not a card: a crate dealt out of the deck would be a crate inside a
 * crate. Everything else has no block at all, which is {@link #off()}.
 *
 * @param cards how many of this pickup the deck holds before any upgrade adds more; zero for anything
 *     else. What an upgrade adds is folded through {@code Modifiers.armouryCards}
 * @param crate whether this is a crate: opened by hand, dealt from the deck on first opening
 */
public record ArmouryDef(int cards, boolean crate) {

    public ArmouryDef {
        Require.notNegative("armoury cards", cards);
        if (cards > 0 && crate) {
            throw new IllegalArgumentException(
                    "a pickup is a card in the deck or a crate, never both: a crate dealt from the deck would be"
                            + " a crate inside a crate");
        }
    }

    /** Not in the deck and never a crate: what every pickup but a gun's cache, a medkit and a crate declares. */
    public static ArmouryDef off() {
        return new ArmouryDef(0, false);
    }

    /** True if the deck deals this before any upgrade has added to it. */
    public boolean inDeck() {
        return cards > 0;
    }
}
