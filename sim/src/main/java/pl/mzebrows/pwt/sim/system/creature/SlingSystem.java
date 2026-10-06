package pl.mzebrows.pwt.sim.system.creature;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Sling;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.system.level.Brood;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Slings a unit's riders at whoever it hunts, one at a time, and spills the rest round its body when it dies (G0074).
 *
 * <ul>
 *   <li><strong>The tell is the tint, and it stands for it.</strong> Only once what it hunts is between its two
 *       ranges does it wind up; {@code Pace} stops its legs while it does.
 *   <li><strong>Aimed once, as the rider leaves</strong>, at a place past its target along the line - so a rider
 *       comes down behind the man it was slung at, and stepping aside after it has left changes nothing. Its flight
 *       is {@code EnemyLeapSystem}'s, as every leap's is.
 *   <li><strong>A declared stun breaks the wind-up off</strong> and the rider stays aboard (G0053, G0071). A stagger
 *       does not: up close is claw range.
 *   <li><strong>Riders are never reloaded.</strong> What is still aboard when it dies spills round its body through
 *       {@link Brood}, already hunting as a nest's last brood is - and, unlike a nest, rousing nobody.
 * </ul>
 *
 * <p>Who it slings at is read off its own {@code Perception} (G0026), as every alien attack reads it.
 */
public final class SlingSystem extends EntitySystem {

    private static final ComponentMapper<Sling> SLING = ComponentMapper.getFor(Sling.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Strain> STRAIN = ComponentMapper.getFor(Strain.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);

    /** Below this a direction is treated as no direction, rather than normalised into a huge one. */
    private static final float EPSILON = 1e-4f;

    private final SimConfig config;
    private final Spawner spawner;
    private final Rng rng;
    private final Brood brood;

    /** How far clear of its body a rider leaves: the one gap every shot and throw leaves by. */
    private final float muzzleGap;

    private ImmutableArray<Entity> slingers = new ImmutableArray<>(new Array<>());

    /** The list an alien hunts from, which riders spilling are kept off. */
    private ImmutableArray<Entity> quarry = new ImmutableArray<>(new Array<>());

    public SlingSystem(SimConfig config, TileMap map, Spawner spawner, Rng rng) {
        this.config = config;
        this.spawner = spawner;
        this.rng = rng;
        this.brood = new Brood(map, spawner, config.nest().hatchKeepOff());
        this.muzzleGap = config.muzzleGap();
    }

    @Override
    public void addedToEngine(Engine engine) {
        slingers = engine.getEntitiesFor(
                Family.all(Sling.class, Position.class, Perception.class, Body.class, Strain.class, Stunned.class)
                        .get());
        quarry = engine.getEntitiesFor(Family.all(Faction.class, Position.class)
                .one(Health.class, Vitality.class)
                .get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < slingers.size(); i++) {
            Entity entity = slingers.get(i);
            Sling sling = SLING.get(entity);
            if (!sling.loaded()) {
                continue;
            }
            if (Life.dying(entity)) {
                spill(entity, sling);
                continue;
            }
            switch (sling.phase) {
                case READY -> windUpIfInRange(entity, sling);
                case WINDING_UP -> windUp(entity, sling);
                case RESTING -> {
                    sling.remainingTicks--;
                    if (sling.remainingTicks <= 0) {
                        sling.breakOff();
                    }
                }
            }
        }
    }

    /** The tell starts once what it hunts is between its two ranges, and it is on its feet to show it. */
    private static void windUpIfInRange(Entity entity, Sling sling) {
        Perception perception = PERCEPTION.get(entity);
        if (!perception.aggro || Drivers.held(entity)) {
            return;
        }
        Position at = POSITION.get(entity);
        float toX = perception.targetX - at.x;
        float toY = perception.targetY - at.y;
        float squared = toX * toX + toY * toY;
        if (squared < sling.minRange * sling.minRange || squared > sling.maxRange * sling.maxRange) {
            return;
        }
        sling.phase = Sling.Phase.WINDING_UP;
        sling.remainingTicks = sling.windUpTicks;
    }

    /** Counts the tell down, broken off by a declared stun, and lets the rider go at its end. */
    private void windUp(Entity entity, Sling sling) {
        Stunned stunned = STUNNED.get(entity);
        if (stunned.active() && stunned.dazed) {
            sling.breakOff();
            return;
        }
        sling.remainingTicks--;
        if (sling.remainingTicks > 0) {
            return;
        }
        Perception perception = PERCEPTION.get(entity);
        if (!perception.aggro) {
            // Nobody left to sling at: the rider stays aboard.
            sling.breakOff();
            return;
        }
        release(entity, sling, perception);
    }

    /**
     * The rider leaves, aimed once: along the line to its target, to come down {@code landBehind} past him, from a
     * muzzle gap clear of its body.
     */
    private void release(Entity entity, Sling sling, Perception perception) {
        UnitKind rider = sling.rider;
        Position at = POSITION.get(entity);
        float toX = perception.targetX - at.x;
        float toY = perception.targetY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (rider == null || distance <= EPSILON) {
            sling.breakOff();
            return;
        }
        float directionX = toX / distance;
        float directionY = toY / distance;
        float clear = BODY.get(entity).radius + muzzleGap + config.unit(rider).radius();
        float flight = Math.max(distance + sling.landBehind - clear, 0f);
        int airborneTicks = Math.max(1, Ticks.of(flight / sling.speed, config.tickSeconds()));
        spawner.spawnSlung(
                rider,
                at.x + directionX * clear,
                at.y + directionY * clear,
                STRAIN.get(entity).wave,
                directionX,
                directionY,
                sling.speed,
                airborneTicks,
                sling.landingTicks);
        sling.riders--;
        sling.phase = Sling.Phase.RESTING;
        sling.remainingTicks = sling.restTicks;
    }

    /** Whatever is still aboard, round its body, once: it carries none afterwards. */
    private void spill(Entity entity, Sling sling) {
        UnitKind rider = sling.rider;
        if (rider != null) {
            brood.hatch(
                    rng,
                    rider,
                    config.unit(rider).radius(),
                    STRAIN.get(entity).wave,
                    POSITION.get(entity),
                    BODY.get(entity).radius,
                    sling.spillRadius,
                    sling.riders,
                    quarry);
        }
        sling.riders = 0;
        sling.breakOff();
    }
}
