package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Order;

/** A standing order, carried out: the half of {@link TrooperMindSystem}'s cascade below the fight. */
final class TrooperOrders {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);

    private final TileMap map;
    private final float bodyRadius;
    private final float followAhead;
    private final float followDistance;

    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    TrooperOrders(SimConfig config, TileMap map) {
        this.map = map;
        this.bodyRadius = config.trooper().radius();
        this.followAhead = config.trooper().followAheadSeconds();
        this.followDistance = config.trooper().followDistance();
    }

    /** Takes the views it reads from the engine, once, as its system is added. */
    void bind(Engine engine) {
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
    }

    /**
     * Carries out a standing order, if there is one worth acting on.
     *
     * <p>Below the fight and above the objective, which is the whole of 3.e. Nothing here has to
     * remember being interrupted: a trooper that was shooting simply never reached this branch, and
     * reaches it again the tick the shooting stops.
     *
     * <p>"Forward" is not handled here at all - it is a burst of pace on the way to the objective
     * rather than somewhere else to be, so it falls through to the objective and is spent by
     * {@code TrooperMoveSystem} as ground is covered.
     */
    boolean obeys(Trooper trooper, Position at) {
        if (trooper.order == Order.DEFENCE) {
            trooper.mode = Trooper.Mode.HOLD;
            trooper.clearTarget();
            return true;
        }
        if (trooper.order != Order.FOLLOW_ME) {
            return false;
        }
        // The man who said it, not whichever commander happens to be first. With four of them,
        // following anybody else would make "follow me" the one order the player cannot aim.
        Entity giver = Players.inSlot(commanders, trooper.orderedBy);
        if (giver == null || Drivers.down(giver)) {
            // Belt and braces: his death already released the order upstream this tick. Falling
            // through to the objective is still better than standing about waiting for a corpse.
            return false;
        }
        Position commander = POSITION.get(giver);
        trooper.mode = Trooper.Mode.FOLLOW;
        keepUpWith(trooper, at, commander, VELOCITY.get(giver));
        return true;
    }

    /**
     * Keeps up with where the commander is going rather than where he is.
     *
     * <p>A follower that walks at the man himself chases the spot he has just left, so a squad strings
     * out on his heels and cuts every corner behind him. Walking at where he will be in a moment - the
     * squad formations of <em>Company of Heroes</em>, shrunk to one multiply - brings it alongside him
     * when he turns. Once it is within its following distance it is aimed at the man himself, so it
     * settles round him and never overtakes him into his own line of fire.
     *
     * <p>The point ahead is dropped where it would be inside rock; the route to him is always good.
     */
    private void keepUpWith(Trooper trooper, Position at, Position commander, Velocity moving) {
        float aheadX = commander.x + moving.x * followAhead;
        float aheadY = commander.y + moving.y * followAhead;
        boolean closeEnough = Sight.withinRange(at.x, at.y, commander.x, commander.y, followDistance);
        if (followAhead <= 0f || closeEnough || !map.isClear(aheadX, aheadY, bodyRadius)) {
            trooper.headFor(commander.x, commander.y);
            return;
        }
        trooper.headFor(aheadX, aheadY);
    }
}
