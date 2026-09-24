package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Players;
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
        // Ascending slot, so when two commanders shout at one trooper on the same tick the higher
        // slot is heard last and therefore wins. Whatever order Ashley holds the family in would
        // also be an answer, but it changes the moment somebody is killed and respawned - and a
        // rule that quiet is a rule nobody can play around.
        for (int slot = 0; slot < standing.length; slot++) {
            Entity commander = Players.inSlot(commanders, slot);
            if (commander == null) {
                continue;
            }
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
                    give(TROOPER.get(entity), order, slot);
                }
            }
        }
    }

    /** Who is on his feet, once, so the trooper loop below is a lookup rather than a search. */
    private void takeStock() {
        for (int slot = 0; slot < standing.length; slot++) {
            Entity commander = Players.inSlot(commanders, slot);
            standing[slot] = commander != null && !Drivers.down(commander);
        }
    }

    /**
     * Drops every standing order whose giver is on the ground.
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
     *
     * <p><strong>Each commander releases only his own men.</strong> With one commander that is the
     * rule as it was written; with four, releasing the lot when any of them fell would let a death
     * across the map cancel the formation somebody else was holding. What is orphaned is what he
     * said, and nothing else.
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
     * <p>Two of these stand and two do not. Follow and defence are states a trooper stays in until it
     * is told otherwise; forward is a burst of ground to cover plus permission to leave a point
     * (G0029), so it lands as a distance and a flag rather than as a standing order, and expires by
     * being spent.
     *
     * <p><strong>Forward also drops whatever standing order it had</strong>, because every one of
     * them is a reason not to advance. Cancel still exists and is not the same thing: it stops the
     * squad without sending it anywhere, and it is the one key that puts a man back to his own
     * judgement rather than giving him something else to do.
     */
    private void give(Trooper trooper, Order order, int slot) {
        trooper.orderedBy = slot;
        switch (order) {
            case FOLLOW_ME -> trooper.order = Order.FOLLOW_ME;
            case DEFENCE -> trooper.order = Order.DEFENCE;
            case FORWARD -> {
                // Pushing on cancels whatever it was doing instead of advancing - holding a place,
                // or keeping station on him. Either would swallow the order: a man told to hold
                // pushes on and then stands still again, and a man following stays at your shoulder
                // while you walk. Having to cancel first was one press too many for the order that
                // sets a level's pace.
                trooper.order = null;
                trooper.forwardRemaining = forwardDistance;
                // And it leaves the point it is holding. One order buys one point: the flag is spent
                // when it reaches the next, so the squad advances at the commander's pace rather than
                // running the level on its own.
                trooper.sentOn = true;
            }
            case CANCEL -> {
                trooper.order = null;
                trooper.forwardRemaining = 0f;
            }
        }
    }
}
