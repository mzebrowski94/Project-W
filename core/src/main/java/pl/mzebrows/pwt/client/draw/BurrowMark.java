package pl.mzebrows.pwt.client.draw;

/**
 * How to draw a worm (G0096): under the ground its shadow, a see-through black disc its size, fainter while it rests
 * and may not come up; up, a ring of teeth pointing inwards - shrinking onto it while its maw closes, round its hole
 * while it stands - one tooth fewer for every tooth's worth of health it has lost (pillar 5).
 *
 * @param shadowAlpha how dark its shadow is while it may come up
 * @param restAlpha how dark while it rests - fainter, so a worm that cannot surface yet reads as one
 * @param ringWidth how thick the maw's ring is, in world units
 * @param ringAlpha how solid the ring and its teeth are
 * @param toothLength how far a tooth reaches in from the ring, as a fraction of the worm's radius
 * @param toothWidth how wide a tooth is at the ring, as a fraction of the worm's radius
 */
public record BurrowMark(
        float shadowAlpha, float restAlpha, float ringWidth, float ringAlpha, float toothLength, float toothWidth) {

    public BurrowMark {
        if (shadowAlpha < 0f
                || shadowAlpha > 1f
                || restAlpha < 0f
                || restAlpha > 1f
                || ringAlpha < 0f
                || ringAlpha > 1f) {
            throw new IllegalArgumentException("burrow alphas must be in [0, 1]");
        }
        if (restAlpha > shadowAlpha) {
            throw new IllegalArgumentException(
                    "a resting worm's shadow is fainter, never darker, than one that may come up");
        }
        if (ringWidth <= 0f || toothLength <= 0f || toothWidth <= 0f) {
            throw new IllegalArgumentException("burrow ringWidth, toothLength and toothWidth must be positive");
        }
    }
}
