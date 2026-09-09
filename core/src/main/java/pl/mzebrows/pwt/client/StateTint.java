package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.sim.component.Appearance;

/**
 * How far to shift an entity's colour for what it is currently doing.
 *
 * <p>Pillar 5 says state must be visible, and until there is art the only thing a circle has to say
 * it with is its colour. Each value is a lerp: positive toward white, negative toward black, zero for
 * no change. Keeping them as four numbers rather than four more colours is what makes a winding-up
 * brute and a winding-up enemy read as the same event.
 *
 * @param unaware a unit that has not noticed the commander, or has lost him. It must read as the
 *     quietest thing on the field, because it is the one thing there that is not a threat yet
 * @param windingUp a unit committed to a leap and standing still - the tell, so it must pop
 * @param airborne a unit in the air on a direction it can no longer change
 * @param grappling both parties to a landed blow, for as long as the lock holds them. Winner and
 *     loser share it on purpose - see {@code AppearanceSystem}
 * @param staggered a unit shoved off a blow it survived, briefly unable to act
 * @param dying a body on the field that is not coming back up, with no exchange behind it
 */
public record StateTint(float unaware, float windingUp, float airborne, float grappling, float staggered, float dying) {

    /** How far to shift the base colour, in {@code [-1, 1]}. */
    public float forState(Appearance.State state) {
        return switch (state) {
            case UNAWARE -> unaware;
            case WINDING_UP -> windingUp;
            case AIRBORNE -> airborne;
            case GRAPPLING -> grappling;
            case STAGGERED -> staggered;
            case DYING -> dying;
            case NORMAL -> 0f;
        };
    }
}
