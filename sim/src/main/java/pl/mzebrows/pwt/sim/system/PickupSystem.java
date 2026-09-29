package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.Bag;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Armoury;
import pl.mzebrows.pwt.sim.component.BlastsOnDeath;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.ArmouryDef;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Turns the pickups claimed this tick into whatever they grant, and spends the ones that were taken.
 *
 * <p>Contact records the claim; this decides what it means. <strong>The switch is exhaustive over a
 * sealed type on purpose</strong>: a new grant does not compile until somebody decides what it does.
 * Runs before damage: a grunt that reaches a flower first gets up whole. A spent pickup is marked dead.
 *
 * <p>Then it deals every sealed cache a commander has come near its gun, from the armoury deck (G0050),
 * and recasts it in place as that gun's cache. After the claims, so a cache revealed this tick can be
 * taken on the next at the earliest - and nobody is near enough to take it then anyway.
 */
public final class PickupSystem extends EntitySystem {

    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Taking> TAKING = ComponentMapper.getFor(Taking.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Feed> FEED = ComponentMapper.getFor(Feed.class);
    private static final ComponentMapper<Ranged> RANGED = ComponentMapper.getFor(Ranged.class);
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<BlastsOnDeath> REACTION = ComponentMapper.getFor(BlastsOnDeath.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Armoury> ARMOURY = ComponentMapper.getFor(Armoury.class);

    private final Spawner spawner;
    private final Modifiers modifiers;
    private final Defs<PickupKind, PickupDef> pickups;
    private final Rng armouryRng;

    /** Every cache the armoury deck holds, in kind order: the order its cards are counted in. */
    private final PickupKind[] deck;

    /** What each of those holds before any upgrade adds to it, in the same order. */
    private final int[] deckBase;

    /** Scratch: what each holds on this reveal, folded. Overwritten before every read. */
    private final int[] deckNow;

    private ImmutableArray<Entity> takers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> lying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    public PickupSystem(SimConfig config, Spawner spawner, Modifiers modifiers, Rng armouryRng) {
        this.spawner = spawner;
        this.modifiers = modifiers;
        this.pickups = config.pickups();
        this.armouryRng = armouryRng;
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
        takers = engine.getEntitiesFor(Family.all(Taking.class, Faction.class).get());
        lying = engine.getEntitiesFor(Family.all(Pickup.class).get());
        levels = engine.getEntitiesFor(Family.all(Level.class, Armoury.class).get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < takers.size(); i++) {
            Entity taker = takers.get(i);
            Taking taking = TAKING.get(taker);
            PickupKind kind = taking.claimed;
            if (kind == null) {
                continue;
            }
            taking.claimed = null;
            apply(taker, pickups.of(kind), FACTION.get(taker).side);
        }
        for (int i = 0; i < lying.size(); i++) {
            Entity pickup = lying.get(i);
            if (!PICKUP.get(pickup).claimed) {
                continue;
            }
            if (REACTION.has(pickup)) {
                // Swallowed whole, no bang; bitten, the smaller blast. Same reaction (G0018).
                if (PICKUP.get(pickup).swallowed) {
                    REACTION.get(pickup).swallowed();
                } else {
                    REACTION.get(pickup).bitten();
                }
            }
            spawner.markDead(pickup);
        }
        reveal();
    }

    /** Every sealed cache a commander on his feet has come within reach of seeing, dealt its gun (G0050). */
    private void reveal() {
        if (levels.size() == 0) {
            return;
        }
        for (int i = 0; i < lying.size(); i++) {
            Entity cache = lying.get(i);
            ArmouryDef armoury = pickups.of(PICKUP.get(cache).kind).armoury();
            if (armoury.sealed()
                    && POSITION.has(cache)
                    && commanderWithin(POSITION.get(cache), armoury.revealRadius())) {
                spawner.applyPickup(cache, deal(ARMOURY.get(levels.first())));
            }
        }
    }

    /** Whether any commander on his feet stands within this distance of a point. */
    private boolean commanderWithin(Position at, float radius) {
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (Players.down(commander)) {
                continue;
            }
            Position him = POSITION.get(commander);
            float dx = him.x - at.x;
            float dy = him.y - at.y;
            if (dx * dx + dy * dy <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * The next card from the armoury deck, from a bag: no cache comes up twice before every card has.
     * The deck's counts are folded afresh (ADR 0017); a deck that has grown since its last shuffle -
     * an upgrade has stacked it - is shuffled again before it deals.
     */
    private PickupKind deal(Armoury armoury) {
        int size = 0;
        for (int d = 0; d < deck.length; d++) {
            deckNow[d] = modifiers.armouryCards(deck[d], deckBase[d]);
            size += deckNow[d];
        }
        if (armoury.shuffledAt != size) {
            armoury.drawn = 0L;
            armoury.shuffledAt = size;
        }
        int card = Bag.draw(armouryRng, armoury.drawn, size);
        armoury.drawn = Bag.take(armoury.drawn, card, size);
        for (int d = 0; d < deck.length; d++) {
            if (card < deckNow[d]) {
                return deck[d];
            }
            card -= deckNow[d];
        }
        throw new IllegalStateException("card " + card + " is past the end of a deck of " + size);
    }

    /** What one side gets out of one pickup. Nothing here allocates - a grant is a value, not a call. */
    private void apply(Entity taker, PickupDef def, Side side) {
        switch (def.grantFor(side)) {
            case Grant.None _ -> {
                // Unreachable: a side with nothing on offer never claims one.
            }
            case Grant.Detonate _ -> charge(taker);
            case Grant.Arm(WeaponKind gun) -> spawner.applyWeapon(taker, gun);
            case Grant.Feed(int amount) -> feed(taker, amount);
            case Grant.Mutate(UnitKind into) -> mutate(taker, into);
            case Grant.Reinforce(int troopers) -> reinforce(troopers);
            case Grant.Mend _ -> mend(taker);
        }
    }

    /**
     * What is left of a barrel once it has gone off in something's face.
     *
     * <p>Nothing, unless the strain names a {@code chargedGun} (G0022); the blast is the barrel's own
     * death (G0018).
     */
    private void charge(Entity taker) {
        if (!RANGED.has(taker)) {
            return;
        }
        Ranged ranged = RANGED.get(taker);
        if (!ranged.enabled || ranged.chargedGun == null) {
            return;
        }
        spawner.applyWeapon(taker, ranged.chargedGun);
    }

    /** Every wound cleared, through the one method that heals a man (G0053). */
    private static void mend(Entity taker) {
        if (VITALITY.has(taker)) {
            VITALITY.get(taker).recover();
        }
    }

    /** Counts towards whatever the taker's strain evolves into. {@code EvolutionSystem} decides when. */
    private static void feed(Entity taker, int amount) {
        if (FEED.has(taker)) {
            FEED.get(taker).add(amount);
        }
    }

    /** Names the strain the taker is about to become. {@code EvolutionSystem} is what performs it. */
    private static void mutate(Entity taker, UnitKind into) {
        if (FEED.has(taker)) {
            FEED.get(taker).mutateInto(into);
        }
    }

    /**
     * A grant that belongs to the level rather than to whoever picked it up.
     *
     * <p>The level is an ordinary entity, so no message queue (G0004). What a crate is worth is read
     * here, where it is spent.
     */
    private void reinforce(int troopers) {
        int men = modifiers.menPerCrate(troopers);
        for (int i = 0; i < levels.size(); i++) {
            LEVEL.get(levels.get(i)).pendingReinforcements += men;
        }
    }
}
