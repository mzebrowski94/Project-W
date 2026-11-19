package pl.mzebrows.pwt.client.draw;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.Log;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Armour;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Burrow;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Slither;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Side;

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
 * publishes a facing - a creature's as a spike ({@link FacingMark}). That is a tell rather than decoration: a trooper only engages what is inside
 * its arc, so the player has to be able to see where its front is (G0015).
 *
 * <p>What an entity is doing is drawn as a shift in its colour rather than as a different colour per
 * situation, so the same tell reads the same way on every kind of unit (pillar 5).
 *
 * <p><strong>One thing is drawn as a line rather than as a dot</strong>, and it reads the same
 * component as everything else: an entity that publishes a trail is drawn from that point to where
 * it is now. A gun firing forty times a second is a stream of dots unless the dots are joined, and
 * every pixel of the line is on the path a real shot actually took - there is no hitscan behind it,
 * and the renderer is not inventing geometry (G0003).
 *
 * <p>Blending is on for the whole shape pass. Everything in it is opaque but one mark - the ring on
 * a thing being pumped up, which has to be seen through - and turning it on once costs nothing that
 * a flush per mark would not cost more of.
 *
 * <p><strong>Trees are drawn last</strong> (G0072): their foliage hides everything beneath it, and the trunk is
 * drawn over the foliage, so the part that stops a man is always seen.
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
    private static final ComponentMapper<Facing> FACING = ComponentMapper.getFor(Facing.class);
    private static final ComponentMapper<Armour> ARMOUR = ComponentMapper.getFor(Armour.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Slither> SLITHER = ComponentMapper.getFor(Slither.class);
    private static final ComponentMapper<Burrow> BURROW = ComponentMapper.getFor(Burrow.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);

    private static final Family HANDLED = Family.all(Handle.class).get();

    /** Where a cosmetic lead writes the facing it draws; reused, so it costs nothing per frame. */
    private final float[] ahead = new float[2];

    private static final Family DRAWABLE = Family.all(
                    Position.class, PreviousPosition.class, Body.class, Appearance.class)
            .get();
    /**
     * What has no body is drawn from its appearance alone, as the squares it publishes - a door, its doorway's
     * tiles (G0069). It is not round and is never hit as a circle (ADR 0022), so it is not among the bodies.
     */
    private static final Family SQUARED = Family.all(Position.class, PreviousPosition.class, Appearance.class)
            .exclude(Body.class)
            .get();

    private static final int CIRCLE_SEGMENTS = 16;

    private static final int HEXAGON_SEGMENTS = 6;

    private static final int PENTAGON_CORNERS = 5;

    /** A unit pentagon's corners, the first due east: turned per stone by one sine and one cosine, not five of each. */
    private static final float[] PENTAGON_X = new float[PENTAGON_CORNERS];

    private static final float[] PENTAGON_Y = new float[PENTAGON_CORNERS];

    static {
        for (int corner = 0; corner < PENTAGON_CORNERS; corner++) {
            double angle = Math.PI * 2.0 * corner / PENTAGON_CORNERS;
            PENTAGON_X[corner] = (float) Math.cos(angle);
            PENTAGON_Y[corner] = (float) Math.sin(angle);
        }
    }

    /** A tree's foliage is several bodies wide; sixteen sides read as a polygon at that size. */
    private static final int FOLIAGE_SEGMENTS = 28;

    /** A slam's circle is many bodies wide; sixteen sides read as a polygon at that size. */
    private static final int BURST_SEGMENTS = 40;

    /** Reused, so drawing a see-through mark costs no colour per entity per frame. */
    private static final Color SCRATCH = new Color();

    private final Viewport viewport;
    private final Palette palette;
    private final StateTint stateTint;
    private final ChargeMark chargeMark;
    private final BloatMark bloatMark;
    private final NestMark nestMark;
    private final FacingMark facingMark;
    private final SegmentMark segmentMark;
    private final ChargedMark chargedMark;
    private final OrderMark orderMark;
    private final HeatMark heatMark;
    private final WoundMark woundMark;
    private final ShieldMark shieldMark;
    private final CrateMark crateMark;
    private final DoorMark doorMark;
    private final PostMark postMark;
    private final FlagMark flagMark;
    private final BurstMark burstMark;
    private final DazeMark dazeMark;
    private final TreeMark treeMark;
    private final BoulderMark boulderMark;
    private final SlingMark slingMark;
    private final ShadowMark shadowMark;
    private final PopMark popMark;
    private final GeyserMark geyserMark;
    private final SlitherMark slitherMark;
    private final BurrowMark burrowMark;
    private final DevourMark devourMark;
    private final LimpetMark limpetMark;

    /** How far anything drawn on the client's clock moves - a taken point's flag; zero holds it still. */
    private final float motionScale;

    /**
     * The client's own clock, for the post's and a geyser's pulse and a taken point's flag alone - a look, never read
     * back by the simulation.
     */
    private float pulseSeconds;

    private final ShapeRenderer shapes = new ShapeRenderer();

    /** Reused every entity every frame: tinting must not allocate a colour per circle per frame. */
    private final Color tinted = new Color();

    /** Cached: Ashley keeps this view live, so it must not be re-queried per frame. */
    private ImmutableArray<Entity> drawable = new ImmutableArray<>(new Array<>());

    private ImmutableArray<Entity> squared = new ImmutableArray<>(new Array<>());

    /** Every entity with a handle, to find a clinch's partner by (G0099). */
    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

    /** How long one tick of the bound world is: what a devour's sway counts its phase in. */
    private float tickSeconds;

    /** What {@link #devour} worked out for the entity being drawn: its radius's scale and how far it is swayed. */
    private float devourScale;

    private float swayX;

    private float swayY;

    private boolean disposed;

    /** Points the renderer at a world. Call again after a restart - a new simulation is a new engine. */
    public void bind(Simulation simulation) {
        drawable = simulation.engine().getEntitiesFor(DRAWABLE);
        squared = simulation.engine().getEntitiesFor(SQUARED);
        handled = simulation.engine().getEntitiesFor(HANDLED);
        tickSeconds = simulation.config().tickSeconds();
    }

    /**
     * Draws one frame. {@code alpha} is how far the clock is towards the next tick, in {@code [0, 1)}.
     * With {@code lead}, a commander played on this machine is drawn facing its player's aim this frame
     * rather than the world's - the cosmetic lead (ADR 0012); null draws the world as it is.
     */
    public void render(float alpha, @Nullable LocalAim lead) {
        render(alpha, lead, null);
    }

    /**
     * The same, with the action key's brackets for this machine's commanders (G0098) drawn over everything on the
     * ground and under the trees, which still hide what is beneath them (G0072).
     */
    public void render(float alpha, @Nullable LocalAim lead, @Nullable ActionBrackets brackets) {
        if (disposed) {
            // Belt and braces. Drawing with a disposed ShapeRenderer writes to freed GL memory and
            // takes the JVM down with an access violation and no stack trace, so this turns the
            // worst possible failure into a visible, reportable one.
            Log.error("WorldRenderer.render called after dispose; nothing drawn", null);
            return;
        }
        pulseSeconds += Gdx.graphics.getDeltaTime();
        viewport.apply();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawGround(alpha);
        drawSquared(alpha);
        for (int i = 0; i < drawable.size(); i++) {
            Entity entity = drawable.get(i);
            Position position = POSITION.get(entity);
            PreviousPosition previous = PREVIOUS.get(entity);
            Appearance appearance = APPEARANCE.get(entity);
            if (ground(appearance.look) || appearance.look == Look.TREE) {
                // Ground is already drawn, underneath everything; a tree is drawn last, over everything (G0072).
                continue;
            }
            if (appearance.state == Appearance.State.ABOARD) {
                // Inside something rather than standing on the ground. What the player sees is the
                // thing he is in, which says for itself that somebody is on it.
                continue;
            }
            devour(entity, alpha);
            float x = Drawn.x(position, previous, alpha) + swayX;
            float y = Drawn.y(position, previous, alpha) + swayY;
            float radius = BODY.get(entity).radius * devourScale;
            if (appearance.pop > 0f) {
                // A take by touch, rising off the taker and fading (G0078): its look and nothing on it.
                drawPop(x, y, radius, appearance);
                continue;
            }
            if (appearance.burst > 0f) {
                // Under the body, so the thumper stays readable in the middle of its own slam (G0071).
                SCRATCH.set(burstMark.color());
                SCRATCH.a = burstMark.fillAlpha() * appearance.burstFade;
                shapes.setColor(SCRATCH);
                shapes.circle(x, y, appearance.burst, BURST_SEGMENTS);
            }
            if (appearance.state == Appearance.State.SLUNG || appearance.look == Look.GRENADE) {
                // Off the ground (G0074, G0077): its shadow stays below it, so a slung rider nothing can hurt and a
                // grenade on its way read as up in the air.
                drawShadow(x, y, radius);
            }
            if (SLITHER.has(entity) && SLITHER.get(entity).enabled) {
                drawParts(entity, x, y, radius, tint(appearance));
            }
            if (BURROW.has(entity) && BURROW.get(entity).burrowed()) {
                // Under the ground it is its shadow and nothing else (G0096, pillar 4).
                drawBurrowShadow(x, y, radius, BURROW.get(entity));
                continue;
            }
            if (appearance.look == Look.LIMPET) {
                drawLimpet(x, y, radius, appearance);
                continue;
            }
            shapes.setColor(tint(appearance));
            if (square(appearance.look)) {
                shapes.rect(x - radius, y - radius, radius * 2f, radius * 2f);
            } else if (segmented(appearance.look) && faces(entity)) {
                drawSegments(x, y, radius, FACING.get(entity), hardBehind(entity), appearance);
            } else if (shelled(appearance.look)) {
                drawShells(x, y, radius, appearance);
            } else if (appearance.look == Look.OBSTACLE) {
                drawBoulder(x, y, radius, tint(appearance));
            } else if (appearance.look == Look.STONE) {
                drawStone(x, y, radius, tint(appearance));
            } else if (appearance.streaks()) {
                // Drawn back to where it says it came from, with a head on it so a beam that has
                // just been fired is still something rather than a zero-length line. The far end is
                // a fixed point in the world and is not interpolated; the head is, like everything
                // else, so the line lengthens as smoothly as the shot moves.
                shapes.rectLine(appearance.trailX, appearance.trailY, x, y, radius * 2f);
                shapes.circle(x, y, radius, CIRCLE_SEGMENTS);
            } else {
                shapes.circle(x, y, radius, CIRCLE_SEGMENTS);
            }
            if (appearance.riders > 0 && faces(entity)) {
                drawRiders(x, y, radius, FACING.get(entity), appearance);
            }
            if (FACING.has(entity) && (FACING.get(entity).x != 0f || FACING.get(entity).y != 0f)) {
                float facingX = FACING.get(entity).x;
                float facingY = FACING.get(entity).y;
                if (lead != null && COMMANDER.has(entity) && lead.facing(COMMANDER.get(entity).slot, x, y, ahead)) {
                    facingX = ahead[0];
                    facingY = ahead[1];
                }
                shapes.setColor(brightened(tint(appearance), facingMark.tint()));
                float reach = facingMark.lengthFor(radius);
                if (alien(entity)) {
                    // A spike rather than a spoke: broad across the centre, sharp at the same reach.
                    float half = facingMark.spikeFor(radius) * 0.5f;
                    shapes.triangle(
                            x - facingY * half,
                            y + facingX * half,
                            x + facingY * half,
                            y - facingX * half,
                            x + facingX * reach,
                            y + facingY * reach);
                } else {
                    shapes.rectLine(x, y, x + facingX * reach, y + facingY * reach, facingMark.widthFor(radius));
                }
            }
            if (FlagMark.flies(appearance)) {
                // Taken, not nearly taken (pillar 5): a ring and a flag in place of the full core.
                drawFlag(x, y, radius);
            } else if (appearance.charge > 0f) {
                // A charge that names a look of its own is drawn in that colour; everything else is
                // the body's own colour brightened, which is what the mark has always been. The
                // simulation says what the charge is and the palette says what that looks like
                // (G0003) - there is no branch here on what the entity is.
                if (appearance.chargeLook == Look.BLOAT) {
                    // The one charge that grows OUT past the body instead of filling it, because it
                    // is the one that is about to kill whatever is standing nearby rather than
                    // something the creature is doing. Half-transparent, so what it is charging can
                    // still be seen and fought.
                    Color ring = SCRATCH.set(palette.colorFor(Look.BLOAT));
                    ring.a = bloatMark.alpha();
                    shapes.setColor(ring);
                    shapes.circle(x, y, bloatMark.radiusFor(radius, appearance.charge), CIRCLE_SEGMENTS);
                } else {
                    shapes.setColor(
                            appearance.chargeLook == Look.NONE
                                    ? brightened(tint(appearance), chargeMark.tint())
                                    : palette.colorFor(appearance.chargeLook));
                    shapes.circle(x, y, chargeMark.radiusFor(radius, appearance.charge), CIRCLE_SEGMENTS);
                }
            }
            if (appearance.heat > 0f) {
                drawHeat(x, y, radius, appearance);
            }
            if (dotted(appearance.look)) {
                shapes.setColor(crateMark.color());
                shapes.circle(x, y, crateMark.dotFor(radius), CIRCLE_SEGMENTS);
            }
            if (appearance.wounds > 0) {
                drawWounds(x, y, radius, appearance.wounds);
            }
            if (BURROW.has(entity) && BURROW.get(entity).enabled && !Life.dying(entity)) {
                // Up: its maw's teeth, closing on the place it came up at, or round its hole (G0096).
                drawMaw(entity, radius, appearance);
            }
            if (appearance.daze > 0f) {
                // A declared stun, shrinking onto the body as it is spent (G0053, G0071).
                SCRATCH.set(dazeMark.color());
                SCRATCH.a = dazeMark.alpha();
                shapes.setColor(SCRATCH);
                shapes.circle(x, y, dazeMark.radiusFor(radius, appearance.daze), CIRCLE_SEGMENTS);
            }
        }
        if (brackets != null) {
            brackets.draw(shapes, alpha);
        }
        shapes.end();
        drawRings();
        drawTrees();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * A clinch ending on screen (G0099), for the entity about to be drawn: the one it killed shrinks over his own
     * death throes, and while the winner is still in his lock both are drawn swayed together from side to side, across
     * the line joining them. The partner is the one the clinch named by handle - never one found here - and both of
     * the pair are drawn from the same numbers: the winner's lock, the loser's throes, the line from loser to winner. Neither is a clinch's
     * partner once it is off the field, so a body long gone devours nothing. A look only: the bodies are where the
     * simulation has them.
     */
    private void devour(Entity entity, float alpha) {
        devourScale = 1f;
        swayX = 0f;
        swayY = 0f;
        if (!STUNNED.has(entity) || STUNNED.get(entity).partner == Handles.NOBODY) {
            return;
        }
        @Nullable Entity partner = Handles.resolve(handled, STUNNED.get(entity).partner);
        if (partner == null || !STUNNED.has(partner) || STUNNED.get(partner).partner != Handles.of(entity)) {
            // Half a clinch is none: a body no blow holds was never clinched (G0095), so neither is devoured.
            return;
        }
        boolean down = Life.dying(entity);
        if (down) {
            devourScale = devourMark.scaleFor(APPEARANCE.get(entity).throes);
        }
        if (down == Life.dying(partner) || !BODY.has(partner)) {
            // Both standing is no clinch's end; both down leaves nobody to sway with.
            return;
        }
        Entity winner = down ? partner : entity;
        Entity loser = down ? entity : partner;
        Stunned lock = STUNNED.get(winner);
        if (!lock.grappling || !lock.active()) {
            return;
        }
        Position from = POSITION.get(loser);
        Position to = POSITION.get(winner);
        float alongX = to.x - from.x;
        float alongY = to.y - from.y;
        float apart = (float) Math.sqrt(alongX * alongX + alongY * alongY);
        if (apart <= 0f) {
            return;
        }
        float sway = devourMark.swayFor(
                BODY.get(loser).radius,
                APPEARANCE.get(loser).throes,
                lock.remainingTicks,
                alpha,
                tickSeconds,
                motionScale);
        // Square to the line, the same way round for both of them.
        swayX = -alongY / apart * sway;
        swayY = alongX / apart * sway;
    }

    /**
     * A boulder: its colour, and over it a stepped gradient of smaller, lighter discs towards the north-west, so it
     * reads as round rock. {@code base} is the shared tint colour, copied out before it is brightened.
     */
    private void drawBoulder(float x, float y, float radius, Color base) {
        float outlineThickness = 0.5f;
        Color outlineColor = darkened(SCRATCH.set(base), 0.3f);
        shapes.setColor(outlineColor);
        shapes.circle(x, y, radius + outlineThickness, 8);
        shapes.setColor(base);
        shapes.circle(x, y, radius, 8);
        for (int step = 1; step <= boulderMark.steps(); step++) {
            float offset = boulderMark.offsetAt(radius, step);
            shapes.setColor(brightened(SCRATCH.set(base), boulderMark.lightAt(step)));
            shapes.circle(x - offset, y + offset, boulderMark.radiusAt(radius, step), HEXAGON_SEGMENTS);
        }
    }

    /**
     * The parts trailing a slithering head (G0095), tail first so each lies under the one in front, as many as it has
     * left - one fewer for every part's worth of health lost - each see-through with a denser core ({@link
     * SlitherMark}). Moved by the head's interpolation, which is near enough
     * for parts that follow it.
     */
    private void drawParts(Entity entity, float x, float y, float headRadius, Color base) {
        Slither slither = SLITHER.get(entity);
        Position head = POSITION.get(entity);
        float shiftX = x - head.x;
        float shiftY = y - head.y;
        for (int part = slither.partsLeft - 1; part >= 1; part--) {
            float partX = slither.partX(part) + shiftX;
            float partY = slither.partY(part) + shiftY;
            float radius = slither.radiusOf(part, headRadius);
            SCRATCH.set(base);
            SCRATCH.a = slitherMark.alpha();
            shapes.setColor(SCRATCH);
            shapes.circle(partX, partY, radius, CIRCLE_SEGMENTS);
            SCRATCH.set(base);
            SCRATCH.a = slitherMark.coreAlpha();
            shapes.setColor(SCRATCH);
            shapes.circle(partX, partY, radius * slitherMark.coreScale(), CIRCLE_SEGMENTS);
        }
    }

    /**
     * A stone: a pentagon of rock, outlined darker and lit from the north-west as a boulder is, turned by where it
     * stands so a field of them is not a field of the same stone. Static, so the turn never changes.
     */
    private void drawStone(float x, float y, float radius, Color base) {
        float turn = turnOf(x, y);
        float cos = (float) Math.cos(turn);
        float sin = (float) Math.sin(turn);
        shapes.setColor(darkened(SCRATCH.set(base), 0.3f));
        pentagon(x, y, radius + 0.5f, cos, sin);
        shapes.setColor(base);
        pentagon(x, y, radius, cos, sin);
        for (int step = 1; step <= boulderMark.steps(); step++) {
            float offset = boulderMark.offsetAt(radius, step);
            shapes.setColor(brightened(SCRATCH.set(base), boulderMark.lightAt(step)));
            pentagon(x - offset, y + offset, boulderMark.radiusAt(radius, step), cos, sin);
        }
    }

    /** A filled regular pentagon, its first corner turned from east by the angle whose cosine and sine these are. */
    private void pentagon(float x, float y, float radius, float cos, float sin) {
        float fromX = x + cos * radius;
        float fromY = y + sin * radius;
        for (int corner = 1; corner <= PENTAGON_CORNERS; corner++) {
            int k = corner % PENTAGON_CORNERS;
            float toX = x + (PENTAGON_X[k] * cos - PENTAGON_Y[k] * sin) * radius;
            float toY = y + (PENTAGON_X[k] * sin + PENTAGON_Y[k] * cos) * radius;
            shapes.triangle(x, y, fromX, fromY, toX, toY);
            fromX = toX;
            fromY = toY;
        }
    }

    /** A turn in {@code [0, 2pi)} that depends only on where something stands - a look, never read by the simulation. */
    private static float turnOf(float x, float y) {
        int hash = Float.floatToIntBits(x) * 0x9E3779B1 ^ Float.floatToIntBits(y) * 0x85EBCA77;
        hash ^= hash >>> 15;
        return (hash & 0xFFFF) / 65536f * (float) (Math.PI * 2.0);
    }

    /**
     * Trees, last of all, so their foliage hides everything beneath it - bodies, shots, rings, stuns - whoever is
     * looking and however far the view is pulled back (G0072). Every outer disc first, then every inner one, then
     * every trunk, so one tree's see-through edge never tints another's opaque middle and the trunks, which stop men
     * and shots, are always seen. Static, so not interpolated. Skipped when there are none, which is every biome but
     * the fen.
     */
    private void drawTrees() {
        boolean any = false;
        for (int i = 0; i < drawable.size() && !any; i++) {
            any = APPEARANCE.get(drawable.get(i)).look == Look.TREE;
        }
        if (!any) {
            return;
        }
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        SCRATCH.set(treeMark.outer());
        SCRATCH.a = treeMark.outerAlpha();
        shapes.setColor(SCRATCH);
        for (int i = 0; i < drawable.size(); i++) {
            Entity entity = drawable.get(i);
            if (APPEARANCE.get(entity).look == Look.TREE) {
                Position position = POSITION.get(entity);
                shapes.circle(position.x, position.y, treeMark.outerFor(BODY.get(entity).radius), FOLIAGE_SEGMENTS);
            }
        }
        SCRATCH.set(treeMark.outer());
        SCRATCH.a = treeMark.outerAlpha() * 0.5f;
        shapes.setColor(SCRATCH);
        for (int i = 0; i < drawable.size(); i++) {
            Entity entity = drawable.get(i);
            if (APPEARANCE.get(entity).look == Look.TREE) {
                Position position = POSITION.get(entity);
                float v = treeMark.outerFor(BODY.get(entity).radius * 0.85f);
                float y = position.y - v * 0.5f;
                shapes.ellipse(position.x - v, y, v * 2f, v, 0f, FOLIAGE_SEGMENTS);
                shapes.ellipse(position.x - v, y, v * 2f, v, 90f, FOLIAGE_SEGMENTS);
            }
        }
        SCRATCH.set(treeMark.inner());
        SCRATCH.a = 1f;
        shapes.setColor(SCRATCH);
        for (int i = 0; i < drawable.size(); i++) {
            Entity entity = drawable.get(i);
            if (APPEARANCE.get(entity).look == Look.TREE) {
                Position position = POSITION.get(entity);
                shapes.circle(position.x, position.y, treeMark.innerFor(BODY.get(entity).radius), FOLIAGE_SEGMENTS);
            }
        }
        for (int i = 0; i < drawable.size(); i++) {
            Entity entity = drawable.get(i);
            Appearance appearance = APPEARANCE.get(entity);
            if (appearance.look == Look.TREE) {
                Position position = POSITION.get(entity);
                shapes.setColor(tint(appearance));
                shapes.circle(position.x, position.y, BODY.get(entity).radius, HEXAGON_SEGMENTS);
            }
        }
        shapes.end();
    }

    /**
     * Everything that is ground rather than something standing on it, drawn first so that nothing
     * underfoot can hide a man standing in it (pillar 5). A post among it: it is put down mid-fight, and drawn
     * where it was spawned it covered the fight.
     *
     * <p>A control point says the same thing by being spawned before anything stands on it, and
     * that is the cheaper answer where it works. It does not work for ruined ground or for fire,
     * both of which appear in the middle of a fight - so this is a pass rather than an ordering, and
     * it is the third thing here that asks what a look is, beside the square one and the shelled one.
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
            float x = Drawn.x(position, previous, alpha);
            float y = Drawn.y(position, previous, alpha);
            float radius = BODY.get(entity).radius;
            if (appearance.look == Look.POST) {
                drawPost(x, y, radius);
                continue;
            }
            shapes.setColor(tint(appearance));
            shapes.circle(x, y, radius, CIRCLE_SEGMENTS);
            if (appearance.look == Look.GEYSER) {
                if (appearance.charge > 0f) {
                    // Filling: the fire mark at its core, growing and pulsing - the warning (G0089, pillar 5).
                    tinted.set(palette.colorFor(appearance.chargeLook))
                            .lerp(geyserMark.light(), geyserMark.lightAt(pulseSeconds));
                    shapes.setColor(tinted);
                    shapes.circle(x, y, chargeMark.radiusFor(radius, appearance.charge), CIRCLE_SEGMENTS);
                }
                continue;
            }
            if (appearance.charge > 0f) {
                // The same core every other thing that fills up wears, on the one channel that draws
                // it - so burning ground reads as hottest in the middle and cools as it dies without
                // the simulation ever saying anything about a colour (G0003). Ground that publishes
                // no charge, which is all ruined ground, gets nothing.
                shapes.setColor(brightened(tint(appearance), chargeMark.tint()));
                shapes.circle(x, y, chargeMark.radiusFor(radius, appearance.charge), CIRCLE_SEGMENTS);
            }
        }
    }

    /**
     * Everything drawn as squares rather than as a body: each square as much of a tile as it publishes, round its
     * own middle, so a door sinking is seen as its squares shrinking (G0069). What it is filling up to is a
     * loading circle on it, in the colour its charge names.
     */
    private void drawSquared(float alpha) {
        for (int i = 0; i < squared.size(); i++) {
            Entity entity = squared.get(i);
            Appearance appearance = APPEARANCE.get(entity);
            if (appearance.squaresAcross <= 0 || appearance.squareFill <= 0f) {
                continue;
            }
            float x = Drawn.x(POSITION.get(entity), PREVIOUS.get(entity), alpha);
            float y = Drawn.y(POSITION.get(entity), PREVIOUS.get(entity), alpha);
            float pitch = appearance.squarePitch;
            float side = pitch * appearance.squareFill;
            shapes.setColor(tint(appearance));
            for (int across = 0; across < appearance.squaresAcross; across++) {
                for (int down = 0; down < appearance.squaresDown; down++) {
                    float middleX = x + (across - (appearance.squaresAcross - 1) * 0.5f) * pitch;
                    float middleY = y + (down - (appearance.squaresDown - 1) * 0.5f) * pitch;
                    shapes.rect(middleX - side * 0.5f, middleY - side * 0.5f, side, side);
                }
            }
            if (appearance.charge > 0f) {
                drawLoading(x, y, pitch, palette.colorFor(appearance.chargeLook), appearance.charge);
            }
        }
    }

    /**
     * A loading circle on a door, or on a limpet calibrating (G0100): as much of a ring as it is through, clockwise from straight up. Straight pieces,
     * because the renderer's own arc is a pie slice.
     */
    private void drawLoading(float x, float y, float pitch, Color color, float fraction) {
        shapes.setColor(color);
        float ring = doorMark.radiusFor(pitch);
        float width = doorMark.widthFor(pitch);
        int steps = doorMark.segmentsFor(fraction);
        float fromX = x + MathUtils.cosDeg(doorMark.angleAt(0)) * ring;
        float fromY = y + MathUtils.sinDeg(doorMark.angleAt(0)) * ring;
        for (int step = 1; step <= steps; step++) {
            float toX = x + MathUtils.cosDeg(doorMark.angleAt(step)) * ring;
            float toY = y + MathUtils.sinDeg(doorMark.angleAt(step)) * ring;
            shapes.rectLine(fromX, fromY, toX, toY, width);
            fromX = toX;
            fromY = toY;
        }
    }

    /**
     * A limpet (G0100): a rectangle turned by how far it has flown, then the door's loading circle on it while it
     * calibrates, or the red dot ticking once it has.
     */
    private void drawLimpet(float x, float y, float radius, Appearance appearance) {
        shapes.setColor(tint(appearance));
        float length = radius * limpetMark.length();
        float breadth = radius * limpetMark.breadth();
        shapes.rect(
                x - length * 0.5f,
                y - breadth * 0.5f,
                length * 0.5f,
                breadth * 0.5f,
                length,
                breadth,
                1f,
                1f,
                limpetMark.angleFor(appearance.spin));
        if (appearance.charge <= 0f) {
            return;
        }
        if (appearance.chargeLook == Look.CALIBRATED) {
            if (limpetMark.litAt(pulseSeconds)) {
                shapes.setColor(palette.colorFor(Look.CALIBRATED));
                shapes.circle(x, y, radius * limpetMark.dot(), CIRCLE_SEGMENTS);
            }
            return;
        }
        drawLoading(x, y, radius * limpetMark.ringPitch(), palette.colorFor(appearance.chargeLook), appearance.charge);
    }

    /** A post: an outline just round the point it stands on, pulsing between its dark green and a light one. */
    private void drawPost(float x, float y, float radius) {
        tinted.set(palette.colorFor(Look.POST)).lerp(postMark.light(), postMark.lightAt(pulseSeconds));
        shapes.setColor(tinted);
        drawOutline(x, y, postMark.ringFor(radius), postMark.widthFor(radius), postMark.segments());
    }

    /**
     * A taken control point: a static ring just inside its edge, and at its middle a pole flying a small flag that
     * waves on the client's clock, pinned at the pole - in strips, each two triangles, so it bends.
     */
    private void drawFlag(float x, float y, float radius) {
        shapes.setColor(flagMark.color());
        drawOutline(x, y, flagMark.ringFor(radius), flagMark.edgeFor(radius), flagMark.segments());
        float poleWidth = flagMark.poleWidthFor(radius);
        float top = y + flagMark.poleHeightFor(radius);
        float hang = flagMark.heightFor(radius);
        float strip = flagMark.widthFor(radius) / flagMark.slices();
        float fromX = x + poleWidth * 0.5f;
        float fromTop = top;
        for (int at = 1; at <= flagMark.slices(); at++) {
            float toX = fromX + strip;
            float toTop = top + flagMark.liftAt(at, radius, pulseSeconds, motionScale);
            shapes.triangle(fromX, fromTop, toX, toTop, toX, toTop - hang);
            shapes.triangle(fromX, fromTop, toX, toTop - hang, fromX, fromTop - hang);
            fromX = toX;
            fromTop = toTop;
        }
        shapes.setColor(flagMark.pole());
        shapes.rect(x - poleWidth * 0.5f, y, poleWidth, top - y);
    }

    /** A circle outline in the current colour, in straight pieces, because the renderer's own arc is a pie slice. */
    private void drawOutline(float x, float y, float ring, float width, int segments) {
        float fromX = x + ring;
        float fromY = y;
        for (int step = 1; step <= segments; step++) {
            float angle = step * 360f / segments;
            float toX = x + MathUtils.cosDeg(angle) * ring;
            float toY = y + MathUtils.sinDeg(angle) * ring;
            shapes.rectLine(fromX, fromY, toX, toY, width);
            fromX = toX;
            fromY = toY;
        }
    }

    /** Whether a look is underfoot rather than standing on the ground. */
    private static boolean ground(Look look) {
        return look == Look.SLICK || look == Look.FIRE || look == Look.GLUE || look == Look.POST || look == Look.GEYSER;
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
            if (appearance.ordered || appearance.charged || appearance.shield > 0f || appearance.burst > 0f) {
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
            if (appearance.shield > 0f) {
                drawShield(position.x, position.y, radius, appearance.shield);
            }
            if (appearance.burst > 0f) {
                SCRATCH.set(burstMark.color());
                SCRATCH.a = burstMark.outlineAlpha() * appearance.burstFade;
                shapes.setColor(SCRATCH);
                shapes.circle(position.x, position.y, appearance.burst, BURST_SEGMENTS);
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
        drawArc(x, y, radius, palette.colorFor(appearance.overheated ? Look.OVERHEAT : Look.HEAT), appearance.heat);
    }

    /** The heat bar's arc, over something this big, this full, in this colour. */
    private void drawArc(float x, float y, float radius, Color color, float fraction) {
        shapes.setColor(color);
        float arc = heatMark.radiusFor(radius);
        float width = heatMark.widthFor(radius);
        int steps = heatMark.segmentsFor(fraction);
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
     * A shield: as much of a ring as it has left, clockwise from straight up (G0053). Straight pieces
     * in the outline pass, because the renderer's own arc is a pie slice.
     */
    private void drawShield(float x, float y, float radius, float fraction) {
        shapes.setColor(shieldMark.color());
        float ring = shieldMark.radiusFor(radius);
        int steps = shieldMark.segmentsFor(fraction);
        float fromX = x + MathUtils.cosDeg(shieldMark.angleAt(0)) * ring;
        float fromY = y + MathUtils.sinDeg(shieldMark.angleAt(0)) * ring;
        for (int step = 1; step <= steps; step++) {
            float toX = x + MathUtils.cosDeg(shieldMark.angleAt(step)) * ring;
            float toY = y + MathUtils.sinDeg(shieldMark.angleAt(step)) * ring;
            shapes.line(fromX, fromY, toX, toY);
            fromX = toX;
            fromY = toY;
        }
    }

    /** A pop: what was taken, in its own shape, risen by how far through it is and faded by how much is left. */
    private void drawPop(float x, float y, float radius, Appearance appearance) {
        float left = appearance.pop;
        float risen = y + popMark.riseFor(radius, left);
        SCRATCH.set(tint(appearance));
        SCRATCH.a = popMark.alpha() * left;
        shapes.setColor(SCRATCH);
        if (square(appearance.look)) {
            shapes.rect(x - radius, risen - radius, radius * 2f, radius * 2f);
        } else {
            shapes.circle(x, risen, radius, CIRCLE_SEGMENTS);
        }
        if (dotted(appearance.look)) {
            SCRATCH.set(crateMark.color());
            SCRATCH.a = popMark.alpha() * left;
            shapes.setColor(SCRATCH);
            shapes.circle(x, risen, crateMark.dotFor(radius), CIRCLE_SEGMENTS);
        }
    }

    /** A worm under the ground: a see-through black disc its size, fainter while it rests (G0096). */
    private void drawBurrowShadow(float x, float y, float radius, Burrow burrow) {
        SCRATCH.set(shadowMark.color());
        SCRATCH.a = burrow.restTicks > 0 ? burrowMark.restAlpha() : burrowMark.shadowAlpha();
        shapes.setColor(SCRATCH);
        shapes.circle(x, y, radius, CIRCLE_SEGMENTS);
    }

    /**
     * A worm's maw: a ring on the place it came up at, as wide as its maw has left to close - from its {@code mawScale}
     * times its body down to its body at the snap - with its teeth pointing inwards, as many as it has left. Drawn on
     * the maw's place rather than the body's, which steers onto it.
     */
    private void drawMaw(Entity entity, float radius, Appearance appearance) {
        Burrow burrow = BURROW.get(entity);
        float ring = radius * (1f + (burrow.mawScale - 1f) * burrow.mawLeft());
        float cx = burrow.mawX;
        float cy = burrow.mawY;
        SCRATCH.set(tint(appearance));
        SCRATCH.a = burrowMark.ringAlpha();
        shapes.setColor(SCRATCH);
        float width = burrowMark.ringWidth();
        for (int segment = 0; segment < CIRCLE_SEGMENTS; segment++) {
            double from = Math.PI * 2.0 * segment / CIRCLE_SEGMENTS;
            double to = Math.PI * 2.0 * (segment + 1) / CIRCLE_SEGMENTS;
            shapes.rectLine(
                    cx + ring * (float) Math.cos(from),
                    cy + ring * (float) Math.sin(from),
                    cx + ring * (float) Math.cos(to),
                    cy + ring * (float) Math.sin(to),
                    width);
        }
        float halfWidth = burrowMark.toothWidth() * radius * 0.5f;
        float length = burrowMark.toothLength() * radius;
        for (int tooth = 0; tooth < burrow.teethLeft; tooth++) {
            double angle = Math.PI * 2.0 * tooth / burrow.teeth;
            float outX = (float) Math.cos(angle);
            float outY = (float) Math.sin(angle);
            float baseX = cx + outX * ring;
            float baseY = cy + outY * ring;
            shapes.triangle(
                    baseX - outY * halfWidth,
                    baseY + outX * halfWidth,
                    baseX + outY * halfWidth,
                    baseY - outX * halfWidth,
                    baseX - outX * length,
                    baseY - outY * length);
        }
    }

    /** A shadow on the ground below what is in the air and to one side - a slung rider, a grenade (G0074, G0077). */
    private void drawShadow(float x, float y, float radius) {
        SCRATCH.set(shadowMark.color());
        SCRATCH.a = shadowMark.alpha();
        shapes.setColor(SCRATCH);
        float offset = shadowMark.offset() * radius;
        shapes.circle(x + offset, y - offset, shadowMark.scale() * radius, CIRCLE_SEGMENTS);
    }

    /**
     * What a slinger still carries, side by side across its back behind its facing, in the riders' own colour - one
     * fewer for every sling (G0074, pillar 5).
     */
    private void drawRiders(float x, float y, float radius, Facing facing, Appearance appearance) {
        float length = (float) Math.sqrt(facing.x * facing.x + facing.y * facing.y);
        float aheadX = facing.x / length;
        float aheadY = facing.y / length;
        float backX = x - aheadX * slingMark.riderOffset() * radius;
        float backY = y - aheadY * slingMark.riderOffset() * radius;
        shapes.setColor(palette.colorFor(appearance.riderLook));
        for (int i = 0; i < appearance.riders; i++) {
            float side = slingMark.sideFor(radius, i, appearance.riders);
            shapes.circle(backX - aheadY * side, backY + aheadX * side, slingMark.riderFor(radius), CIRCLE_SEGMENTS);
        }
    }

    /** One disc in his middle, larger per wound (G0053); only ever reached by a man carrying one. */
    private void drawWounds(float x, float y, float radius, int wounds) {
        shapes.setColor(woundMark.color());
        shapes.circle(x, y, woundMark.radiusFor(radius, wounds), CIRCLE_SEGMENTS);
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
     * Whether a look is drawn as two segments along its facing rather than as one circle - the client's
     * decision, as the square is. The strains whose head and back are different things (G0045, G0073).
     */
    private static boolean segmented(Look look) {
        return look == Look.ANT || look == Look.SLUDGER;
    }

    /** Whether its armour is on its back rather than its head, so the back is the segment drawn hard (G0073). */
    private static boolean hardBehind(Entity entity) {
        return ARMOUR.has(entity) && ARMOUR.get(entity).enabled && ARMOUR.get(entity).centreCosine < 0f;
    }

    /** The swarm's side: its facing is drawn as a spike, the squad's as a spoke. */
    private static boolean alien(Entity entity) {
        return FACTION.has(entity) && FACTION.get(entity).side == Side.ALIEN;
    }

    private static boolean faces(Entity entity) {
        return FACING.has(entity) && (FACING.get(entity).x != 0f || FACING.get(entity).y != 0f);
    }

    /**
     * A body behind and a head in front, both inside the circle that is hit, the armoured one darker - the head, or
     * for a strain armoured behind the body - so the end a bullet glances off is the end that looks hard (pillar 5).
     */
    private void drawSegments(
            float x, float y, float radius, Facing facing, boolean hardBehind, Appearance appearance) {
        float length = (float) Math.sqrt(facing.x * facing.x + facing.y * facing.y);
        float aheadX = facing.x / length;
        float aheadY = facing.y / length;
        shapes.setColor(hardBehind ? darkened(tint(appearance), segmentMark.headShade()) : tint(appearance));
        float back = segmentMark.bodyOffset() * radius;
        shapes.circle(x - aheadX * back, y - aheadY * back, segmentMark.bodyScale() * radius, CIRCLE_SEGMENTS);
        shapes.setColor(hardBehind ? tint(appearance) : darkened(tint(appearance), segmentMark.headShade()));
        float front = segmentMark.headOffset() * radius;
        shapes.circle(x + aheadX * front, y + aheadY * front, segmentMark.headScale() * radius, CIRCLE_SEGMENTS);
        shapes.setColor(tint(appearance));
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
        return look == Look.TURRET || look == Look.CRATE;
    }

    /** Looks drawn with a dot in the middle, over everything else on them (G0056). */
    private static boolean dotted(Look look) {
        return look == Look.CRATE;
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
