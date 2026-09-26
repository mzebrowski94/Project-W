package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;

/**
 * How the bars look and move, under {@code graphic.hud} and {@code graphic.ui}. The HUD stands out from
 * the fight by place and treatment, not by hue - every hue already means something in the world - so
 * its text is neutral: bright for what is current, dim for what is static or old. A player's colour and
 * the heat colours are not repeated here; the world's own keys are read (plan 7.0).
 *
 * @param text what is current - the goal, the newest line
 * @param textDim what is static or old - the mission, an older line, a tally
 * @param alert the one warning colour, only ever as inverse video inside the bars: DOWN, KIA, OVERHEAT
 * @param overlayFrame the dev overlay's border
 * @param bar what the bars and the pillars beside the view are filled with
 * @param overlayWidthShare the share of the top bar's content the dev overlay panel takes, on the right
 * @param motionScale every UI duration and amplitude is multiplied by this; 0 is reduced motion
 * @param flashScale 0 turns every UI flash and pulse into a steady change
 * @param alarmSeconds how long a panel's frame flashes and shakes as its player goes down, dies or
 *     overheats
 * @param alarmShake how far the frame shakes, in UI pixels
 * @param pulseHz how fast an alert pulses; refused above three a second (WCAG 2.3.1)
 * @param goalHoldSeconds the least time a goal stays on the goal line before another replaces it
 * @param reticleSize the reticle's side in texels, drawn at the UI scale
 * @param keyboardReticle whether the keyboard player aims with the game's reticle instead of the
 *     operating system's pointer
 * @param padReticles whether each pad player gets a reticle a fixed distance along his aim
 * @param padReticleDistance how far along his aim a pad player's reticle sits, in world units
 * @param rollCallAtStart whether a level opens on the roll call rather than the players' panels
 * @param scrimAlpha how dark the view goes under a menu - dark enough to say the world is not live,
 *     light enough to read the frozen fight
 * @param bannerScrimAlpha how dark it goes under the paused banner, with no menu open here
 * @param scrimSeconds how long the scrim takes to come and go, before the motion scale
 * @param pageSeconds how long a page takes to arrive, before the motion scale
 * @param pageSlide how far an arriving page slides, in UI pixels
 * @param pageColumns how many characters wide every page's panel is, whatever it holds
 */
public record HudStyle(
        Color text,
        Color textDim,
        Color alert,
        Color overlayFrame,
        Color bar,
        float overlayWidthShare,
        float motionScale,
        float flashScale,
        float alarmSeconds,
        int alarmShake,
        float pulseHz,
        float goalHoldSeconds,
        int reticleSize,
        boolean keyboardReticle,
        boolean padReticles,
        float padReticleDistance,
        boolean rollCallAtStart,
        float scrimAlpha,
        float bannerScrimAlpha,
        float scrimSeconds,
        float pageSeconds,
        int pageSlide,
        int pageColumns) {

    /** Nothing may flash more often than this a second - the photosensitive-seizure threshold. */
    public static final float MAX_PULSE_HZ = 3f;

    public HudStyle {
        if (!(overlayWidthShare >= 0f && overlayWidthShare < 1f)) {
            throw new IllegalArgumentException("overlay width share is a fraction below one: " + overlayWidthShare);
        }
        if (!(pulseHz >= 0f && pulseHz <= MAX_PULSE_HZ)) {
            throw new IllegalArgumentException(
                    "an alert may pulse at most " + MAX_PULSE_HZ + " times a second, not " + pulseHz);
        }
        if (motionScale < 0f
                || flashScale < 0f
                || alarmSeconds < 0f
                || alarmShake < 0
                || reticleSize < 1
                || padReticleDistance < 0f) {
            throw new IllegalArgumentException("HUD motion out of range");
        }
    }
}
