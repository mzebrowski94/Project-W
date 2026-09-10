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
     */
    public record Settings(
            float deadZone,
            float smoothing,
            float lookAhead,
            float leadSmoothing,
            float nearZoom,
            float farZoom,
            float zoomSmoothing,
            float fitMargin) {
        public Settings {
            if (nearZoom <= 0f || farZoom < nearZoom) {
                throw new IllegalArgumentException("zoom levels must be positive with far >= near");
            }
        }
    }

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

    private float centreX;
    private float centreY;
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
        return centreX;
    }

    public float centreY() {
        return centreY;
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

    /** Applied last, so the view never shows anything beyond the edge of the map. */
    private void clampToMap(float viewWidth, float viewHeight, float mapWidth, float mapHeight) {
        float halfWidth = viewWidth * zoom * 0.5f;
        float halfHeight = viewHeight * zoom * 0.5f;
        centreX = mapWidth <= halfWidth * 2f ? mapWidth * 0.5f : clamp(centreX, halfWidth, mapWidth - halfWidth);
        centreY = mapHeight <= halfHeight * 2f ? mapHeight * 0.5f : clamp(centreY, halfHeight, mapHeight - halfHeight);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
