package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strike;

/**
 * Sweeps a swing through its arc and takes it away when it is done.
 *
 * <p>Runs after movement and before collision, so the sweep is where it should be for this tick by
 * the time contacts are found. It writes a position directly rather than a velocity because it
 * travels on an arc, and a velocity integrated in a straight line for a tick would cut the corner.
 *
 * <p><strong>The arc is re-centred on whoever swung it before it is advanced</strong>, which is the
 * whole of what keeps a sweep in front of a man who is walking. It matters that this runs after
 * movement: the centre is where he ended up this tick, not where he set off from. A swing whose
 * owner has left the world keeps the centre it last had rather than snapping to the corner of the
 * arena, so a commander killed mid-swing does not fling his own fist across the level.
 */
public final class StrikeSystem extends IteratingSystem {

    private static final ComponentMapper<Strike> STRIKE = ComponentMapper.getFor(Strike.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    private final Spawner spawner;

    /** Every entity with a handle, for finding one named by it. */
    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

    public StrikeSystem(Spawner spawner) {
        super(Family.all(Strike.class, Position.class).get());
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        handled = engine.getEntitiesFor(Family.all(Handle.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Strike strike = STRIKE.get(entity);
        Entity owner = Handles.resolve(handled, strike.owner);
        if (owner != null && POSITION.has(owner)) {
            Position at = POSITION.get(owner);
            strike.originX = at.x;
            strike.originY = at.y;
        }
        strike.elapsedSeconds += deltaTime;
        if (strike.spent()) {
            spawner.markDead(entity);
            return;
        }
        float angle = strike.angle();
        POSITION.get(entity)
                .set(
                        strike.originX + (float) StrictMath.cos(angle) * strike.reach,
                        strike.originY + (float) StrictMath.sin(angle) * strike.reach);
    }
}
