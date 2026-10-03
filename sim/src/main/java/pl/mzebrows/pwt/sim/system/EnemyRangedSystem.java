package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.system.query.Aim;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Sight;

/**
 * An alien that shoots: it holds its ground at a distance and spits, roughly, at what it is hunting.
 *
 * <p><strong>It shoots what {@code PerceptionSystem} published</strong> (G0026), never a target of its
 * own. The creature's cone rotates the aim, drawn from its own stream (pillar 6); the gun then
 * scatters each pellet. <strong>It has a field of view and swings onto you</strong> at a rate.
 * <strong>Its facing is its aim, always</strong>: with nothing to shoot, the aim follows where it is
 * about to walk.
 *
 * <p><strong>The first shot is the wild one</strong>: shots on one target and closeness narrow the
 * cone; losing the target throws that away. Holding ground is {@code NavigationSystem}'s.
 */
public final class EnemyRangedSystem extends EntitySystem {

    private static final ComponentMapper<Ranged> RANGED = ComponentMapper.getFor(Ranged.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);

    private static final float EPSILON = 1e-4f;

    private final TileMap map;
    private final Guns guns;

    /** How badly the creature aims, which is a different thing from the gun's own pattern. */
    private final Rng rng;

    /** Scratch, so swinging an aim costs no allocation per shooter per tick. */
    private final float[] swung = new float[2];

    /** Scratch for the place it is hunting, so reading it costs no allocation per shooter per tick. */
    private final Position hunting = new Position();

    private ImmutableArray<Entity> shooters = new ImmutableArray<>(new Array<>());

    public EnemyRangedSystem(TileMap map, Guns guns, Rng rng) {
        this.map = map;
        this.guns = guns;
        this.rng = rng;
    }

    @Override
    public void addedToEngine(Engine engine) {
        shooters = engine.getEntitiesFor(
                Family.all(Enemy.class, Ranged.class, Weapon.class, Position.class, Body.class, Faction.class)
                        .get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < shooters.size(); i++) {
            Entity entity = shooters.get(i);
            Ranged ranged = RANGED.get(entity);
            if (!ranged.enabled || Drivers.held(entity)) {
                continue;
            }
            Position at = POSITION.get(entity);
            boolean hunting = !PERCEPTION.has(entity) || PERCEPTION.get(entity).aggro;
            @Nullable Position target = hunting ? hunted(entity) : null;
            if (!hunting) {
                // Lost him outright: releases the position as well as the aim.
                ranged.giveUpPosition();
            }
            if (target == null || !hasASolution(entity, ranged, at, target)) {
                ranged.loseTarget();
                lookWhereItIsGoing(entity, ranged, deltaTime);
                continue;
            }
            if (ranged.haltsToFire && !ranged.holdsGround && STEERING.has(entity)) {
                // Planting a unit that was closing; one that never closes keeps its amble.
                STEERING.get(entity).clear();
            }
            float toX = target.x - at.x;
            float toY = target.y - at.y;
            float distance = (float) Math.sqrt(toX * toX + toY * toY);
            if (distance <= EPSILON) {
                continue;
            }
            if (distance <= ranged.preferredRange()) {
                // Arrived: it holds from here. About arriving, not about the first shot.
                ranged.inPosition = true;
            }
            float aimX = toX / distance;
            float aimY = toY / distance;
            swingOnto(ranged, aimX, aimY, deltaTime);
            if (ranged.aimX * aimX + ranged.aimY * aimY < ranged.arcCosine) {
                // In range, still coming round.
                continue;
            }
            fire(entity, ranged, aimX, aimY, distance);
        }
    }

    /**
     * Where whatever this shooter is hunting was standing this tick, or null if it is hunting nobody.
     *
     * <p>Read from what {@code PerceptionSystem} published (G0026).
     */
    private @Nullable Position hunted(Entity entity) {
        if (!PERCEPTION.has(entity)) {
            return null;
        }
        Perception perception = PERCEPTION.get(entity);
        hunting.set(perception.targetX, perception.targetY);
        return hunting;
    }

    /**
     * Whether it has something it could shoot at this tick: noticed, inside its range, with nothing
     * solid in between. Whether there is a living target at all is asked at the call site, once.
     *
     * <p>It will not fire into rock.
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
     * <p>Reads the <em>desired</em> velocity: where it is about to go. Standing still keeps the aim.
     */
    private void lookWhereItIsGoing(Entity entity, Ranged ranged, float deltaTime) {
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
     * <p>An exponential approach, a lerp between unit vectors: no trigonometry (pillar 6). A turn rate
     * of zero is always already looking at you.
     */
    private void swingOnto(Ranged ranged, float aimX, float aimY, float deltaTime) {
        if (!ranged.aiming()) {
            // No bearing to come round from. Everything else is Aim's.
            ranged.aim(aimX, aimY);
            return;
        }
        Aim.swing(ranged.aimX, ranged.aimY, aimX, aimY, ranged.turnRate, deltaTime, swung);
        ranged.aim(swung[0], swung[1]);
    }

    /** One shot, roughly at him, whenever the gun is ready - and less roughly than the last one. */
    private void fire(Entity entity, Ranged ranged, float aimX, float aimY, float distance) {
        Weapon weapon = WEAPON.get(entity);
        if (!guns.ready(entity)) {
            return;
        }
        float turn = (rng.nextFloat() * 2f - 1f) * ranged.halfSpreadFor(distance);
        float cos = (float) StrictMath.cos(turn);
        float sin = (float) StrictMath.sin(turn);
        float directionX = aimX * cos - aimY * sin;
        float directionY = aimX * sin + aimY * cos;
        // The creature's cone is spent once per volley; each pellet's scatter is the gun's.
        guns.pull(entity, directionX, directionY, Tally.NOBODY, ranged.cooldownFor(weapon.cooldownTicks, distance));
        ranged.shotsOnTarget++;
    }
}
