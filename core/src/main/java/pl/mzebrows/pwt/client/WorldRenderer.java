package pl.mzebrows.pwt.client;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import lombok.RequiredArgsConstructor;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * Draws the simulation as coloured circles.
 *
 * <p>This is the placeholder renderer: until real art exists every entity is a shape, so gameplay
 * can be built and looked at without a single texture. It decides what to draw by reading an
 * entity's {@link Appearance} and asks nothing else about it (G0003). Once the atlas lands, a sprite
 * renderer replaces this class and reads the same component; the simulation is still never asked how
 * to draw itself.
 *
 * <p>A unit acting on an order carries a ring, so the player can see who heard him - which is how
 * he learns how far his voice carries rather than guessing at it (pillar 5).
 *
 * <p>Which way an entity is pointing is drawn as a spoke out of its centre, for anything that
 * publishes a facing. That is a tell rather than decoration: a trooper only engages what is inside
 * its arc, so the player has to be able to see where its front is (G0015).
 *
 * <p>What an entity is doing is drawn as a shift in its colour rather than as a different colour per
 * situation, so the same tell reads the same way on every kind of unit (pillar 5).
 *
 * <p>Positions are interpolated between the previous tick and the current one. The simulation runs at
 * a fixed 60 ticks per second while the window may refresh at 144, so without this every entity
 * visibly steps. The cost is that what you see lags the simulation by up to one tick, which is the
 * standard trade and invisible in play.
 */
@RequiredArgsConstructor
public final class WorldRenderer implements Disposable {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Appearance> APPEARANCE = ComponentMapper.getFor(Appearance.class);
    private static final Family DRAWABLE = Family.all(
                    Position.class, PreviousPosition.class, Body.class, Appearance.class)
            .get();
    private static final int CIRCLE_SEGMENTS = 16;

    private final Viewport viewport;
    private final Palette palette;
    private final StateTint stateTint;
    private final ChargeMark chargeMark;
    private final NestMark nestMark;
    private final FacingMark facingMark;
    private final ChargedMark chargedMark;
    private final OrderMark orderMark;
    private final HeatMark heatMark;

    private final ShapeRenderer shapes = new ShapeRenderer();

    /** Reused every entity every frame: tinting must not allocate a colour per circle per frame. */
    private final Color tinted = new Color();

    /** Cached: Ashley keeps this view live, so it must not be re-queried per frame. */
    private ImmutableArray<Entity> drawable = new ImmutableArray<>(new Array<>());

    private boolean disposed;

    /** Points the renderer at a world. Call again after a restart - a new simulation is a new engine. */
    public void bind(Simulation simulation) {
        drawable = simulation.engine().getEntitiesFor(DRAWABLE);
    }

    /** Draws one frame. {@code alpha} is how far the clock is towards the next tick, in {@code [0, 1)}. */
    public void render(float alpha) {
        if (disposed) {
            // Belt and braces. Drawing with a disposed ShapeRenderer writes to freed GL memory and
            // takes the JVM down with an access violation and no stack trace, so this turns the
            // worst possible failure into a visible, reportable one.
            Log.error("WorldRenderer.render called after dispose; nothing drawn", null);
            return;
        }
        viewport.apply();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawGround(alpha);
        for (int i = 0; i < drawable.size(); i++) {
            Entity entity = drawable.get(i);
            Position position = POSITION.get(entity);
            PreviousPosition previous = PREVIOUS.get(entity);
            Body body = BODY.get(entity);
            Appearance appearance = APPEARANCE.get(entity);
            if (ground(appearance.look)) {
                // Already drawn, underneath everything.
                continue;
            }
            if (appearance.state == Appearance.State.ABOARD) {
                // Inside something rather than standing on the ground. What the player sees is the
                // thing he is in, which says for itself that somebody is on it.
                continue;
            }
            float x = previous.x + (position.x - previous.x) * alpha;
            float y = previous.y + (position.y - previous.y) * alpha;
            shapes.setColor(tint(appearance));
            if (square(appearance.look)) {
                shapes.rect(x - body.radius, y - body.radius, body.radius * 2f, body.radius * 2f);
            } else if (shelled(appearance.look)) {
                drawShells(x, y, body.radius, appearance);
            } else {
                shapes.circle(x, y, body.radius, CIRCLE_SEGMENTS);
            }
            if (appearance.facingX != 0f || appearance.facingY != 0f) {
                shapes.setColor(brightened(tint(appearance), facingMark.tint()));
                float reach = facingMark.lengthFor(body.radius);
                shapes.rectLine(
                        x,
                        y,
                        x + appearance.facingX * reach,
                        y + appearance.facingY * reach,
                        facingMark.widthFor(body.radius));
            }
            if (appearance.charge > 0f) {
                // A charge that names a look of its own is drawn in that colour; everything else is
                // the body's own colour brightened, which is what the mark has always been. The
                // simulation says what the charge is and the palette says what that looks like
                // (G0003) - there is no branch here on what the entity is.
                shapes.setColor(
                        appearance.chargeLook == Look.NONE
                                ? brightened(tint(appearance), chargeMark.tint())
                                : palette.colorFor(appearance.chargeLook));
                shapes.circle(x, y, chargeMark.radiusFor(body.radius, appearance.charge), CIRCLE_SEGMENTS);
            }
            if (appearance.heat > 0f) {
                drawHeat(x, y, body.radius, appearance);
            }
        }
        shapes.end();
        drawRings();
    }

