package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * Passes the commander's orders to the troopers near enough to hear them (G0017).
 *
 * <p><strong>Near enough, and no further.</strong> There is no selection and no roster: who hears an
 * order is decided by where the commander is standing, which is the one thing that makes commanding
 * interesting. When the squad is together - which pillar 4 says is most of the time - the radius
 * costs the player nothing; when it is strung out, getting close enough to be heard is a real
 * decision made with the same input as everything else, movement.
 *
 * <p>An order is delivered <strong>once</strong>, on the tick it is given, and then stands on
 * whoever heard it. Holding the key does not keep issuing it to whatever has since wandered into
 * range, and a trooper that walks away later keeps what it was told - which is what "follow me"
 * has to mean to be worth saying.
 *
 * <p>Nothing here decides what an order <em>does</em>. It writes a field; {@code TrooperMindSystem}
 * reads it, below the fight and above the objective. That is the ordering 3.e asks for, and it needs
 * no queue: a trooper interrupted by a fight has not lost its order, because nothing consumed it.
 */
public final class SquadOrderSystem extends EntitySystem {

    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);

    private final float earshot;
    private final float moveOutDistance;

    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());

    public SquadOrderSystem(SimConfig config) {
        this.earshot = config.trooper().orderRadius();
        this.moveOutDistance = config.trooper().moveOutDistance();
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
        if (Drivers.livingTarget(commanders) == null) {
            release();
            return;
        }
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            @Nullable Order order = INTENT.get(commander).order;
            if (order == null || Drivers.held(commander)) {
                // A commander held still by a blow is not giving orders. Being able to shout while
                // clinched would make the one moment he is genuinely out of the fight free.
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
                    give(TROOPER.get(entity), order);
                }
            }
        }
    }

    /**
     * Drops every standing order, because the man who gave them is on the ground.
     *
     * <p><strong>His going down releases the squad</strong>, which is the same rule the other side
     * already lives by: nothing stands to attention over a corpse. A squad holding a formation around
     * a body, or still walking to where he was when he died, is the game holding its breath - and the
     * seconds after a death are supposed to be a beat rather than a freeze frame.
     *
     * <p>It clears the burst of pace as well as the standing orders, so one death means one rule
     * rather than "some of what he said survives him". The ring goes out with it, so what the player
     * sees and what the squad is doing agree again.
     *
     * <p>Orders are <strong>not</strong> restored when he comes back. An order is a live instruction
     * from a living commander; resuming a formation he asked for two deaths ago would be the game
     * remembering something the player has long stopped meaning.
     */
    private void release() {
        for (int i = 0; i < troopers.size(); i++) {
            Trooper trooper = TROOPER.get(troopers.get(i));
            trooper.order = null;
            trooper.moveOutRemaining = 0f;
        }
    }

    /**
     * Writes one order onto one trooper.
     *
     * <p>Two of these stand and two do not. Follow and stop are states a trooper stays in until it is
     * told otherwise; move out is a burst of ground to cover, so it lands as a distance rather than
     * as a standing order and expires by being spent. Cancel is the only one that removes both, which
     * is what makes it a single key rather than a mode the player has to remember they are in.
     */
    private void give(Trooper trooper, Order order) {
        switch (order) {
            case FOLLOW_ME -> trooper.order = Order.FOLLOW_ME;
            case STOP -> trooper.order = Order.STOP;
            case MOVE_OUT -> {
                // Pushing on cancels standing still, or the order would be swallowed by the last one.
                if (trooper.order == Order.STOP) {
                    trooper.order = null;
                }
                trooper.moveOutRemaining = moveOutDistance;
            }
            case CANCEL -> {
                trooper.order = null;
                trooper.moveOutRemaining = 0f;
            }
        }
    }
}
