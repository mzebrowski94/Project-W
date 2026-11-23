package pl.mzebrows.pwt.client.draw;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Site;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.WorkedSite;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.system.combat.Grants;
import pl.mzebrows.pwt.sim.system.query.Reach;
import pl.mzebrows.pwt.ui.hud.ChipCover;
import pl.mzebrows.pwt.ui.hud.Prompts;
import pl.mzebrows.pwt.ui.hud.Readout;

/**
 * The action key's **brackets** for the commanders played on this machine (G0098), drawn in the world pass under
 * the trees, so a tree still hides what is beneath it (G0072): corners round each one's **subject** - the entity his
 * readout names by handle, never one found here - faint corners round what else the key could act on within the
 * **notice distance** of a commander free to act, and small ticks round a pickup worth something to one.
 *
 * <p>Whether a thing could be acted on is {@link Reach#offers} and whether a pickup is worth something is {@link
 * Grants#worthTo}: the simulation's own answers. Each thing is marked once, however many commanders here are near
 * it. A scan over a handful of things by at most a machine's few commanders, nothing allocated.
 *
 * <p>It also says whether a prompt would hide a seen alien ({@link ChipCover}): it knows where each body is drawn
 * this frame, and what a tree's foliage hides.
 */
public final class ActionBrackets implements ChipCover {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Door> DOOR = ComponentMapper.getFor(Door.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Appearance> APPEARANCE = ComponentMapper.getFor(Appearance.class);

    private static final Family HANDLED = Family.all(Handle.class).get();
    private static final Family COMMANDERS =
            Family.all(Commander.class, Position.class, Body.class).get();
    private static final Family CASUALTIES = Family.all(
                    Vitality.class, Position.class, PreviousPosition.class, Body.class)
            .get();
    private static final Family TURRETS = Family.all(Turret.class, Position.class, PreviousPosition.class, Body.class)
            .get();
    private static final Family CRATES = Family.all(Crate.class, Position.class, PreviousPosition.class, Body.class)
            .get();
    private static final Family DOORS = Family.all(Door.class).get();
    private static final Family WORKED_SITES = Family.all(
                    WorkedSite.class, Site.class, Position.class, PreviousPosition.class, Body.class)
            .get();
    private static final Family BODIES = Family.all(Faction.class, Position.class, PreviousPosition.class, Body.class)
            .get();
    private static final Family LOOKS =
            Family.all(Appearance.class, Position.class, Body.class).get();
    private static final Family PICKUPS = Family.all(Pickup.class, Position.class, PreviousPosition.class, Body.class)
            .exclude(Crate.class)
            .get();

    private final ActionMark mark;
    private final TreeMark tree;
    private final Color subjectColor;
    private final Color faintColor;
    private final Color pickupColor;
    private final Readout readout;
    private final IntPredicate local;

    /** By slot, this machine's commanders this frame, and whether each is free to act; cleared every frame. */
    private final @Nullable Entity[] mine = new Entity[Players.MAX];

    private final boolean[] noticing = new boolean[Players.MAX];

    private ImmutableArray<Entity> handled = empty();
    private ImmutableArray<Entity> commanders = empty();
    private ImmutableArray<Entity> casualties = empty();
    private ImmutableArray<Entity> turrets = empty();
    private ImmutableArray<Entity> crates = empty();
    private ImmutableArray<Entity> doors = empty();
    private ImmutableArray<Entity> workedSites = empty();
    private ImmutableArray<Entity> pickups = empty();
    private ImmutableArray<Entity> bodies = empty();
    private ImmutableArray<Entity> looks = empty();

    /** How far the clock was towards the next tick when the world was last drawn - where a body is on screen. */
    private float drawnAlpha;

    private @Nullable SimConfig config;
    private float tileSize = 1f;

    /**
     * @param readout the frame's readout, filled before the world is drawn: each slot's subject and state
     * @param local whether a slot is played on this machine - another machine's player is marked there
     */
    public ActionBrackets(
            ActionMark mark, TreeMark tree, Color subject, Color pickup, Readout readout, IntPredicate local) {
        this.mark = mark;
        this.tree = tree;
        this.subjectColor = subject;
        this.faintColor = new Color(subject.r, subject.g, subject.b, subject.a * mark.faintAlpha());
        this.pickupColor = pickup;
        this.readout = readout;
        this.local = local;
    }

    /** Points it at a world. Again after a restart - a new simulation is a new engine. */
    public void bind(Simulation world) {
        handled = world.engine().getEntitiesFor(HANDLED);
        commanders = world.engine().getEntitiesFor(COMMANDERS);
        casualties = world.engine().getEntitiesFor(CASUALTIES);
        turrets = world.engine().getEntitiesFor(TURRETS);
        crates = world.engine().getEntitiesFor(CRATES);
        doors = world.engine().getEntitiesFor(DOORS);
        workedSites = world.engine().getEntitiesFor(WORKED_SITES);
        pickups = world.engine().getEntitiesFor(PICKUPS);
        bodies = world.engine().getEntitiesFor(BODIES);
        looks = world.engine().getEntitiesFor(LOOKS);
        config = world.config();
        tileSize = world.map().tileSize();
    }

    /** Draws every mark, inside the world's filled shape pass. */
    void draw(ShapeRenderer shapes, float alpha) {
        drawnAlpha = alpha;
        if (!follow()) {
            return;
        }
        if (mark.notices()) {
            faint(shapes, casualties, ActionKind.STABILISE, alpha);
            faint(shapes, turrets, ActionKind.MAN_TURRET, alpha);
            faint(shapes, crates, ActionKind.OPEN_CRATE, alpha);
            faint(shapes, doors, ActionKind.OPEN_DOOR, alpha);
            faint(shapes, workedSites, ActionKind.DOWNLOAD, alpha);
            faint(shapes, workedSites, ActionKind.EXTRACT, alpha);
            ticks(shapes, alpha);
        }
        for (int slot = 0; slot < Players.MAX; slot++) {
            long subject = readout.player(slot).subject;
            @Nullable Entity entity = mine[slot] == null ? null : Handles.resolve(handled, subject);
            if (entity != null) {
                bracket(shapes, entity, alpha, subjectColor, mark.gap(), mark.corner(), mark.width());
            }
        }
    }

    @Override
    public boolean alienWithin(float left, float bottom, float right, float top) {
        for (int i = 0; i < bodies.size(); i++) {
            Entity body = bodies.get(i);
            if (FACTION.get(body).side != Side.ALIEN || Life.dying(body)) {
                continue;
            }
            float x = Drawn.x(POSITION.get(body), PREVIOUS.get(body), drawnAlpha);
            float y = Drawn.y(POSITION.get(body), PREVIOUS.get(body), drawnAlpha);
            float radius = BODY.get(body).radius;
            float toX = x - Math.max(left, Math.min(right, x));
            float toY = y - Math.max(bottom, Math.min(top, y));
            if (toX * toX + toY * toY <= radius * radius && !underFoliage(x, y)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a tree's opaque foliage covers this point (G0072): what it hides is not seen, so it is under nothing. */
    private boolean underFoliage(float x, float y) {
        for (int i = 0; i < looks.size(); i++) {
            Entity entity = looks.get(i);
            if (APPEARANCE.get(entity).look != Look.TREE) {
                continue;
            }
            Position at = POSITION.get(entity);
            float reach = tree.innerFor(BODY.get(entity).radius);
            float toX = x - at.x;
            float toY = y - at.y;
            if (toX * toX + toY * toY <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /** Finds this machine's commanders; false when there are none. */
    private boolean follow() {
        Arrays.fill(mine, null);
        Arrays.fill(noticing, false);
        boolean any = false;
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            int slot = COMMANDER.get(commander).slot;
            if (slot >= 0 && slot < Players.MAX && local.test(slot)) {
                mine[slot] = commander;
                noticing[slot] = Prompts.noticing(readout.player(slot));
                any = true;
            }
        }
        return any;
    }

    private void faint(ShapeRenderer shapes, ImmutableArray<Entity> candidates, ActionKind kind, float alpha) {
        for (int i = 0; i < candidates.size(); i++) {
            Entity candidate = candidates.get(i);
            if (!Reach.offers(candidate, kind) || someonesSubject(candidate) || !noticed(candidate, kind)) {
                continue;
            }
            bracket(shapes, candidate, alpha, faintColor, mark.gap(), mark.corner(), mark.width());
        }
    }

    /** A pickup a commander here would take by touch and is worth something to him, near him (G0006, G0053). */
    private void ticks(ShapeRenderer shapes, float alpha) {
        if (config == null) {
            return;
        }
        for (int i = 0; i < pickups.size(); i++) {
            Entity entity = pickups.get(i);
            Pickup pickup = PICKUP.get(entity);
            PickupDef def = config.pickup(pickup.kind);
            if (pickup.claimed || pickup.shunned || Life.dying(entity) || !def.offersSomethingTo(Side.PLAYER)) {
                continue;
            }
            Grant grant = def.grantFor(Side.PLAYER);
            for (int slot = 0; slot < Players.MAX; slot++) {
                Entity commander = mine[slot];
                if (commander != null
                        && noticing[slot]
                        && Reach.gap(commander, entity) <= mark.noticeDistance()
                        && Grants.worthTo(grant, commander)) {
                    bracket(shapes, entity, alpha, pickupColor, mark.tickGap(), mark.tick(), mark.tickWidth());
                    break;
                }
            }
        }
    }

    /** Whether a commander here free to act is within the notice distance of it, surface to surface. */
    private boolean noticed(Entity candidate, ActionKind kind) {
        for (int slot = 0; slot < Players.MAX; slot++) {
            Entity commander = mine[slot];
            if (commander == null || !noticing[slot] || Objects.equals(commander, candidate)) {
                continue;
            }
            float gap = kind == ActionKind.OPEN_DOOR
                    ? Reach.doorGap(commander, candidate, tileSize)
                    : Reach.gap(commander, candidate);
            if (gap <= mark.noticeDistance()) {
                return true;
            }
        }
        return false;
    }

    /** Whether a commander here already has it as his subject, and so it gets the full brackets instead. */
    private boolean someonesSubject(Entity candidate) {
        long handle = Handles.of(candidate);
        for (int slot = 0; slot < Players.MAX; slot++) {
            if (mine[slot] != null && readout.player(slot).subject == handle) {
                return true;
            }
        }
        return false;
    }

    /** Corners round a body, or round a door's doorway - never a ring. */
    private void bracket(
            ShapeRenderer shapes, Entity entity, float alpha, Color color, float gap, float corner, float width) {
        float left;
        float bottom;
        float right;
        float top;
        if (DOOR.has(entity)) {
            Door door = DOOR.get(entity);
            left = door.col * tileSize - gap;
            bottom = door.row * tileSize - gap;
            right = (door.col + door.width) * tileSize + gap;
            top = (door.row + door.height) * tileSize + gap;
        } else if (BODY.has(entity) && PREVIOUS.has(entity)) {
            float x = Drawn.x(POSITION.get(entity), PREVIOUS.get(entity), alpha);
            float y = Drawn.y(POSITION.get(entity), PREVIOUS.get(entity), alpha);
            float half = BODY.get(entity).radius + gap;
            left = x - half;
            bottom = y - half;
            right = x + half;
            top = y + half;
        } else {
            return;
        }
        float arm = Math.min(right - left, top - bottom) * corner;
        shapes.setColor(color);
        corner(shapes, left, bottom, arm, arm, width);
        corner(shapes, right, bottom, -arm, arm, width);
        corner(shapes, left, top, arm, -arm, width);
        corner(shapes, right, top, -arm, -arm, width);
    }

    /** One corner: two arms out from its point, inwards along each side, drawn inside the box. */
    private static void corner(ShapeRenderer shapes, float x, float y, float alongX, float alongY, float width) {
        float inX = Math.signum(alongX) * width;
        float inY = Math.signum(alongY) * width;
        shapes.rect(Math.min(x, x + alongX), Math.min(y, y + inY), Math.abs(alongX), width);
        shapes.rect(Math.min(x, x + inX), Math.min(y, y + alongY), width, Math.abs(alongY));
    }

    private static ImmutableArray<Entity> empty() {
        return new ImmutableArray<>(new Array<>());
    }
}
