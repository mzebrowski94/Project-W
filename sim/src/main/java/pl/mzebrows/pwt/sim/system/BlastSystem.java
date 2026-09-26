package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.BlastsOnDeath;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Runs the blasts on the field: each catches what is standing in it, once, and then burns out.
 *
 * <p><strong>A blast is on nobody's side.</strong> It asks nothing about factions, so it kills the
 * commander, his squad and the aliens alike - which is what makes throwing one a decision rather than
 * a free button, and what keeps pillar 2 intact when the thing that kills him is his own.
 *
 * <p>One radius, and death inside it. No falloff: a curve the player cannot see turns "am I clear of
 * that?" into arithmetic, and pillar 1 says a mechanic that makes the player stop and compute belongs
 * in a different game. The circle drawn is the circle that kills, because both are the entity's own
 * {@code Body}.
 *
 * <p>Rock stops it, by the same clear-line test perception already uses - otherwise a grenade kills
 * round corners, and the standing rule that rock stops a shot would hold for bullets and not for
 * explosions.
 *
 * <p>It owes damage rather than applying it, exactly as contact resolution does: {@code DamageSystem}
 * is the only thing that reduces health. That system has already run by the time this one does, so a
 * blast lands on the tick <em>after</em> it appears - which is what makes a row of barrels go off as a
 * cascade rather than all at once.
 *
 * <p>An {@code EntitySystem} rather than an {@code IteratingSystem} because a blast asks a question
 * about every damageable body, so the two families are walked against each other. There is almost
 * never a blast on the field, and never more than a handful, so this costs nothing on the ticks it
 * does nothing.
 */
public final class BlastSystem extends EntitySystem {

    private static final ComponentMapper<Blast> BLAST = ComponentMapper.getFor(Blast.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<BlastsOnDeath> REACTION = ComponentMapper.getFor(BlastsOnDeath.class);

    private final Spawner spawner;
    private final TileMap map;

    private ImmutableArray<Entity> blasts = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> victims = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> detonating = new ImmutableArray<>(new Array<>());

    public BlastSystem(TileMap map, Spawner spawner) {
        this.map = map;
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        blasts = engine.getEntitiesFor(
                Family.all(Blast.class, Position.class, Body.class).get());
        victims = engine.getEntitiesFor(
                Family.all(Health.class, Damage.class, Position.class).get());
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
                catchEveryoneInside(at.x, at.y, BODY.get(entity).radius, blast.damage);
            }
            if (blast.remainingTicks <= 0) {
                spawner.markDead(entity);
            }
        }
        leaveBlastsWhereTheyFell();
    }

    /**
     * The death reaction, run in the window between a thing being marked dead and being reaped -
     * where every reaction to a death belongs (G0004), and the same window {@code RespawnSystem} uses
     * one system earlier.
     *
     * <p>It runs once per death by construction: the entity it reads is taken off the field at the end
     * of this tick, so there is no second visit to guard against.
     *
     * <p>A blast spawned here is not in the family above until the engine has flushed this system, so
     * it goes off on the next tick rather than this one. That delay is what makes a row of barrels
     * read as a cascade instead of one flat bang.
     */
    private void leaveBlastsWhereTheyFell() {
        for (int i = 0; i < detonating.size(); i++) {
            Entity entity = detonating.get(i);
            BlastsOnDeath reaction = REACTION.get(entity);
            float radius = reaction.radius;
            if (radius <= 0f) {
                // A reaction with no radius leaves nothing: a barrel swallowed whole rather than set
                // off (G0022). Still one death and still one path to a blast - this one just has no
                // circle to draw.
                continue;
            }
            Position at = POSITION.get(entity);
            spawner.spawnBlast(at.x, at.y, radius, reaction.leavesFire, reaction.look);
        }
    }

    /**
     * Measured centre to centre, deliberately. "Your middle is inside the circle" is a rule a player
     * can see being applied; "your edge clipped it" is one they would have to be told about.
     */
    private void catchEveryoneInside(float x, float y, float radius, float damage) {
        for (int i = 0; i < victims.size(); i++) {
            Entity victim = victims.get(i);
            Position at = POSITION.get(victim);
            if (!Sight.withinRange(x, y, at.x, at.y, radius) || !Sight.clearLine(map, x, y, at.x, at.y, true)) {
                continue;
            }
            Damage owed = DAMAGE.get(victim);
            // Wounding, like gunfire: a blast is still uniformly decisive inside its circle
            // (G0018) - everything in it goes down - but what it leaves behind depends on whether
            // anything on that side could come and get you. An alien cannot be rescued and dies.
            owed.add(damage, 0, false);
            owed.cameFrom(x, y);
        }
    }
}
