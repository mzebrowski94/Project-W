package pl.mzebrows.pwt.ui.hud;

import java.util.Arrays;
import pl.mzebrows.pwt.sim.Players;

/**
 * How see-through each slot's **prompt** backing is: its own alpha, easing to a thinner one while an alien body is
 * under the prompt so the fight shows through it, and back once it has gone (G0098). A straight-line ease over a
 * fixed time, so how fast it goes does not depend on how far it has to.
 */
public final class ChipFade {

    private final float alpha;
    private final float occludedAlpha;
    private final float fadeSeconds;
    private final float[] alphas = new float[Players.MAX];

    /**
     * @param alpha the backing's alpha over open ground
     * @param occludedAlpha its alpha while an alien body is under it
     * @param fadeSeconds how long the whole way from one to the other takes; zero is at once
     */
    public ChipFade(float alpha, float occludedAlpha, float fadeSeconds) {
        if (!(alpha >= 0f && alpha <= 1f && occludedAlpha >= 0f && occludedAlpha <= 1f && fadeSeconds >= 0f)) {
            throw new IllegalArgumentException("a prompt's alphas are shares and its fade is not negative: " + alpha
                    + ", " + occludedAlpha + ", " + fadeSeconds);
        }
        this.alpha = alpha;
        this.occludedAlpha = occludedAlpha;
        this.fadeSeconds = fadeSeconds;
        Arrays.fill(alphas, alpha);
    }

    /** Moves this slot's backing towards where it belongs this frame. */
    public void step(int slot, boolean occluded, float delta) {
        float target = occluded ? occludedAlpha : alpha;
        if (fadeSeconds == 0f) {
            alphas[slot] = target;
            return;
        }
        float most = Math.abs(alpha - occludedAlpha) * delta / fadeSeconds;
        float now = alphas[slot];
        alphas[slot] = now < target ? Math.min(target, now + most) : Math.max(target, now - most);
    }

    public float alpha(int slot) {
        return alphas[slot];
    }
}
