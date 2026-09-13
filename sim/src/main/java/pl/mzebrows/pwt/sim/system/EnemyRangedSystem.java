package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Weapon;

/**
 * An alien that shoots: it holds its ground at a distance and spits, roughly, at what it is hunting.
 *
 * <p>The counterpart of {@code TrooperCombatSystem} on the other side of the field, and deliberately
 * the same shape - a range test, a clear line, a gun that is ready, a projectile spawned from the
 * same {@code Spawner} call. Everything that makes a shooter a shooter already existed; this decides
 * only when to pull the trigger and in roughly which direction.
 *
 * <p><strong>It shoots what it is hunting, which is the commander.</strong> Nothing on the alien
 * side has ever targeted a trooper - {@code Perception} latches onto him and only him - and
 * inventing a second targeting rule inside a firing system would settle a design question by
 * accident. The squad is still in danger, because the cone is wide enough that a shot meant for him
 * reaches the men standing near him, which is most of what a firing line does to a squad anyway.
 * Whether an alien should ever aim at a trooper is an open question in {@code docs/design}.
 *
 * <p><strong>The spread is drawn from a stream of its own</strong> (pillar 6). Sharing one with the
 * leap would mean re-tuning how often something shoots quietly reshuffles when everything jumps -
 * the failure that is reproducible, so no test catches it, and miserable to iterate against.
 *
 * <p>The cone is applied by rotating the aim, not by jittering a target point. Rotating keeps every
 * shot the same length and the cone the same width at every distance; nudging a point makes a
 * creature wilder the further away it is, which is backwards.
 *
 * <p>It runs after everything that decides where an enemy wants to be going, because planting a
 * shooter has to beat the course avoidance just finished bending - and before {@code DriveSystem},
 * because a desired velocity is spent on the tick it is written.
 */
public final class EnemyRangedSystem extends EntitySystem {

    private static final ComponentMapper<Ranged> RANGED = ComponentMapper.getFor(Ranged.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);

    /** Clear of its own body, so nothing shoots itself in the foot. */
    private static final float MUZZLE_GAP = 0.1f;

    private static final float EPSILON = 1e-4f;

    private final TileMap map;
    private final Spawner spawner;
    private final Rng rng;

    private ImmutableArray<Entity> shooters = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    public EnemyRangedSystem(TileMap map, Spawner spawner, Rng rng) {
        this.map = map;
        this.spawner = spawner;
        this.rng = rng;
    }

    @Override
    public void addedToEngine(Engine engine) {
        shooters = engine.getEntitiesFor(
                Family.all(Enemy.class, Ranged.class, Weapon.class, Position.class, Body.class, Faction.class)
                        .get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        Position target = Drivers.livingTarget(commanders);
        if (target == null) {
            return;
        }
        for (int i = 0; i < shooters.size(); i++) {
            Entity entity = shooters.get(i);
            Ranged ranged = RANGED.get(entity);
            if (!ranged.enabled || Drivers.held(entity)) {
                continue;
            }
            if (PERCEPTION.has(entity) && !PERCEPTION.get(entity).aggro) {
                continue;
            }
            Position at = POSITION.get(entity);
            if (!Sight.withinRange(at.x, at.y, target.x, target.y, ranged.range)) {
                continue;
            }
            if (!map.clearLine(at.x, at.y, target.x, target.y)) {
                // It will not fire into rock. The shot would stop there anyway; what this saves is
                // the picture of a creature emptying itself into a wall, which reads as broken.
                continue;
            }
            if (ranged.haltsToFire && STEERING.has(entity)) {
                STEERING.get(entity).clear();
            }
            fire(entity, ranged, at, target);
        }
    }

    /** One shot, roughly at him, whenever the gun is ready. */
    private void fire(Entity entity, Ranged ranged, Position at, Position target) {
        Weapon weapon = WEAPON.get(entity);
        if (!weapon.ready()) {
            return;
        }
        float toX = target.x - at.x;
        float toY = target.y - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        float aimX = toX / distance;
        float aimY = toY / distance;
        float turn = (rng.nextFloat() * 2f - 1f) * ranged.halfSpreadRadians;
        float cos = (float) StrictMath.cos(turn);
        float sin = (float) StrictMath.sin(turn);
        float directionX = aimX * cos - aimY * sin;
        float directionY = aimX * sin + aimY * cos;
        float muzzle = BODY.get(entity).radius + weapon.projectileRadius + MUZZLE_GAP;
        spawner.spawnProjectile(
                at.x + directionX * muzzle,
                at.y + directionY * muzzle,
                directionX,
                directionY,
                weapon,
                FACTION.get(entity).side);
        weapon.remainingSeconds = weapon.cooldownSeconds;
    }
}
