package pl.mzebrows.pwt.client;

/**
 * Decides where the view is and how far out it sits. Holds no GL object and never touches {@code
 * Gdx}, so all of it is unit-tested; {@link ScenarioScreen} only copies the answer onto the camera.
 *
 * <p>The rig is four things and the order matters: a <strong>dead zone</strong> the targets move
 * inside without the view reacting, <strong>look-ahead</strong> that leans the view the way they are
 * heading, framerate-independent <strong>smoothing</strong> toward that goal, and a <strong>clamp</strong>
 * to the map applied last. Clamping before smoothing makes the view stick at map edges; skipping the
 * dead zone makes it jitter on every small step.
 *
 * <p><strong>Centre and zoom are one decision, not two.</strong> Framing several targets means fitting
 * a box, and a fit that could not also move the centre would fight whatever did. That is why local
 * co-op costs nothing here: two commanders are two {@code watch} calls, and the box does the rest. A
 * separation past the far zoom stops widening rather than pulling out forever - past that the view
 * has given up, and it is the game's job not to let players get that far apart.
 *
 * <p>Look-ahead follows where the targets are <em>walking</em>, deliberately not where they are
 * aiming. Aim is a world point unprojected through this very camera, so leaning toward it moves the
 * point, which leans it further: a feedback loop that reads as the view drifting off on its own.
 * Movement input comes from the keyboard and is camera-independent, so it cannot do that.
 *
 * <p><strong>A shake is applied last of all, and outside the clamp.</strong> The jolt is kept out of
 * the smoothed centre entirely - a view that chased its own shaken position would wobble its way
 * across the level - so the smoothing state and what the camera is actually set to are two different
 * numbers, and only the second one is shaken.
 *
 * <p><strong>The shaken position is deliberately not clamped, and this is the one place the view may
 * leave the map.</strong> Clamping it was the first version and it did not work: the clamp pins the
 * centre to the middle of any level no bigger than the view, so on a map the size of the viewport -
 * which is the shipped default - the jolt was computed and then thrown away, and no explosion shook
 * anything. The resting centre is still clamped, so the view never *sits* outside the level; a shake
 * can take it out by no more than the shake itself displaces it, which is a couple of world units for
 * a fraction of a second. Re-clamping this line is the obvious tidy-up and it silently removes screen
 * shake from every small level, so do not.
 *
 * <p>How violent an event was is the simulation's to say; how much the view moves is not. The
 * simulation publishes a strength in world units and a radius it is felt within, and everything
 * here - the falloff, the waveform, how many times it swings - is a rendering decision. Zoom needs no
 * handling at all: the offset is in world units, so the pulled-back view divides it by the zoom for
 * free, and the same blast that rocks the close view is a twitch in the overview.
 *
 * <p><strong>The lead eases in on its own, and far more slowly than the view follows.</strong> Taking
 * the movement input raw makes the goal jump the whole look-ahead distance the instant the player
 * reverses, so tapping left and right shakes the view badly - which is exactly what playing it
 * showed. Easing the lead over about half a second means a flick averages out to almost nothing while
 * a sustained run still earns the full lead, and with a dead zone in front of it a player fidgeting
 * on the spot moves the camera not at all.
 */
public final class CameraDirector {

    /**
     * How the view follows what it is watching, and how far out it may sit.
     *
     * @param deadZone how far the goal may drift from the current centre before the view follows it,
     *     in world units
     * @param smoothing how fast the view closes on its goal, per second. Higher is tighter.
     * @param lookAhead how far the view leans the way the targets are walking, in world units
     * @param leadSmoothing how fast that lean builds and decays, per second. Deliberately slower than
     *     {@code smoothing}: it is what stops a change of direction snapping the view across.
     * @param nearZoom the normal view. One means the viewport's own size.
     * @param farZoom the pulled-back view, and the widest the fit is ever allowed to go
     * @param zoomSmoothing how fast the zoom eases between levels, per second
     * @param fitMargin slack left around the targets when framing more than one, as a multiplier
     * @param shakeScale how much of a published shake the view actually takes, as a multiplier. One
     *     means the strength the simulation published, in world units. Trim the feel here rather than
     *     in {@code game.properties} - how violent an event is and how much the camera moves are two
     *     different questions
     * @param shakeCycles how many times the view swings back and forth over a whole shake
     */
    public record Settings(
            float deadZone,
            float smoothing,
            float lookAhead,
            float leadSmoothing,
            float nearZoom,
            float farZoom,
            float zoomSmoothing,
            float fitMargin,
            float shakeScale,
            float shakeCycles) {
        public Settings {
            if (nearZoom <= 0f || farZoom < nearZoom) {
                throw new IllegalArgumentException("zoom levels must be positive with far >= near");
            }
        }
    }

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    /**
     * How the sideways swing relates to the forward one. Not a whole number and not one, so the view
     * traces a ragged figure rather than a circle - a circular shake reads as the camera orbiting.
     */
    private static final float SIDEWAYS_RATE = 1.6f;

    private static final float SIDEWAYS_PHASE = 1.1f;

    private final Settings settings;

    private float minX;
    private float minY;
    private float maxX;
    private float maxY;
    private int watched;

    /** What was asked for this frame, before easing. */
    private float askedX;

    private float askedY;

    /** What the view is actually leaning by, easing toward the ask. Survives {@link #begin}. */
    private float leadX;

    private float leadY;

    /** This frame's jolt, summed over everything violent near enough to be felt. Cleared each frame. */
    private float shakeX;

    private float shakeY;

    private float centreX;
    private float centreY;

    /** Where the camera is actually put: the smoothed centre, shaken, then clamped to the map. */
    private float viewX;

    private float viewY;

    private float zoom;
    private float baseZoom;
    private boolean wide;
    private boolean placed;

