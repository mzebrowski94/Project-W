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
 * @param casualty a man down and bleeding out, who can still be put back on his feet. It must not be
 *     confusable with {@code dying} at a glance: one is somewhere to run to and the other is not,
 *     and a player who mistook them would either abandon a man he could have saved or cross open
 *     ground for a corpse
 * @param crewed a gun with somebody on it. Brighter than an abandoned one, because the difference
 *     between a turret that is about to shoot at you and a turret that is scenery is the difference
 *     between two completely different approaches to it
 */
public record StateTint(
        float unaware,
        float windingUp,
        float airborne,
        float grappling,
        float staggered,
        float dying,
        float casualty,
        float crewed) {

    /** How far to shift the base colour, in {@code [-1, 1]}. */
    public float forState(Appearance.State state) {
        return switch (state) {
            case UNAWARE -> unaware;
            case WINDING_UP -> windingUp;
            case AIRBORNE -> airborne;
            case GRAPPLING -> grappling;
            case STAGGERED -> staggered;
            case DYING -> dying;
            case CASUALTY -> casualty;
            case CREWED -> crewed;
            // A man aboard something is inside it: the renderer draws the thing he is in and not
            // him, so there is no colour for this one to shift.
            case ABOARD, NORMAL -> 0f;
        };
    }
}
