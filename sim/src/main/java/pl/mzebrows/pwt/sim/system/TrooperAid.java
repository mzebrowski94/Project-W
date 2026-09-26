package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;

/** Going to pick up somebody who is down: the half of {@link TrooperMindSystem}'s cascade below orders. */
final class TrooperAid {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private final Rng nerve;
    private final Chances chances;
    private final float reach;

    private ImmutableArray<Entity> casualties = new ImmutableArray<>(new Array<>());

    TrooperAid(SimConfig config, Rng nerve) {
        this.nerve = nerve;
        this.chances = config.chances();
        this.reach = config.action().reach();
    }

    /** Takes the views it reads from the engine, once, as its system is added. */
    void bind(Engine engine) {
        casualties = engine.getEntitiesFor(
                Family.all(Casualty.class, Position.class, Body.class).get());
    }

    /**
     * Goes to pick somebody up, if there is somebody and this man is willing.
     *
     * <p>Below the order and above the objective, which is the whole of where a rescue sits. The
     * candidate has to be somewhere this trooper could have seen - its own sight range, rather than a
     * number of its own - so "it went for a man it could see go down" is the rule, and a squad does
     * not converge on a body across the map.
     *
     * <p><strong>The dice are consulted once, on the way in.</strong> A trooper already in the mode
     * keeps going: re-rolling every tick of an approach would have men setting off and thinking
     * better of it halfway, which reads as a bug rather than as judgement. It stops only when there
     * is nobody left within range - because he was picked up, because somebody else got there, or
     * because he ran out - and then falls through to the objective on the same tick.
     *
     * <p>What it heads for is a <em>place</em> (G0010). Which body it is standing over is asked again
     * by {@code AidSystem} every tick, from the entities actually in reach, so a casualty reaped
     * between ticks cannot come back as whatever was built from the recycled component.
     */
    boolean aids(Entity entity, Trooper trooper, Position at, float deltaTime) {
        Entity subject = nearestCasualty(entity, at, trooper.sightRange);
        if (subject == null) {
            return false;
        }
        if (trooper.mode != Trooper.Mode.AID) {
            // One due for both kinds of rescue: it is one willingness, spent faster for the commander.
            trooper.aidDue = Odds.spend(nerve, trooper.aidDue, chances, willingness(subject), deltaTime);
            if (!Odds.spent(trooper.aidDue)) {
                return false;
            }
        }
        trooper.mode = Trooper.Mode.AID;
        if (Aid.gapBetween(at, BODY.get(entity).radius, subject) <= reach) {
            // Standing over him. Nothing to head for, so it stops where it is and gets on with it.
            trooper.clearTarget();
        } else {
            Position where = POSITION.get(subject);
            trooper.headFor(where.x, where.y);
        }
        return true;
    }

    /**
     * How readily this trooper would go for that particular man.
     *
     * <p>The commander is worth far more than a comrade, and that is the whole of "more frequently
     * the commander": no ranking, no second pass over the candidates, just a number that makes a man
     * with two bodies in front of him set off for the right one far more often than not.
     */
    private static AiChance willingness(Entity subject) {
        return COMMANDER.has(subject) ? AiChance.AID_THE_COMMANDER : AiChance.AID_A_TROOPER;
    }

    /**
     * The nearest man on the ground within this trooper's sight range, or null if there is none.
     *
     * <p>By straight-line distance rather than by route, unlike an objective: a body you can see is
     * one you go to, and a squad that worked out the walking distance to each casualty before setting
     * off would be doing arithmetic in a firefight. Nobody is offered himself, and nothing already
     * dying is offered at all - the work would be spent on a corpse.
     */
    private @Nullable Entity nearestCasualty(Entity helper, Position at, float range) {
        Entity nearest = null;
        float shortest = range * range;
        for (int i = 0; i < casualties.size(); i++) {
            Entity candidate = casualties.get(i);
            if (Objects.equals(candidate, helper) || !CASUALTY.get(candidate).begun) {
                continue;
            }
            if (DYING.has(candidate) && DYING.get(candidate).begun) {
                continue;
            }
            Position where = POSITION.get(candidate);
            float dx = where.x - at.x;
            float dy = where.y - at.y;
            float distance = dx * dx + dy * dy;
            if (distance > shortest) {
                continue;
            }
            shortest = distance;
            nearest = candidate;
        }
        return nearest;
    }
}