    public CameraDirector(Settings settings) {
        this.settings = settings;
        this.baseZoom = settings.nearZoom();
        this.zoom = settings.nearZoom();
    }

    /** Starts a frame's worth of targets. */
    public void begin() {
        watched = 0;
        askedX = 0f;
        askedY = 0f;
        shakeX = 0f;
        shakeY = 0f;
    }

    /** Adds something the view must keep in shot. */
    public void watch(float x, float y) {
        if (watched == 0) {
            minX = x;
            maxX = x;
            minY = y;
            maxY = y;
        } else {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        watched++;
    }

    /**
     * Adds something violent that happened this frame, as the simulation published it: where, how hard
     * at the middle of it, how far it is felt, and how much of it is left.
     *
     * <p>Distance is measured from the targets rather than from the camera, so call it after
     * {@link #watch}. It matters at the level's edges, where the view is clamped some way off whoever
     * it is watching: what should decide how hard a blast is felt is how close the player was to it,
     * not how close the camera happened to be allowed to get.
     *
     * <p>The waveform is a function of how far through the shake is and of nothing else - no clock, no
     * random numbers - so the same explosion looks the same on every replay, and a paused frame is
     * still a frame the view is not drifting in.
     */
    public void shake(float x, float y, float strength, float radius, float fade) {
        if (strength <= 0f || radius <= 0f || fade <= 0f) {
            return;
        }
        float fromX = watched > 0 ? (minX + maxX) * 0.5f : centreX;
        float fromY = watched > 0 ? (minY + maxY) * 0.5f : centreY;
        float distance = (float) Math.hypot(x - fromX, y - fromY);
        if (distance >= radius) {
            return;
        }
        float felt = strength * settings.shakeScale() * fade * (1f - distance / radius);
        float phase = fade * settings.shakeCycles() * TWO_PI;
        shakeX += felt * (float) Math.cos(phase);
        shakeY += felt * (float) Math.cos(phase * SIDEWAYS_RATE + SIDEWAYS_PHASE);
    }

    /** Where the targets are walking, as a direction of roughly unit length. */
    public void heading(float x, float y) {
        askedX = x;
        askedY = y;
    }

    /** Swaps between the normal and the pulled-back view. */
    public void toggleWide() {
        wide = !wide;
        baseZoom = wide ? settings.farZoom() : settings.nearZoom();
    }

    /** Whether the pulled-back view is currently selected. */
    public boolean wide() {
        return wide;
    }

    /** Moves the view one frame towards where it should be, then keeps it inside the map. */
    public void update(float viewWidth, float viewHeight, float mapWidth, float mapHeight, float deltaSeconds) {
        float goalZoom = baseZoom;
        if (watched > 0) {
            float needed = Math.max((maxX - minX) / viewWidth, (maxY - minY) / viewHeight) * settings.fitMargin();
            goalZoom = clamp(Math.max(baseZoom, needed), settings.nearZoom(), settings.farZoom());
        }
        float leadApproach = approach(settings.leadSmoothing(), deltaSeconds);
        leadX += (askedX - leadX) * leadApproach;
        leadY += (askedY - leadY) * leadApproach;

        float goalX = centreX;
        float goalY = centreY;
        if (watched > 0) {
            goalX = (minX + maxX) * 0.5f + leadX * settings.lookAhead();
            goalY = (minY + maxY) * 0.5f + leadY * settings.lookAhead();
        }

        if (!placed) {
            centreX = goalX;
            centreY = goalY;
            zoom = goalZoom;
            placed = true;
        } else {
            goalX = beyondDeadZone(centreX, goalX);
            goalY = beyondDeadZone(centreY, goalY);
            float approach = approach(settings.smoothing(), deltaSeconds);
            centreX += (goalX - centreX) * approach;
            centreY += (goalY - centreY) * approach;
            zoom += (goalZoom - zoom) * approach(settings.zoomSmoothing(), deltaSeconds);
        }
        clampToMap(viewWidth, viewHeight, mapWidth, mapHeight);
    }

    public float centreX() {
        return viewX;
    }

    public float centreY() {
        return viewY;
    }

    public float zoom() {
        return zoom;
    }

    /**
     * Only the part of the goal that is outside the dead zone is chased, so a target fidgeting near
     * the middle of the view moves nothing at all.
     */
    private float beyondDeadZone(float from, float goal) {
        float offset = goal - from;
        if (Math.abs(offset) <= settings.deadZone()) {
            return from;
        }
        return from + Math.signum(offset) * (Math.abs(offset) - settings.deadZone());
    }

    /**
     * Exponential approach, framerate-independent. A plain {@code rate * delta} lerp moves further per
     * second at high frame rates than at low ones, so the camera feels different on a 144Hz monitor -
     * which is exactly the kind of bug nobody reproduces.
     */
    private static float approach(float rate, float deltaSeconds) {
        return 1f - (float) Math.exp(-rate * deltaSeconds);
    }

    /**
     * Applied last, so the view never comes to rest showing anything beyond the edge of the map. The
     * shaken position is offset from the clamped one rather than clamped itself - see the class note.
     */
    private void clampToMap(float viewWidth, float viewHeight, float mapWidth, float mapHeight) {
        centreX = clampAxis(centreX, viewWidth * zoom, mapWidth);
        centreY = clampAxis(centreY, viewHeight * zoom, mapHeight);
        viewX = centreX + shakeX;
        viewY = centreY + shakeY;
    }

    /** One axis of that: centred on a level narrower than the view, held inside a wider one. */
    private static float clampAxis(float value, float viewExtent, float mapExtent) {
        float half = viewExtent * 0.5f;
        return mapExtent <= half * 2f ? mapExtent * 0.5f : clamp(value, half, mapExtent - half);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
