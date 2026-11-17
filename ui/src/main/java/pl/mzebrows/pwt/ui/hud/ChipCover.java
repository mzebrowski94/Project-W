package pl.mzebrows.pwt.ui.hud;

/**
 * Whether a **prompt** would hide part of the fight: an alien body the player can see under a rectangle of the world
 * (G0098). One under a tree's foliage is not seen (G0072), so it is not under anything. Answered by whatever draws
 * the world, which knows where each thing is drawn this frame.
 */
@FunctionalInterface
public interface ChipCover {

    /** Whether a seen alien body overlaps this rectangle, in world units. */
    boolean alienWithin(float left, float bottom, float right, float top);
}