    /**
     * Everything that is ground rather than something standing on it, drawn first so that nothing
     * underfoot can hide a man standing in it (pillar 5).
     *
     * <p>A control point says the same thing by being spawned before anything stands on it, and
     * that is the cheaper answer where it works. It does not work for ruined ground, which appears
     * in the middle of a fight - so this is a pass rather than an ordering, and it is the third
     * thing here that asks what a look is, beside the square one and the shelled one.
     */
    private void drawGround(float alpha) {
        for (int i = 0; i < drawable.size(); i++) {
            Entity entity = drawable.get(i);
            Appearance appearance = APPEARANCE.get(entity);
            if (!ground(appearance.look)) {
                continue;
            }
            Position position = POSITION.get(entity);
            PreviousPosition previous = PREVIOUS.get(entity);
            float x = previous.x + (position.x - previous.x) * alpha;
            float y = previous.y + (position.y - previous.y) * alpha;
            shapes.setColor(tint(appearance));
            shapes.circle(x, y, BODY.get(entity).radius, CIRCLE_SEGMENTS);
        }
    }

    /** Whether a look is underfoot rather than standing on the ground. */
    private static boolean ground(Look look) {
        return look == Look.SLICK;
    }

    /**
     * The rings: one round every unit acting on an order (3.f), one round every unit whose gun goes
     * off where its shots land.
     *
     * <p>A second pass in outline rather than more filled circles, so a ring never hides the unit
     * inside it - and outlines cannot be drawn in the same {@code ShapeRenderer} pass as fills. Both
     * rings share the pass because both are outlines; a pass each would cost a state change to say
     * the same thing twice.
     *
     * <p>The pass is skipped entirely when there is nothing to ring, which is most of the time, so it
     * costs a walk of the list rather than a state change.
     */
    private void drawRings() {
        boolean any = false;
        for (int i = 0; i < drawable.size(); i++) {
            Appearance appearance = APPEARANCE.get(drawable.get(i));
            if (appearance.ordered || appearance.charged) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < drawable.size(); i++) {
            Entity entity = drawable.get(i);
            Appearance appearance = APPEARANCE.get(entity);
            Position position = POSITION.get(entity);
            float radius = BODY.get(entity).radius;
            if (appearance.charged) {
                // Drawn first, so a trooper that somehow had both keeps its order ring outermost -
                // whose man it is matters more at a glance than what he is holding.
                shapes.setColor(chargedMark.color());
                shapes.circle(position.x, position.y, chargedMark.radiusFor(radius), CIRCLE_SEGMENTS);
            }
            if (appearance.ordered) {
                shapes.setColor(brightened(tint(appearance), orderMark.tint()));
                shapes.circle(position.x, position.y, orderMark.radiusFor(radius), CIRCLE_SEGMENTS);
            }
        }
        shapes.end();
    }

