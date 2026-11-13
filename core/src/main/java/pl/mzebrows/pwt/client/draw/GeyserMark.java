package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a geyser filling (G0089): the fire mark at its core, growing as it fills and pulsing between the fire's
 * own colour and a light one - the warning, so the eruption that follows is never a surprise and never a number to
 * count down (pillar 5).
 *
 * <p>The pulse runs on the client's own clock; the simulation says only how far it has filled (G0003).
 *
 * @param light the colour it pulses up to; the fire's palette colour is the one it pulses down to
 * @param pulseHz how many times a second it pulses
 */
public record GeyserMark(Color light, float pulseHz) {

    public GeyserMark {
        if (!(pulseHz >= 0f && pulseHz <= GraphicConfig.MAX_PULSE_HZ)) {
            throw new IllegalArgumentException(
                    "a geyser may pulse at most " + GraphicConfig.MAX_PULSE_HZ + " times a second, not " + pulseHz);
        }
    }

    /** How far towards {@link #light} it is this many seconds in, in {@code [0, 1]}: a sine, so it eases. */
    public float lightAt(float seconds) {
        return 0.5f - 0.5f * (float) Math.cos(seconds * pulseHz * 2f * Math.PI);
    }
}
