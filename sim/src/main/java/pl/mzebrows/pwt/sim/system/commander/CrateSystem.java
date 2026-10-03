package pl.mzebrows.pwt.sim.system.commander;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.Bag;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Armoury;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Presence;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.system.Grants;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Reach;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Everything about a crate after the key that opens it (G0056): dealing what it holds, handing over what
 * its opener takes, and shutting it on him.
 *
 * <p><strong>Dealt on its first opening</strong> - how many from a bag, two or three unless an upgrade says
 * more; which from the armoury deck, a bag of its own so no card comes up twice before every card has; then
 * whatever an upgrade stocks every crate with, swapped in from the last card back. It keeps what it holds
 * from then on, whoever opens it next.
 *
 * <p><strong>It shuts on him</strong> the tick he takes a wound or goes down, steps out of arm's length,
 * drops his link, takes something, or says so; and it goes from the field the tick it is empty. A take of
 * something he is offered nothing by - a medkit to a man with no wound (G0053) - is turned down and leaves
 * it open.
 *
 * <p>Straight after {@code ActionSystem}, which opens it: a crate opened this tick is dealt this tick, and
 * the interface shows its cards on the frame after the press. After {@code DamageSystem}, so a wound this
 * tick shuts it this tick.
 */
public final class CrateSystem extends EntitySystem {

