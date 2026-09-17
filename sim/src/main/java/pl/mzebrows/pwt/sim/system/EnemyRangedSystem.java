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
 * <p><strong>It has a field of view, and it has to swing onto you.</strong> Only what is inside the
 * arc it declares is fired at, and its aim walks onto a target at a rate rather than snapping - which
 * is what makes stepping round one a real move and the arc a rule the player can feel rather than a
 * number in a file. The arc is wider than a trooper's: a thing built around a gun should be harder to
 * get behind than a man is.
 *
 * <p><strong>It never advances on what it is hunting</strong> when its strain holds ground. That is
 * {@code NavigationSystem}'s to enforce, because walking at things is its job, and it is named here
 * because it is half of what a shooter <em>is</em>: you are driven off, not chased down.
 *
 * <p><strong>A shooter's facing is its aim, always.</strong> With something to shoot at, the aim
 * swings onto it; with nothing, it follows wherever the creature is about to walk - so a spitter
 * crossing the ground to eat something faces the thing it is going to eat rather than the fight it
 * has left behind, and an idling one sweeps about as it ambles. One source for the facing, and no
 * state that says "aiming" separately from where the gun points.
 *
 * <p><strong>The first shot is the wild one.</strong> Every shot it lands on the same target narrows
 * the cone towards the floor its strain declares, and being close narrows it further and shortens the
 * wait between shots. Losing it - by range, by rock, or by leaving its arc - throws all of that away.
 * Standing in a shooter's sights is therefore the mistake, rather than being shot at once.
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

    /** Close enough to dead behind that there is no shorter way round to lerp along. */
    private static final float ANTIPARALLEL = -0.9999f;

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
        for (int i = 0; i < shooters.size(); i++) {
            Entity entity = shooters.get(i);
            Ranged ranged = RANGED.get(entity);
            if (!ranged.enabled || Drivers.held(entity)) {
                continue;
            }
            Position at = POSITION.get(entity);
            boolean hunting = !PERCEPTION.has(entity) || PERCEPTION.get(entity).aggro;
            if (!hunting) {
                // It has lost him, not merely lost sight of him. That is what releases the position
                // it took as well as the aim it had walked in.
                ranged.giveUpPosition();
            }
            if (target == null || !hasASolution(entity, ranged, at, target)) {
                ranged.loseTarget();
                lookWhereItIsGoing(entity, ranged, deltaTime);
                continue;
            }
            if (ranged.haltsToFire && !ranged.holdsGround && STEERING.has(entity)) {
                // Planting a unit that was closing. A unit that never closes has nothing to plant -
                // and clearing its course here would delete the amble that stops it reading as a
                // turret, because this runs after the wander that wrote it.
                STEERING.get(entity).clear();
            }
            float toX = target.x - at.x;
            float toY = target.y - at.y;
            float distance = (float) Math.sqrt(toX * toX + toY * toY);
            if (distance <= EPSILON) {
                continue;
            }
            if (distance <= ranged.preferredRange()) {
                // It has the ground it wanted. From here it holds it, however far he goes - which is
                // a fact about arriving rather than about shooting, because it opens fire on the way
                // in and a first shot at the edge of its reach must not pin it out there.
                ranged.inPosition = true;
            }
            float aimX = toX / distance;
            float aimY = toY / distance;
            swingOnto(ranged, aimX, aimY, deltaTime);
            if (ranged.aimX * aimX + ranged.aimY * aimY < ranged.arcCosine) {
                // Still coming round. It has you in range and cannot bring the gun to bear yet,
                // which is the whole of what its field of view buys the player.
                continue;
            }
            fire(entity, ranged, at, aimX, aimY, distance);
        }
    }

    /**
     * Whether it has something it could shoot at this tick: noticed, inside its range, with nothing
     * solid in between. Whether there is a living target at all is asked at the call site, once.
     *
     * <p>It will not fire into rock. The shot would stop there anyway; what this saves is the picture
     * of a creature emptying itself into a wall, which reads as broken.
     */
    private boolean hasASolution(Entity entity, Ranged ranged, Position at, Position target) {
        if (PERCEPTION.has(entity) && !PERCEPTION.get(entity).aggro) {
            return false;
        }
        if (!Sight.withinRange(at.x, at.y, target.x, target.y, ranged.range)) {
            return false;
        }
        return map.clearLine(at.x, at.y, target.x, target.y);
    }

    /**
     * Points the gun where the creature is going, for as long as it has nothing to shoot at.
     *
     * <p><strong>A shooter's facing is its aim</strong>, because its aim is the thing the player has
     * to read. The consequence is that a shooter with a stale aim walks about pointing at where a
     * fight used to be - which on the field looks exactly like a creature walking backwards, and is
     * what playtesting caught. So when there is nothing to aim at, the aim follows what it is doing:
     * it turns to face the food it is crossing the ground for, and sweeps about as it ambles.
     *
     * <p>It reads the <em>desired</em> velocity rather than the velocity, because that is where it is
     * about to go: everything that decides a course for an alien has already run this tick, and
     * nothing has moved yet. A creature standing still keeps the aim it had, which is what lets it
     * hold a bearing between waypoints instead of snapping back to nothing.
     */
    private static void lookWhereItIsGoing(Entity entity, Ranged ranged, float deltaTime) {
        if (!STEERING.has(entity)) {
            return;
        }
        Steering steering = STEERING.get(entity);
        float length = (float) Math.sqrt(steering.desiredX * steering.desiredX + steering.desiredY * steering.desiredY);
        if (length <= EPSILON) {
            return;
        }
        swingOnto(ranged, steering.desiredX / length, steering.desiredY / length, deltaTime);
    }

    /**
     * Swings its aim towards where the target is, by a fraction of what is left to cover.
     *
     * <p>An exponential approach rather than a fixed number of degrees per second: it turns quickly
     * when it is badly out and settles rather than overshooting, and - the part that matters here -
     * it is a lerp between two unit vectors, so it needs no trigonometry at all. Rotating by an angle
     * would, and {@code Math.sin} and {@code Math.cos} are banned in this module because they are
     * specified only to within an ulp and may differ between JVMs (pillar 6).
     *
     * <p>A turn rate of zero means it is always already looking at you, which is what everything that
     * shot before there were fields of view declares.
     */
    private static void swingOnto(Ranged ranged, float aimX, float aimY, float deltaTime) {
        if (ranged.turnRate <= 0f || !ranged.aiming()) {
            ranged.aim(aimX, aimY);
            return;
        }
        float blend = ranged.turnRate * deltaTime;
        if (blend >= 1f) {
            ranged.aim(aimX, aimY);
            return;
        }
        float swungX;
        float swungY;
        if (ranged.aimX * aimX + ranged.aimY * aimY <= ANTIPARALLEL) {
            // Dead behind it. The two directions cancel, so lerping between them only shortens the
            // aim along its own line and renormalising puts it straight back - a shooter with
            // somebody exactly at its six would turn for ever. Nudge off the line by the same amount
            // it would have turned, and from the next tick there is a shorter way round to take.
            swungX = ranged.aimX - aimY * blend;
            swungY = ranged.aimY + aimX * blend;
        } else {
            swungX = ranged.aimX + (aimX - ranged.aimX) * blend;
            swungY = ranged.aimY + (aimY - ranged.aimY) * blend;
        }
        float length = (float) Math.sqrt(swungX * swungX + swungY * swungY);
        if (length <= EPSILON) {
            ranged.aim(aimX, aimY);
            return;
        }
        ranged.aim(swungX / length, swungY / length);
    }

    /** One shot, roughly at him, whenever the gun is ready - and less roughly than the last one. */
    private void fire(Entity entity, Ranged ranged, Position at, float aimX, float aimY, float distance) {
        Weapon weapon = WEAPON.get(entity);
        if (!weapon.ready()) {
            return;
        }
        float turn = (rng.nextFloat() * 2f - 1f) * ranged.halfSpreadFor(distance);
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
        weapon.remainingSeconds = ranged.cooldownFor(weapon.cooldownSeconds, distance);
        ranged.shotsOnTarget++;
    }
}