    /**
     * The heat bar: an arc over the thing holding the gun, filling from left to right.
     *
     * <p>Drawn in straight pieces rather than as an arc primitive, because {@code ShapeRenderer}'s
     * own arc is a pie slice - it would draw two spokes back to the middle of the unit and read as a
     * wedge rather than as a gauge. Only ever reached by something that is actually hot, which is
     * one or two things on the worst tick.
     */
    private void drawHeat(float x, float y, float radius, Appearance appearance) {
        shapes.setColor(palette.colorFor(appearance.overheated ? Look.OVERHEAT : Look.HEAT));
        float arc = heatMark.radiusFor(radius);
        float width = heatMark.widthFor(radius);
        int steps = heatMark.segmentsFor(appearance.heat);
        float fromX = x + MathUtils.cosDeg(heatMark.angleAt(0)) * arc;
        float fromY = y + MathUtils.sinDeg(heatMark.angleAt(0)) * arc;
        for (int step = 1; step <= steps; step++) {
            float angle = heatMark.angleAt(step);
            float toX = x + MathUtils.cosDeg(angle) * arc;
            float toY = y + MathUtils.sinDeg(angle) * arc;
            shapes.rectLine(fromX, fromY, toX, toY, width);
            fromX = toX;
            fromY = toY;
        }
    }

    /**
     * A hole in the ground: rings, darkening inwards.
     *
     * <p>Drawn outermost first so each one sits on top of the last, which is what makes the shading
     * read as depth rather than as concentric bands. It reuses the same scratch colour every ring, so
     * a nest costs no allocation per frame either.
     */
    private void drawShells(float x, float y, float radius, Appearance appearance) {
        for (int shell = 0; shell < nestMark.shells(); shell++) {
            shapes.setColor(darkened(tint(appearance), nestMark.darkenAt(shell)));
            shapes.circle(x, y, nestMark.radiusAt(shell, radius), CIRCLE_SEGMENTS);
        }
    }

    /**
     * Whether a look is drawn as rings rather than as one filled circle.
     *
     * <p>The client's decision, exactly as {@link #square} is: the simulation names a role and never
     * a shape (G0003). A nest is dug ground, and dug ground drawn as a body reads as an enormous slow
     * creature.
     */
    private static boolean shelled(Look look) {
        return look == Look.NEST;
    }

    /**
     * Whether a look is drawn square rather than round.
     *
     * <p>The client's decision, like the colour beside it: the simulation names a role and never a
     * shape (G0003), and how a role is drawn is this module's to change. Everything alive is round
     * and the one piece of equipment is not, which is the whole of the rule so far - a body is still
     * a circle either way (ADR 0007), so this changes what is seen and nothing about what is hit.
     */
    private static boolean square(Look look) {
        return look == Look.TURRET;
    }

    /**
     * The entity's colour shifted for what it is doing: toward white for a tell the player must not
     * miss, toward black for a unit that has stopped being a threat. Returns the shared scratch
     * colour, which {@code ShapeRenderer} copies out of immediately.
     */
    private Color tint(Appearance appearance) {
        tinted.set(palette.colorFor(appearance.look, appearance.variant));
        float shift = stateTint.forState(appearance.state);
        if (shift == 0f) {
            return tinted;
        }
        float target = shift > 0f ? 1f : 0f;
        float amount = Math.abs(shift);
        tinted.r += (target - tinted.r) * amount;
        tinted.g += (target - tinted.g) * amount;
        tinted.b += (target - tinted.b) * amount;
        return tinted;
    }

    /**
     * The same colour shifted toward black, for a ring further into a hole. Mutates and returns the
     * shared scratch colour, exactly as {@link #brightened} does.
     */
    private static Color darkened(Color color, float amount) {
        color.r -= color.r * amount;
        color.g -= color.g * amount;
        color.b -= color.b * amount;
        return color;
    }

    /**
     * The same colour shifted toward white, for the core drawn inside something that is filling up.
     * Mutates and returns the shared scratch colour, which the caller has just been handed.
     */
    private static Color brightened(Color color, float amount) {
        color.r += (1f - color.r) * amount;
        color.g += (1f - color.g) * amount;
        color.b += (1f - color.b) * amount;
        return color;
    }

    @Override
    public void dispose() {
        disposed = true;
        drawable = new ImmutableArray<>(new Array<>());
        shapes.dispose();
    }
}
