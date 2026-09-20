package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * Takes control points off the map for whoever is standing on them, and wakes what is nearby when
 * they start doing it.
 *
 * <p>Dwell, not touch: somebody on the player's side has to be inside the radius and stay there. The
 * clock runs while any of them is, and <strong>goes back to zero the moment the last one leaves</strong>
 * - so pushing a trooper off a point undoes the whole attempt rather than pausing it, which is what
 * makes defending one worth an alien's time.
 *
 * <p><strong>Taking a point is loud, once.</strong> Nearby enemies are provoked on the tick the
 * attempt <em>starts</em>, not every tick it continues: re-latching them constantly would pin
 * everything within earshot to the point forever and override any other grudge they had, and once is
 * all it takes for them to come and look (G0016). If they are driven off and the squad starts again,
 * that is a new attempt and it is loud again. The shout itself is {@link Rouse}'s, shared with the
 * nest - a point being taken and a nest being hit mean the same thing to a creature.
 *
 * <p><strong>One point on a march does more than be taken.</strong> The last one turns into ground
 * the level then tells you to hold: a post goes down where it stands and whatever the level was
 * holding back is let go, on the tick the bar fills. It is here rather than in a system of its own
 * because it is the same event this one already decides, and it is on the point rather than on the
 * level because which ground does it is a fact about that ground.
 *
 * <p>Progress is not published here. {@code AppearanceSystem} is the only writer of what an entity
 * looks like, and a point filling up rides the same {@code charge} channel as a unit part-way to
 * evolving - one tell, drawn one way, wherever it appears.
 *
 * <p>An {@code EntitySystem} rather than an {@code IteratingSystem} because each point asks a question
 * about every unit, so the families are walked against each other rather than one entity at a time.
 */
public final class ControlPointSystem extends EntitySystem {

    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Waves> WAVES = ComponentMapper.getFor(Waves.class);

    private final Spawner spawner;

    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> holders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

    public ControlPointSystem(Spawner spawner) {
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        points = engine.getEntitiesFor(
                Family.all(ControlPoint.class, Position.class).get());
        holders =
                engine.getEntitiesFor(Family.all(Faction.class, Position.class).get());
        watchers = engine.getEntitiesFor(
                Family.all(Perception.class, Position.class).get());
        levels = engine.getEntitiesFor(Family.all(Waves.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < points.size(); i++) {
            Entity entity = points.get(i);
            ControlPoint point = POINT.get(entity);
            if (point.captured) {
                continue;
            }
            Position at = POSITION.get(entity);
            boolean held = anyoneHolding(at, point.radius);
            if (!held) {
                point.heldSeconds = 0f;
                continue;
            }
            boolean starting = point.heldSeconds <= 0f;
            point.heldSeconds += deltaTime;
            if (point.heldSeconds >= point.captureSeconds) {
                point.captured = true;
                point.heldSeconds = point.captureSeconds;
                if (point.holdsAfterwards) {
                    standTo(point, at);
                }
            }
            if (starting) {
                // Through the one place that answers "there is trouble over there", shared with the
                // nest: two copies of it is one chance for them to drift (G0016).
                Rouse.within(watchers, at.x, at.y, point.aggroRadius);
            }
        }
    }

    /**
     * Turns ground that has just been taken into ground the level tells you to hold.
     *
     * <p>Two things, once, on the tick the bar fills: a post goes down where the point is, and
     * whatever the level was holding back is let go. Both wait until now rather than being on the
     * field from the first tick, and that is the decision rather than an implementation detail - a
     * post existing from the start sends every creature that has noticed nobody walking at it from
     * seven screens away, and turns "forward" round for the whole march (G0027).
     *
     * <p>The post is spawned rather than woken because there is nothing to wake: a post is a goal
     * and has no state, so "there is one now" is the whole of the change.
     */
    private void standTo(ControlPoint point, Position at) {
        spawner.spawnPost(at.x, at.y, point.watchRadius);
        for (int i = 0; i < levels.size(); i++) {
            WAVES.get(levels.get(i)).arm();
        }
    }

    /**
     * Whether anyone on the player's side is inside the point.
     *
     * <p>A body in its throes holds nothing. A point taken by a corpse lying across it would keep
     * ticking over after the squad that was standing there had been wiped out, which is the opposite
     * of what the seconds are for.
     */
    private boolean anyoneHolding(Position at, float radius) {
        for (int i = 0; i < holders.size(); i++) {
            Entity holder = holders.get(i);
            if (FACTION.get(holder).side != Side.PLAYER || Drivers.held(holder)) {
                continue;
            }
            if (within(at, POSITION.get(holder), radius)) {
                return true;
            }
        }
        return false;
    }

    private static boolean within(Position a, Position b, float range) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        return dx * dx + dy * dy <= range * range;
    }
}