    private static final ComponentMapper<Crate> CRATE = ComponentMapper.getFor(Crate.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<Armoury> ARMOURY = ComponentMapper.getFor(Armoury.class);
    private static final ComponentMapper<Presence> PRESENCE = ComponentMapper.getFor(Presence.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);

    private final Spawner spawner;
    private final Modifiers modifiers;
    private final Grants grants;
    private final Rng rng;
    private final Defs<PickupKind, PickupDef> pickups;
    private final SimConfig.CrateConfig sizes;

    /** Every card the armoury deck holds, in kind order: the order its cards are counted in. */
    private final PickupKind[] deck;

    /** What each of those holds before any upgrade adds to it, in the same order. */
    private final int[] deckBase;

    /** Scratch: what each holds on this deal, folded. Overwritten before every read. */
    private final int[] deckNow;

    private ImmutableArray<Entity> crates = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

    public CrateSystem(SimConfig config, Spawner spawner, Modifiers modifiers, Rng rng) {
        this.spawner = spawner;
        this.modifiers = modifiers;
        this.grants = new Grants(spawner, modifiers);
        this.rng = rng;
        this.pickups = config.pickups();
        this.sizes = config.crate();
        int held = 0;
        for (PickupKind kind : PickupKind.values()) {
            held += pickups.of(kind).armoury().inDeck() ? 1 : 0;
        }
        this.deck = new PickupKind[held];
        this.deckBase = new int[held];
        this.deckNow = new int[held];
        int d = 0;
        for (PickupKind kind : PickupKind.values()) {
            if (pickups.of(kind).armoury().inDeck()) {
                deck[d] = kind;
                deckBase[d] = pickups.of(kind).armoury().cards();
                d++;
            }
        }
    }

    @Override
    public void addedToEngine(Engine engine) {
        crates = engine.getEntitiesFor(Family.all(Crate.class, Position.class).get());
        commanders = engine.getEntitiesFor(Family.all(Commander.class, Intent.class, Action.class, Position.class)
                .get());
        levels = engine.getEntitiesFor(Family.all(Level.class, Armoury.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (levels.size() == 0) {
            return;
        }
        Entity level = levels.first();
        long tick = LEVEL.get(level).tick;
        @Nullable Presence presence = PRESENCE.has(level) ? PRESENCE.get(level) : null;
        for (int i = 0; i < crates.size(); i++) {
            Entity entity = crates.get(i);
            Crate crate = CRATE.get(entity);
            if (!crate.open()) {
                continue;
            }
            @Nullable Entity man = Players.inSlot(commanders, crate.openedBy);
            if (man == null
                    || DEAD.has(entity)
                    || Life.down(man)
                    || Drivers.aboard(man)
                    || (presence != null && presence.isAway(crate.openedBy))) {
                crate.close();
                continue;
            }
            if (!crate.dealt) {
                deal(crate, ARMOURY.get(level));
            }
            int wounds = VITALITY.has(man) ? VITALITY.get(man).wounds : 0;
            if (crate.openedOn != tick && wounds > crate.openerWounds) {
                crate.close();
                continue;
            }
            crate.openerWounds = wounds;
            if (Reach.gap(man, entity) > ACTION.get(man).reach) {
                crate.close();
                continue;
            }
            Intent intent = INTENT.get(man);
            PickupKind take = intent.crateTake;
            if (take != null) {
                take(entity, crate, man, take);
            } else if (intent.crateClose) {
                crate.close();
            }
        }
    }

    /**
     * Hands him what he chose, if it is still there and worth something to him; shuts the crate on a take,
     * and takes an emptied crate off the field.
     */
    private void take(Entity entity, Crate crate, Entity man, PickupKind kind) {
        int at = crate.indexOf(kind);
        if (at < 0) {
            return;
        }
        Grant grant = pickups.of(kind).grantFor(FACTION.get(man).side);
        if (!Grant.isSomething(grant) || !Grants.worthTo(grant, man)) {
            return;
        }
        grants.apply(man, grant, levels);
        crate.remove(at);
        crate.close();
        if (crate.count == 0) {
            spawner.markDead(entity);
        }
    }

    /** What a crate holds, dealt once, on its first opening. */
    private void deal(Crate crate, Armoury armoury) {
        int size = size(armoury);
        for (int i = 0; i < size; i++) {
            crate.put(i, card(armoury));
        }
        stock(crate, size);
        crate.count = size;
        crate.dealt = true;
    }

    /**
     * How many it holds, from a bag over the fewest to the most (pillar 6's streak argument: the fewest three
     * times running is a level the player blames). An upgrade that raises the fewest refills the bag.
     */
    private int size(Armoury armoury) {
        int fewest = Math.min(SimConfig.CrateConfig.CAPACITY, modifiers.crateLeast(sizes.fewest()));
        int most = Math.min(SimConfig.CrateConfig.CAPACITY, Math.max(fewest, sizes.most()));
        int span = most - fewest + 1;
        if (armoury.sizesFrom != fewest) {
            armoury.sizesDrawn = 0L;
            armoury.sizesFrom = fewest;
        }
        int drawn = Bag.draw(rng, armoury.sizesDrawn, span);
        armoury.sizesDrawn = Bag.take(armoury.sizesDrawn, drawn, span);
        return fewest + drawn;
    }

    /**
     * The next card from the armoury deck, from a bag: no card comes up twice before every card has. The
     * deck's counts are folded afresh (ADR 0017); a deck that has grown since its last shuffle - an upgrade
     * has stacked it - is shuffled again before it deals.
     */
    private PickupKind card(Armoury armoury) {
        int total = 0;
        for (int d = 0; d < deck.length; d++) {
            deckNow[d] = modifiers.armouryCards(deck[d], deckBase[d]);
            total += deckNow[d];
        }
        if (armoury.shuffledAt != total) {
            armoury.drawn = 0L;
            armoury.shuffledAt = total;
        }
        int card = Bag.draw(rng, armoury.drawn, total);
        armoury.drawn = Bag.take(armoury.drawn, card, total);
        for (int d = 0; d < deck.length; d++) {
            if (card < deckNow[d]) {
                return deck[d];
            }
            card -= deckNow[d];
        }
        throw new IllegalStateException("card " + card + " is past the end of a deck of " + total);
    }

    /**
     * Whatever an upgrade stocks every crate with (Firepower's flamer): what the deck dealt is kept where it
     * already is one, and swapped for it from the last card back until there are enough - never over a card
     * another stock has just put there.
     */
    private void stock(Crate crate, int size) {
        int locked = 0;
        for (PickupKind kind : deck) {
            int wanted = Math.min(size, modifiers.crateStock(kind));
            if (wanted == 0) {
                continue;
            }
            int have = 0;
            for (int i = 0; i < size && have < wanted; i++) {
                if (crate.get(i) == kind && (locked & (1 << i)) == 0) {
                    locked |= 1 << i;
                    have++;
                }
            }
            for (int i = size - 1; i >= 0 && have < wanted; i--) {
                if ((locked & (1 << i)) == 0) {
                    crate.put(i, kind);
                    locked |= 1 << i;
                    have++;
                }
            }
        }
    }
}
