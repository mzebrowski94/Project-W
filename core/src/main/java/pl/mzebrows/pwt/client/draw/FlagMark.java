package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * How to draw a control point once it is taken: a pole at its middle flying a small flag, and a static ring round
 * its edge in the flag's colour, in place of the full charge core - so "taken" never reads as "nearly taken"
 * (pillar 5). The flag's blue is the player's side, light; never red, which is the aliens'.
 *
 * <p>The simulation says only that the point is full (G0003); the flag waves on the client's own clock, times
 * {@code graphic.ui.motionScale}, so zero holds it still.
 *
 * <p>Every size is a share of the point's radius, as a post's outline is.
 *
 * @param color the flag's and the ring's colour
 * @param pole the pole's colour
 * @param poleHeight how tall the pole stands, up from the point's middle
 * @param poleWidth how thick the pole is
 * @param width how far the flag reaches out from the pole
 * @param height how far the flag hangs down from the pole's top
 * @param slices how many strips the flag is drawn in; more is a smoother wave
 * @param waveHz how many times a second a crest runs along it
 * @param waves how many crests lie along it at once
 * @param sway how far its free end rises and falls, as a share of its height
 * @param edge how thick the ring round the point is
 * @param segments how many straight pieces the ring is drawn in
 */
public record FlagMark(
        Color color,
        Color pole,
        float poleHeight,
        float poleWidth,
        float width,
        float height,
        int slices,
        float waveHz,
        float waves,
        float sway,
        float edge,
        int segments) {

    public FlagMark {
        if (poleHeight <= 0f || poleWidth <= 0f || width <= 0f || height <= 0f || edge <= 0f) {
            throw new IllegalArgumentException("a flag's sizes must be positive");
        }
        if (height > poleHeight) {
            throw new IllegalArgumentException("a flag may hang no lower than its pole's foot, was " + height);
        }
        if (slices < 1) {
            throw new IllegalArgumentException("a flag is at least one strip, was " + slices);
        }
        if (segments < 3) {
            throw new IllegalArgumentException("a flag's ring needs at least three pieces, was " + segments);
        }
        if (!(waveHz >= 0f && waveHz <= GraphicConfig.MAX_PULSE_HZ)) {
            throw new IllegalArgumentException(
                    "a flag may wave at most " + GraphicConfig.MAX_PULSE_HZ + " times a second, not " + waveHz);
        }
        if (waves < 0f || sway < 0f || sway > 1f) {
            throw new IllegalArgumentException("a flag's wave out of range");
        }
    }

    /**
     * Whether this flies a flag: a control point that is full. A point reports full only once it is taken, so this
     * reads what the point publishes and never asks what the entity is (G0003). The finale's point is one too.
     */
    public static boolean flies(Appearance appearance) {
        return appearance.look == Look.CONTROL_POINT && appearance.charge >= 1f;
    }

    /** How tall the pole stands on a point of this radius. */
    public float poleHeightFor(float radius) {
        return radius * poleHeight;
    }

    /** How thick the pole is on a point of this radius. */
    public float poleWidthFor(float radius) {
        return radius * poleWidth;
    }

    /** How far the flag reaches out from its pole on a point of this radius. */
    public float widthFor(float radius) {
        return radius * width;
    }

    /** How far the flag hangs from the pole's top on a point of this radius. */
    public float heightFor(float radius) {
        return radius * height;
    }

    /**
     * How far the flag's strip edge {@code at} - zero at the pole, {@link #slices} at its free end - stands above
     * where it hangs still, this many seconds in: pinned at the pole and swaying most at the free end, as a crest
     * runs out along it. Zero motion holds the whole flag still.
     */
    public float liftAt(int at, float radius, float seconds, float motionScale) {
        float along = (float) at / slices;
        double phase = 2.0 * Math.PI * (waveHz * seconds - waves * along);
        return heightFor(radius) * sway * motionScale * along * (float) Math.sin(phase);
    }

    /** How thick the ring is round a point of this radius. */
    public float edgeFor(float radius) {
        return radius * edge;
    }

    /** Where the ring runs, so all of it lies inside the point's edge - a post's outline lies just outside it. */
    public float ringFor(float radius) {
        return radius - edgeFor(radius) * 0.5f;
    }
}
