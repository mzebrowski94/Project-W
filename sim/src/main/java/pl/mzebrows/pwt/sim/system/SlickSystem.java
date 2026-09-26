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
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.component.SlicksOnDeath;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * Leaves a spatter of ruined ground where something carrying one died, and cleans it up again when
 * it has had its time.
 *
 * <p>It lands no damage of its own, unlike {@code BlastSystem}: every circle is an ordinary body with
 * an ordinary blow, carrying the side of whatever died. A death reaction (G0018), for shots and blasts
 * alike (G0035). Nothing is left inside rock.
 */
public final class SlickSystem extends EntitySystem {

    private static final ComponentMapper<Slick> SLICK = ComponentMapper.getFor(Slick.class);
    private static final ComponentMapper<SlicksOnDeath> REACTION = ComponentMapper.getFor(SlicksOnDeath.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    /** Two pi, for turning a fraction of a turn into an angle. */
    private static final float FULL_TURN = 6.2831855f;

    private final Spawner spawner;
    private final TileMap map;
    private final Rng rng;

    private ImmutableArray<Entity> slicks = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> bursting = new ImmutableArray<>(new Array<>());

    public SlickSystem(TileMap map, Spawner spawner, Rng rng) {
        this.map = map;
        this.spawner = spawner;
        this.rng = rng;
    }

    @Override
    public void addedToEngine(Engine engine) {
        slicks = engine.getEntitiesFor(Family.all(Slick.class).get());
        bursting = engine.getEntitiesFor(
                Family.all(SlicksOnDeath.class, Dead.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = slicks.size() - 1; i >= 0; i--) {
            Entity entity = slicks.get(i);
            if (entity.isScheduledForRemoval()) {
                continue;
            }
            Slick slick = SLICK.get(entity);
            if (slick.spent()) {
                spawner.markDead(entity);
                continue;
            }
            if (slick.animated()) {
                // The body is the drawing, so drawn and dangerous stay the same interval (G0034).
                BODY.get(entity).radius = slick.radiusNow();
            }
        }
        leaveSpattersWhereTheyFell();
    }

    /**
     * The death reaction, run in the window between a thing being marked dead and being reaped, where
     * every reaction to a death belongs (G0004).
     *
     * <p>Once per death: what it reads is reaped this tick.
     */
    private void leaveSpattersWhereTheyFell() {
        for (int i = 0; i < bursting.size(); i++) {
            Entity entity = bursting.get(i);
            SlicksOnDeath reaction = REACTION.get(entity);
            if (reaction.blobs <= 0 || reaction.radius <= 0f) {
                continue;
            }
            Position at = POSITION.get(entity);
            Side side = FACTION.has(entity) ? FACTION.get(entity).side : Side.NEUTRAL;
            for (int blob = 0; blob < reaction.blobs; blob++) {
                scatterOne(at, side, reaction, blob);
            }
        }
    }

    /**
     * One circle of the spatter. The first is on the burst itself and the rest are thrown round it,
     * so a slick always has a middle where the shot actually landed.
     *
     * <p>Its own stream. {@link StrictMath}, once per circle of a burst (pillar 6).
     */
    private void scatterOne(Position at, Side side, SlicksOnDeath reaction, int blob) {
        float x = at.x;
        float y = at.y;
        if (blob > 0 && reaction.spread > 0f) {
            float angle = rng.nextFloat() * FULL_TURN;
            // The square root spreads the circles evenly over the disc.
            float distance = reaction.spread * (float) Math.sqrt(rng.nextFloat());
            x += (float) StrictMath.cos(angle) * distance;
            y += (float) StrictMath.sin(angle) * distance;
        }
        if (!map.passableAt(x, y)) {
            // Its middle is in the wall: a splash may lap against rock, never sit inside it.
            return;
        }
        // Equal bounds draw nothing, so retuning fire cannot reshuffle venom (pillar 6).
        float seconds = reaction.secondsMax > reaction.seconds
                ? reaction.seconds + rng.nextFloat() * (reaction.secondsMax - reaction.seconds)
                : reaction.seconds;
        spawner.spawnSlick(
                x,
                y,
                reaction.radius,
                seconds,
                reaction.growFraction,
                reaction.fadeFraction,
                reaction.damage,
                reaction.victimInvulnerableSeconds,
                reaction.look,
                side);
    }
}
