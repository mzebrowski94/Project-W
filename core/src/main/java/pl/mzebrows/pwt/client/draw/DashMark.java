package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a dash (G0107): two small thrusters firing behind his shoulders while it runs and for a short tail after,
 * and the puff it leaves where it began. The simulation says which way the dash went, whether it runs, how long ago it
 * ended and how much of a puff is left; nothing about how that looks (G0003).
 *
 * @param thruster the jets on his back
 * @param puff the smoke where it began; its colour is {@code graphic.color.puff}
 */
public record DashMark(Thruster thruster, Puff puff) {

    /**
     * Two flame cones behind his shoulders, pointing back along the dash.
     *
     * @param tailSeconds how long they go on firing, shrinking and fading, after the dash has ended
     * @param length how long a cone is, as a multiple of his radius
     * @param width how wide a cone's base is, as a multiple of his radius
     * @param apart how far either side of his middle a shoulder is, as a multiple of his radius
     * @param tipShare how much of a cone, from its base, is the white-hot tip
     * @param core the flame's colour
     * @param tip the colour of the flame at the nozzle
     */
    public record Thruster(
            float tailSeconds, float length, float width, float apart, float tipShare, Color core, Color tip) {

        public Thruster {
            if (tailSeconds < 0f || length <= 0f || width <= 0f || apart < 0f) {
                throw new IllegalArgumentException("a thruster's tail, length, width and spacing must be positive");
            }
            if (tipShare < 0f || tipShare > 1f) {
                throw new IllegalArgumentException("thruster tipShare must be in [0, 1], was " + tipShare);
            }
        }

        /**
         * How much of a cone is drawn - one while the dash runs, falling to nothing over the tail - or zero for none.
         *
         * @param ago how long ago, in seconds, the dash ended
         */
        public float strength(boolean dashing, float ago) {
            if (dashing) {
                return 1f;
            }
            if (tailSeconds <= 0f || ago >= tailSeconds) {
                return 0f;
            }
            return 1f - ago / tailSeconds;
        }
    }

    /**
     * A see-through disc growing where it was left and fading out.
     *
     * @param radiusFrom how wide it is the instant it appears, in world units
     * @param radiusTo how wide it has grown by the time it is gone
     * @param alpha how opaque it is the instant it appears
     */
    public record Puff(float radiusFrom, float radiusTo, float alpha) {

        public Puff {
            if (radiusFrom <= 0f || radiusTo < radiusFrom) {
                throw new IllegalArgumentException(
                        "a puff grows from a positive radius, was " + radiusFrom + " to " + radiusTo);
            }
            if (alpha < 0f || alpha > 1f) {
                throw new IllegalArgumentException("puff alpha must be in [0, 1], was " + alpha);
            }
        }

        /** How wide it is with {@code left} of it to go. */
        public float radiusFor(float left) {
            return radiusFrom + (radiusTo - radiusFrom) * (1f - left);
        }
    }
}
