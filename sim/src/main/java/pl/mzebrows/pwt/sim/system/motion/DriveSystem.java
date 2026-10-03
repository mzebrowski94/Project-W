package pl.mzebrows.pwt.sim.system.motion;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.GroundDef;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Pace;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Turns what a unit wants into what it is doing: commits {@link Steering} to {@link Velocity}, once,
 * after every system that had something to say about it.
 *
 * <p>The only place a decision becomes motion (G0010). It clears the desired velocity after committing
 * it, so standing still is the default. <strong>A held unit is left entirely alone</strong>, or its
 * throw would be deleted.
 *
 * <p><strong>What it commits is scaled by {@link Pace}</strong>, which is how much of what it asked
 * for the unit may actually have - a beam held on it, the ground it is standing in, or both. Scaling
 * here rather than in each driver is the same argument the class makes for committing here: a slow
 * applied by whoever happened to write the course last would be a slow that depended on which system
 * spoke, and one applied to the course rather than to the speed would bend it. A pace changes how
 * long a unit takes to get somewhere and never where it was going.
 */
public final class DriveSystem extends IteratingSystem {

    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);

    private final TileMap map;
    private final Defs<Terrain, GroundDef> ground;
    private final Modifiers modifiers;

    public DriveSystem(TileMap map, Defs<Terrain, GroundDef> ground, Modifiers modifiers) {
        super(Family.all(Steering.class, Velocity.class).get());
        this.map = map;
        this.ground = ground;
        this.modifiers = modifiers;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Steering steering = STEERING.get(entity);
        if (!Drivers.held(entity)) {
            // Where the SPEED stat is read: a driven unit's pace, folded (ADR 0017).
            float pace = modifiers.pace(entity, Pace.of(entity, map, ground));
            VELOCITY.get(entity).set(steering.desiredX * pace, steering.desiredY * pace);
        }
        steering.clear();
    }
}
