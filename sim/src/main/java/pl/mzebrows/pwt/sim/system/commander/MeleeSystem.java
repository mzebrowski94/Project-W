package pl.mzebrows.pwt.sim.system.commander;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Turns anything's melee intent into a swing in the world.
 *
 * <p><strong>One place turns a swing into a strike entity</strong> (G0005), whoever swung. The
 * commander's intent comes from input and a trooper's from its own judgement, but both go through
 * {@code Melee.beginSwing} and are released here - so the windup, the arc and the cooldown cannot
 * drift apart between the two.
 *
 * <p>It spawns a {@code Strike} entity and nothing else: no overlap query, no damage, no knowledge of
 * what is nearby (G0005). Whether the swing connects is the collision system's business, and it
 * answers it with the rules it already has.
 *
 * <p>The swing is aimed, not thrown in the direction of travel (G0002) - the sweep starts half an arc
 * behind where the player is pointing and travels through it, so pointing at a target puts that
 * target in the middle of the swing.
 *
 * <p>Which half it starts in alternates: a sweep crosses the aim one way, the next swing crosses it
 * the other, like a blade. It is the sweep's own body that turns round, so what a swing reaches first
 * changes with it and the drawing cannot disagree with the hit (G0005, pillar 5). The count of swings
 * decides it and nothing else - no dice, no clock - so it holds across a pause and a snapshot.
 */
public final class MeleeSystem extends IteratingSystem {

    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Melee> MELEE = ComponentMapper.getFor(Melee.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);

    /** Below this the aim point is on top of the commander and gives no usable direction. */
    private static final float EPSILON = 1e-4f;

    private static final float HALF = 0.5f;

    private final Spawner spawner;

    public MeleeSystem(Spawner spawner) {
        super(Family.all(Position.class, Melee.class, Faction.class).get());
        this.spawner = spawner;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (Drivers.held(entity) && !Drivers.gripped(entity)) {
            // A man in something's jaws is held and may still swing: that is how he gets out (G0045).
            return;
        }
        Melee melee = MELEE.get(entity);
        if (INTENT.has(entity)) {
            Intent intent = INTENT.get(entity);
            if (intent.striking && melee.ready() && !Drivers.handsFull(entity)) {
                melee.beginSwing(intent.aimX, intent.aimY);
            }
        }
        if (melee.releasing()) {
            release(entity, melee);
        }
    }

    private void release(Entity entity, Melee melee) {
        Position position = POSITION.get(entity);
        float toAimX = melee.pendingAimX - position.x;
        float toAimY = melee.pendingAimY - position.y;
        melee.released();
        if (toAimX * toAimX + toAimY * toAimY <= EPSILON * EPSILON) {
            return;
        }
        float aimAngle = (float) StrictMath.atan2(toAimY, toAimX);
        // Which way this swing crosses the aim, and its arc signed that way round.
        float sweep = melee.nextSweepDirection() * melee.arcDegrees * MathUtils.degreesToRadians;
        // It still starts half an arc before the aim whichever way it goes: the sign says only which
        // way the body travels, so every swing covers the same ground centred on where he points.
        spawner.spawnStrike(
                entity, position.x, position.y, aimAngle - sweep * HALF, sweep, melee, FACTION.get(entity).side);
    }
}
