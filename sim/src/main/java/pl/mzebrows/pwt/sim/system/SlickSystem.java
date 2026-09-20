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
 * <p>It lands no damage of its own, which is the whole difference between this and
 * {@code BlastSystem}. A blast is an event and has to ask every damageable body whether it was
 * standing in the circle on the one tick it went off; a slick is a <em>place</em> and every circle
 * of it is an ordinary body with an ordinary blow, so who it catches is decided by the collision
 * system with the rules it already has. <strong>That includes the side</strong>: a circle carries
 * the faction of whatever fired the shot, so venom is a problem for the player's men and not for the
 * things that spat it - the one way a slick differs from a blast, and an invariant rather than a
 * line of code.
 *
 * <p>It sits with the death reactions, immediately after {@code BlastSystem}, because it is one:
 * everything that leaves something behind does so by dying (G0018), so a shot that burst on a wall,
 * one that burst on a man and one that simply ran out of flight all arrive here the same way.
 *
 * <p><strong>A blast arrives here too</strong> (G0035). An explosion is a thing that dies like any
 * other, so the fire it leaves is the same reaction asked of a different carrier - which is why
 * nothing that explodes had to learn that fire exists, and why a gun that leaves no blast leaves no
 * fire either. The one difference is the side: a blast carries no faction, so its fire is on
 * nobody's and burns whoever walks into it, where a frog's venom belongs to the frog.
 *
 * <p>Nothing is left inside rock. A shot is thrown away by {@code ProjectileBoundsSystem} the moment
 * its middle reaches a wall, so a burst against one is a burst just short of it - but the circles
 * are scattered around that point and some of the scatter would land in the wall, and "nothing is
 * spawned inside rock" is an invariant rather than a preference.
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
                // The body is the drawing, so a circle that is swelling reaches further and one that
                // is collapsing reaches less far, by the same number. That is what keeps G0034's
                // promise - drawn and dangerous are the same interval - true of something that moves.
                // Only fire is animated, and there are never many circles of it, so this is a write
                // per burning circle per tick and no arithmetic at all for ruined ground.
                BODY.get(entity).radius = slick.radiusNow();
            }
        }
        leaveSpattersWhereTheyFell();
    }

    /**
     * The death reaction, run in the window between a thing being marked dead and being reaped, where
     * every reaction to a death belongs (G0004).
     *
     * <p>It runs once per death by construction: what it reads is taken off the field at the end of
     * this tick, so there is no second visit to guard against.
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
     * <p>The scatter is drawn from a stream of its own, so re-tuning how a spatter looks cannot
     * reshuffle where a nest's brood lands or how badly a spitter aims.
     *
     * <p>The trigonometry here is paid once per circle of one burst, which happens when a shot ends
     * rather than per entity per tick. It is {@link StrictMath} rather than {@code Math} for the
     * reason everything in this module is: the latter's transcendentals are specified only to within
     * an ulp and may differ between JVMs, which pillar 6 does not allow.
     */
    private void scatterOne(Position at, Side side, SlicksOnDeath reaction, int blob) {
        float x = at.x;
        float y = at.y;
        if (blob > 0 && reaction.spread > 0f) {
            float angle = rng.nextFloat() * FULL_TURN;
            // The square root is what spreads the circles evenly over the disc rather than bunching
            // them in the middle, which is the difference between a spatter and a rosette.
            float distance = reaction.spread * (float) Math.sqrt(rng.nextFloat());
            x += (float) StrictMath.cos(angle) * distance;
            y += (float) StrictMath.sin(angle) * distance;
        }
        if (!map.passableAt(x, y)) {
            // Its middle is in the wall. The same test that throws a shot away when it reaches rock,
            // asked of each circle: a splash may lap against a wall, and none of it may be inside one.
            return;
        }
        // How long this one circle lasts, drawn between the two the reaction names. Ruined ground
        // names the same number twice and draws nothing at all, so re-tuning how long a fire burns
        // cannot reshuffle where a spatter of venom lands (pillar 6).
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
