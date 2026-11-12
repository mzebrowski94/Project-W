package pl.mzebrows.pwt.sim.system.death;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.BlastsOnDeath;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.system.query.Sight;

/**
 * Runs the blasts on the field: each catches what is standing in it, once, and then burns out.
 *
 * <p><strong>A blast is on nobody's side.</strong> One radius and death inside it, no falloff (pillar
 * 1); the drawn circle is the lethal one. Rock and a shut door stop it (ADR 0022). It owes damage, which lands the tick
 * <em>after</em> it appears - so a row of barrels cascades.
 *
 * <p><strong>A shut door it reaches is broken outright</strong> (G0069): measured to the nearest part of the
 * doorway, since a door has no body, along a line that may end in the door's own tiles (ADR 0022).
 */
public final class BlastSystem extends EntitySystem {

    private static final ComponentMapper<Blast> BLAST = ComponentMapper.getFor(Blast.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<BlastsOnDeath> REACTION = ComponentMapper.getFor(BlastsOnDeath.class);
    private static final ComponentMapper<Door> DOOR = ComponentMapper.getFor(Door.class);
    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);
    private static final ComponentMapper<Handle> HANDLE = ComponentMapper.getFor(Handle.class);

    private final Spawner spawner;
    private final Solid map;

    private ImmutableArray<Entity> blasts = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> victims = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> detonating = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> doors = new ImmutableArray<>(new Array<>());

    public BlastSystem(Solid map, Spawner spawner) {
        this.map = map;
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        blasts = engine.getEntitiesFor(
                Family.all(Blast.class, Position.class, Body.class).get());
        victims = engine.getEntitiesFor(Family.all(Damage.class, Position.class)
                .one(Health.class, Vitality.class)
                .exclude(Door.class)
                .get());
        doors = engine.getEntitiesFor(
                Family.all(Door.class, Health.class, Damage.class, Handle.class).get());
        detonating = engine.getEntitiesFor(
                Family.all(BlastsOnDeath.class, Dead.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = blasts.size() - 1; i >= 0; i--) {
            Entity entity = blasts.get(i);
            if (entity.isScheduledForRemoval()) {
                continue;
            }
            Blast blast = BLAST.get(entity);
            if (!blast.spent) {
                blast.spent = true;
                Position at = POSITION.get(entity);
                catchEveryoneInside(at.x, at.y, BODY.get(entity).radius, blast.damage, blast.harm, blast.credit);
            }
            if (blast.remainingTicks <= 0) {
                spawner.markDead(entity);
            }
        }
        leaveBlastsWhereTheyFell();
    }

    /**
     * The death reaction, between marked dead and reaped (G0004); once per death. A blast spawned here
     * goes off next tick.
     */
    private void leaveBlastsWhereTheyFell() {
        for (int i = 0; i < detonating.size(); i++) {
            Entity entity = detonating.get(i);
            BlastsOnDeath reaction = REACTION.get(entity);
            float radius = reaction.radius;
            if (radius <= 0f) {
                // No radius, nothing left: a barrel swallowed whole (G0022).
                continue;
            }
            Position at = POSITION.get(entity);
            spawner.spawnBlast(at.x, at.y, radius, reaction.leavesFire, reaction.look, reaction.credit, reaction.harm);
        }
    }

    /**
     * Centre to centre, deliberately: a rule the player can see applied.
     */
    private void catchEveryoneInside(float x, float y, float radius, int damage, Harm harm, int credit) {
        for (int i = 0; i < victims.size(); i++) {
            Entity victim = victims.get(i);
            Position at = POSITION.get(victim);
            if (!Sight.withinRange(x, y, at.x, at.y, radius) || !Sight.clearLine(map, x, y, at.x, at.y, true)) {
                continue;
            }
            Damage owed = DAMAGE.get(victim);
            // Decisive for anything with health, less what it shrugs off (G0082); a man takes the blast kind's
            // harm (G0018, G0053).
            Health health = HEALTH.get(victim);
            owed.land(health == null ? damage : health.fromBlast(damage), harm, credit);
            owed.cameFrom(x, y);
        }
        breakEveryDoorInside(x, y, radius, harm, credit);
    }

    /** Every shut door the blast reaches, broken outright: whatever it has left is what it takes (G0069). */
    private void breakEveryDoorInside(float x, float y, float radius, Harm harm, int credit) {
        float tileSize = map.map().tileSize();
        for (int i = 0; i < doors.size(); i++) {
            Entity entity = doors.get(i);
            Door door = DOOR.get(entity);
            if (door.open || door.distanceFrom(x, y, tileSize) > radius) {
                continue;
            }
            Position middle = POSITION.get(entity);
            if (!map.clearLineTo(HANDLE.get(entity).id, x, y, middle.x, middle.y)) {
                continue;
            }
            DAMAGE.get(entity).land(HEALTH.get(entity).current, harm, credit);
        }
    }
}
