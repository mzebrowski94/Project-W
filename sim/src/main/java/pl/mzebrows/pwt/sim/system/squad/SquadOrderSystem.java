package pl.mzebrows.pwt.sim.system.squad;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Sight;

/**
 * Passes the commander's orders to the troopers near enough to hear them (G0017).
 *
 * <p><strong>Near enough, and no further</strong>: no selection, no roster. Delivered
 * <strong>once</strong>, on the tick it is given, then it stands on whoever heard it. It writes a
 * field; {@code TrooperMindSystem} decides what it does (3.e).
 */
public final class SquadOrderSystem extends EntitySystem {

    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);

    private final float earshot;
    private final float forwardDistance;

    /** Which slots have a commander on his feet this tick. Filled once a tick, read per trooper. */
    private final boolean[] standing;

    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());

    public SquadOrderSystem(SimConfig config, int capacity) {
        this.earshot = config.trooper().orderRadius();
        this.forwardDistance = config.trooper().forwardDistance();
        this.standing = new boolean[capacity];
    }

    @Override
    public void addedToEngine(Engine engine) {
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Intent.class, Position.class).get());
        troopers =
                engine.getEntitiesFor(Family.all(Trooper.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        takeStock();
        release();
        // Ascending slot: on a tie the higher slot is heard last and wins, stable across respawns.
        for (int slot = 0; slot < standing.length; slot++) {
            Entity commander = Players.inSlot(commanders, slot);
            if (commander == null) {
                continue;
            }
            @Nullable Order order = INTENT.get(commander).order;
            if (order == null || Drivers.held(commander)) {
                // A held commander is not giving orders.
                continue;
            }
            Position at = POSITION.get(commander);
            for (int t = 0; t < troopers.size(); t++) {
                Entity entity = troopers.get(t);
                if (Drivers.held(entity)) {
                    continue;
                }
                Position where = POSITION.get(entity);
                if (Sight.withinRange(at.x, at.y, where.x, where.y, earshot)) {
                    give(TROOPER.get(entity), order, slot);
                }
            }
        }
    }

    /** Who is on his feet, once, so the trooper loop below is a lookup rather than a search. */
    private void takeStock() {
        for (int slot = 0; slot < standing.length; slot++) {
            Entity commander = Players.inSlot(commanders, slot);
            standing[slot] = commander != null && !Life.down(commander);
        }
    }

    /**
     * Drops every standing order whose giver is on the ground.
     *
     * <p><strong>His going down releases the squad</strong> - standing orders and the burst of pace
     * alike - and nothing is restored when he comes back. <strong>Each commander releases only his own
     * men.</strong>
     */
    private void release() {
        for (int i = 0; i < troopers.size(); i++) {
            Trooper trooper = TROOPER.get(troopers.get(i));
            if (trooper.order == null && trooper.forwardRemaining <= 0f && !trooper.sentOn) {
                continue;
            }
            if (standing[trooper.orderedBy]) {
                continue;
            }
            trooper.order = null;
            trooper.orderedBy = 0;
            trooper.forwardRemaining = 0f;
            trooper.sentOn = false;
        }
    }

    /**
     * Writes one order onto one trooper.
     *
     * <p>Follow and defence stand; forward is a distance and a flag, spent (G0029). <strong>Forward also
     * drops any standing order</strong>. Cancel stops the squad without sending it anywhere.
     */
    private void give(Trooper trooper, Order order, int slot) {
        trooper.orderedBy = slot;
        switch (order) {
            case FOLLOW_ME -> trooper.order = Order.FOLLOW_ME;
            case DEFENCE -> trooper.order = Order.DEFENCE;
            case FORWARD -> {
                // Pushing on cancels a hold or a follow, either of which would swallow it.
                trooper.order = null;
                trooper.forwardRemaining = forwardDistance;
                // And it leaves the point it is holding: one order buys one point.
                trooper.sentOn = true;
            }
            case CANCEL -> {
                trooper.order = null;
                trooper.forwardRemaining = 0f;
            }
        }
    }
}
