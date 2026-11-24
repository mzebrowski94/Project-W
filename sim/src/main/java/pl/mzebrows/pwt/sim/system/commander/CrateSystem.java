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
import pl.mzebrows.pwt.sim.system.combat.Grants;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Reach;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * Everything about a crate after the key that opens it (G0056): dealing what it holds, handing over what
 * its opener takes, and shutting it on him.
 *
 * <p><strong>Dealt on its first opening</strong> - how many from a bag, two or three unless an upgrade says
 * more; which from the armoury deck, a bag of its own so no card comes up twice before every card has; then
 * whatever an upgrade stocks every crate with, swapped in from the last card back. It keeps what it holds
 * from then on, whoever opens it next.
 *
 * <p><strong>No crate holds two cards of one kind</strong> (G0110): a card of a kind it already holds goes back and
 * another is drawn, and a deck with no other kind left makes a smaller crate. A stock swaps its card in only where
 * the crate does not hold one. <strong>Looter</strong> adds one card more, of a kind new to it, the first time a
 * commander holding it opens a crate - dealt into the deal, or drawn then if the crate was dealt before - and never
 * again for that crate, whoever's Looter opens it next.
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
            int extra = crate.looted || crate.openedOn != tick ? 0 : modifiers.crateExtra(man);
            if (!crate.dealt) {
                deal(crate, ARMOURY.get(level), extra);
            } else if (extra > 0) {
                draw(crate, ARMOURY.get(level), crate.count + extra);
            }
            if (extra > 0) {
                crate.looted = true;
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

    /** What a crate holds, dealt once, on its first opening; {@code extra} more for a Looter opening it. */
    private void deal(Crate crate, Armoury armoury, int extra) {
        crate.count = 0;
        draw(crate, armoury, size(armoury) + extra);
        stock(crate);
        crate.dealt = true;
    }

    /** Draws cards into the crate up to {@code size}, never past its capacity, while the deck has a kind it lacks. */
    private void draw(Crate crate, Armoury armoury, int size) {
        int wanted = Math.min(SimConfig.CrateConfig.CAPACITY, size);
        while (crate.count < wanted) {
            @Nullable PickupKind kind = card(armoury, crate);
            if (kind == null) {
                return;
            }
            crate.put(crate.count, kind);
            crate.count++;
        }
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
     * has stacked it - is shuffled again before it deals. A card of a kind the crate already holds is never
     * drawn (G0110): it stays in the bag for the next crate. A shuffle with nothing left but those is shuffled
     * afresh; null when even a fresh one holds no other kind.
     */
    private @Nullable PickupKind card(Armoury armoury, Crate crate) {
        int total = 0;
        long barred = 0L;
        for (int d = 0; d < deck.length; d++) {
            deckNow[d] = modifiers.armouryCards(deck[d], deckBase[d]);
            if (crate.indexOf(deck[d]) >= 0) {
                barred |= run(total, deckNow[d]);
            }
            total += deckNow[d];
        }
        if (total == 0) {
            return null;
        }
        if (armoury.shuffledAt != total) {
            armoury.drawn = 0L;
            armoury.shuffledAt = total;
        }
        long whole = run(0, total);
        if (((armoury.drawn | barred) & whole) == whole) {
            if ((barred & whole) == whole) {
                return null;
            }
            armoury.drawn = 0L;
        }
        int card = Bag.draw(rng, armoury.drawn | barred, total);
        armoury.drawn = Bag.take(armoury.drawn, card, total);
        for (int d = 0; d < deck.length; d++) {
            if (card < deckNow[d]) {
                return deck[d];
            }
            card -= deckNow[d];
        }
        throw new IllegalStateException("card " + card + " is past the end of a deck of " + total);
    }

    /** The bits of {@code count} cards from the {@code from}-th, as a bag holds them. */
    private static long run(int from, int count) {
        if (count <= 0) {
            return 0L;
        }
        long bits = count >= Bag.MOST ? -1L : (1L << count) - 1L;
        return bits << from;
    }

    /**
     * Whatever an upgrade stocks every crate with - no shipped card does since G0127: kept where the deck already dealt one,
     * and otherwise swapped in for the last card not already a stock's - one, since no crate holds two of a kind
     * (G0110).
     */
    private void stock(Crate crate) {
        int locked = 0;
        for (PickupKind kind : deck) {
            if (crate.count == 0 || modifiers.crateStock(kind) <= 0) {
                continue;
            }
            int at = crate.indexOf(kind);
            if (at >= 0) {
                locked |= 1 << at;
                continue;
            }
            for (int i = crate.count - 1; i >= 0; i--) {
                if ((locked & (1 << i)) == 0) {
                    crate.put(i, kind);
                    locked |= 1 << i;
                    break;
                }
            }
        }
    }
}
