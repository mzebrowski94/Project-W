package pl.mzebrows.pwt.sim.system.upgrade;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.Seats;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Drafts;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.Site;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * The only writer of the ledger and the drafts (ADR 0017, G0049, G0118). Once per tick, at the end of it:
 *
 * <ol>
 *   <li>on the level's first tick, plans the schedule: every site standing and every ridge of the map, weighed,
 *       how many drafts that plans each slot, and how many adaptations it plans the swarm, its spread drawn;
 *   <li>applies this tick's picks, each from the next tick on;
 *   <li>re-deals any card a pick made stale, in its place;
 *   <li>earns every seated slot - away or not - each draft his own count has reached: the sites the side has
 *       taken and the ridges his own commander has crossed, caught up by where he stands; and deals its cards;
 *   <li>adapts the swarm once for every threshold the side's count has reached: the sites taken and the furthest
 *       biome any commander has entered, each worth more for every player seated when it is passed. One card from the
 *       aliens' pool, on a stream of its own, taken at once - there is nobody to choose.
 * </ol>
 *
 * <p>Nothing here waits, and nothing passes on: a draft is state, a pick is input, and a draft nobody picks is
 * simply still there, its chooser's. Cards are dealt from a stream of their own when a draft is earned, so what is
 * offered does not depend on when anybody looks.
 */
public final class UpgradeSystem extends EntitySystem {

    private static final ComponentMapper<Upgrades> UPGRADES = ComponentMapper.getFor(Upgrades.class);
    private static final ComponentMapper<Drafts> DRAFTS = ComponentMapper.getFor(Drafts.class);
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<Site> SITE = ComponentMapper.getFor(Site.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    private static final UpgradeKind[] KINDS = UpgradeKind.values();

    private final DraftDef draft;
    private final AdaptationDef adaptation;
    private final Defs<UpgradeKind, UpgradeDef> catalogue;
    private final Rng rng;
    private final Rng adapting;

    /** The ground, whose ridges a crossing is counted by. */
    private final TileMap map;

    /** Every rank the swarm could take, of every adaptation dealt at all: what its share of the pool is of. */
    private final int pool;

    /**
     * Scratch, overwritten before each read: by slot, whether it has a commander, and how many ridges his commander
     * stands past this tick.
     */
    private final boolean[] seated;

    private final int[] reached;

    /** Scratch for a deal: a weight per kind, and the kinds dealt. */
    private final int[] weights = new int[KINDS.length];

    private final int[] dealt = new int[KINDS.length];

    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> returning = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> sites = new ImmutableArray<>(new Array<>());

    /**
     * @param rng what drafts are dealt from
     * @param adapting what the swarm's adaptations are dealt from
     * @param map the ground, whose ridges a crossing is counted by
     * @param capacity how many slots the game holds - each is dealt his own
     */
    public UpgradeSystem(SimConfig config, Rng rng, Rng adapting, TileMap map, int capacity) {
        this.draft = config.draft();
        this.adaptation = config.adaptation();
        this.catalogue = config.upgrades();
        this.rng = rng;
        this.adapting = adapting;
        this.map = map;
        this.pool = pool(config.upgrades());
        this.seated = new boolean[capacity];
        this.reached = new int[capacity];
    }

    @Override
    public void addedToEngine(Engine engine) {
        levels = engine.getEntitiesFor(
                Family.all(Upgrades.class, Drafts.class, Level.class).get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
        returning = engine.getEntitiesFor(Family.all(Respawning.class).get());
        sites = engine.getEntitiesFor(Family.all(Site.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < levels.size(); i++) {
            Entity level = levels.get(i);
            Upgrades upgrades = UPGRADES.get(level);
            Drafts drafts = DRAFTS.get(level);
            if (!drafts.scheduled) {
                schedule(drafts, upgrades);
            }
            boolean changed = applyPicks(upgrades, drafts);
            if (changed) {
                refresh(upgrades, drafts);
            }
            int taken = taken();
            changed |= earn(upgrades, drafts, LEVEL.get(level), taken);
            if (changed) {
                drafts.revision++;
            }
            adapt(upgrades, drafts, taken);
        }
    }

    /**
     * Fixes the schedule (G0118), once, on the level's first tick - by then everything the level was built with
     * stands on it: every site, taken or not, and every ridge, at their weights. The swarm's plan is moved by its
     * spread, drawn from its own stream - and nothing is drawn for a plan of none, or no spread.
     */
    private void schedule(Drafts drafts, Upgrades upgrades) {
        drafts.sources = draft.sources(sites.size(), map.ridgeCount());
        drafts.planned = draft.planned(drafts.sources);
        int offset = 0;
        int spread = adaptation.spread();
        if (spread > 0 && adaptation.planned(drafts.sources, draft.sourcesRef(), pool, 0) > 0) {
            offset = adapting.nextInt(2 * spread + 1) - spread;
        }
        upgrades.adaptationsPlanned = adaptation.planned(drafts.sources, draft.sourcesRef(), pool, offset);
        drafts.scheduled = true;
    }

    /** Every rank of every adaptation that is dealt at all. */
    private static int pool(Defs<UpgradeKind, UpgradeDef> catalogue) {
        int ranks = 0;
        for (UpgradeKind kind : KINDS) {
            UpgradeDef def = catalogue.of(kind);
            if (def.side() == Side.ALIEN && def.weight() > 0) {
                ranks += def.ranks();
            }
        }
        return ranks;
    }

    /** How many sites the side has taken, whatever their kind (G0110). */
    private int taken() {
        int taken = 0;
        for (int i = 0; i < sites.size(); i++) {
            if (SITE.get(sites.get(i)).taken) {
                taken++;
            }
        }
        return taken;
    }

    /**
     * Applies the picks handed in this tick. A pick counts only if its draft is still there, belongs to
     * the slot that picked, carries that kind, and the kind can still be taken; otherwise nothing
     * happens at all. Returns whether the ledger changed.
     */
    private boolean applyPicks(Upgrades upgrades, Drafts drafts) {
        boolean changed = false;
        for (int p = 0; p < drafts.submitted.size; p++) {
            Drafts.Pick pick = drafts.submitted.get(p);
            int index = draftIndex(drafts, pick.serial());
            if (index < 0
                    || drafts.pending.get(index).chooser() != pick.slot()
                    || !offers(drafts, pick.serial(), pick.kind())
                    || !eligible(
                            pick.kind(),
                            Side.PLAYER,
                            pick.slot(),
                            upgrades,
                            earnedOf(drafts, pick.slot()),
                            drafts.planned)) {
                continue;
            }
            UpgradeDef def = catalogue.of(pick.kind());
            grant(upgrades, pick.kind(), def.personal() ? pick.slot() : Upgrades.SHARED);
            rememberPassedOver(drafts, pick);
            removeDraft(drafts, index);
            upgrades.revision++;
            changed = true;
        }
        drafts.submitted.clear();
        return changed;
    }

    /**
     * Holds this many ranks of one kind from the first tick, never more than it has - the proving
     * ground's setup (G0060), handed over while its world is built rather than earned. Held exactly as
     * ranks taken would be: a players' upgrade by the side, or by slot zero if it is personal; an
     * adaptation by the swarm, counted towards its tiers. Allocates; never called during a tick.
     */
    public static void hold(Engine engine, SimConfig config, UpgradeKind kind, int ranks) {
        hold(engine, config, kind, ranks, 0);
    }

    /**
     * The same, a personal upgrade held by this slot rather than slot zero - a scenario that hands every seat its kit
     * from the first tick (G0108). A shared one or an adaptation is held as above, whatever the slot.
     */
    public static void hold(Engine engine, SimConfig config, UpgradeKind kind, int ranks, int slot) {
        UpgradeDef def = config.upgrades().of(kind);
        int held = Math.min(ranks, def.ranks());
        ImmutableArray<Entity> levels =
                engine.getEntitiesFor(Family.all(Upgrades.class).get());
        for (int i = 0; held > 0 && i < levels.size(); i++) {
            Upgrades upgrades = UPGRADES.get(levels.get(i));
            upgrades.held.add(new Upgrades.Held(kind, def.personal() ? slot : Upgrades.SHARED, held));
            if (def.side() == Side.ALIEN) {
                upgrades.adaptations += held;
            }
            upgrades.revision++;
        }
    }

    /** Adds one rank, to the side's entry or to the chooser's. Allocates, once per pick. */
    private static void grant(Upgrades upgrades, UpgradeKind kind, int slot) {
        for (int i = 0; i < upgrades.held.size; i++) {
            Upgrades.Held held = upgrades.held.get(i);
            if (held.kind() == kind && held.slot() == slot) {
                upgrades.held.set(i, new Upgrades.Held(kind, slot, held.ranks() + 1));
                return;
            }
        }
        upgrades.held.add(new Upgrades.Held(kind, slot, 1));
    }

    /** What the picker passed over this time replaces what he passed over last time. */
    private static void rememberPassedOver(Drafts drafts, Drafts.Pick pick) {
        for (int i = drafts.passed.size - 1; i >= 0; i--) {
            if (drafts.passed.get(i).slot() == pick.slot()) {
                drafts.passed.removeIndex(i);
            }
        }
        for (int i = 0; i < drafts.cards.size; i++) {
            Drafts.Card card = drafts.cards.get(i);
            if (card.serial() == pick.serial() && card.kind() != pick.kind()) {
                drafts.passed.add(new Drafts.Passed(pick.slot(), card.kind()));
            }
        }
    }

    /**
     * Re-deals, in its place, every card that can no longer be taken by its draft's chooser - its last
     * rank went, or its group was taken. A card nothing can replace is dropped, and a draft left with
     * none goes too. The draft's revision moves, so whoever shows it can tell.
     */
    private void refresh(Upgrades upgrades, Drafts drafts) {
        for (int d = drafts.pending.size - 1; d >= 0; d--) {
            Drafts.Draft pending = drafts.pending.get(d);
            int earned = earnedOf(drafts, pending.chooser());
            boolean redealt = false;
            for (int c = drafts.cards.size - 1; c >= 0; c--) {
                Drafts.Card card = drafts.cards.get(c);
                if (card.serial() != pending.serial()
                        || eligible(card.kind(), Side.PLAYER, pending.chooser(), upgrades, earned, drafts.planned)) {
                    continue;
                }
                redealt = true;
                int count = deal(pending.chooser(), upgrades, drafts, 1, pending.serial(), earned);
                if (count == 0) {
                    drafts.cards.removeIndex(c);
                } else {
                    drafts.cards.set(c, new Drafts.Card(card.serial(), card.position(), KINDS[dealt[0]]));
                }
            }
            if (!redealt) {
                continue;
            }
            if (cardsOf(drafts, pending.serial()) == 0) {
                drafts.pending.removeIndex(d);
            } else {
                drafts.pending.set(
                        d,
                        new Drafts.Draft(
                                pending.serial(), pending.chooser(), pending.earnedTick(), pending.revision() + 1));
            }
        }
    }

    /**
     * Earns every seated slot - away or not, seated late or not - each threshold his count has reached and he has
     * not been credited with (G0118): the sites the side has taken, and the ridges his own commander has crossed,
     * the furthest east he has ever stood. Slots in order, so every machine deals the same cards to the same slot.
     */
    private boolean earn(Upgrades upgrades, Drafts drafts, Level level, int taken) {
        Seats.mark(seated, commanders, returning);
        for (int slot = 0; slot < reached.length; slot++) {
            reached[slot] = 0;
        }
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            int slot = COMMANDER.get(commander).slot;
            if (slot >= 0 && slot < reached.length) {
                int past = map.ridgesReached(map.colAt(POSITION.get(commander).x));
                reached[slot] = Math.max(reached[slot], past);
            }
        }
        boolean earned = false;
        for (int slot = 0; slot < seated.length; slot++) {
            if (seated[slot]) {
                earned |= earnFor(slot, taken, upgrades, drafts, level);
            }
        }
        return earned;
    }

    /**
     * One seated slot's crossings caught up and every threshold he is owed credited. Allocates only when either
     * moves: once per crossing, per draft, and the first time the slot is seated.
     */
    private boolean earnFor(int slot, int taken, Upgrades upgrades, Drafts drafts, Level level) {
        int index = progressIndex(drafts, slot);
        if (index < 0) {
            drafts.progress.add(new Drafts.Progress(slot, 0, 0));
            index = drafts.progress.size - 1;
        }
        Drafts.Progress progress = drafts.progress.get(index);
        int crossings = Math.max(progress.crossings(), reached[slot]);
        int passed = draft.sources(taken, crossings);
        int owed = DraftSchedule.owed(passed, drafts.sources, drafts.planned, draft.firstAtOnce());
        int credited = progress.earned();
        boolean earned = false;
        while (credited < owed) {
            earned |= earnOne(upgrades, drafts, level, slot, credited);
            credited++;
        }
        if (crossings != progress.crossings() || credited != progress.earned()) {
            drafts.progress.set(index, new Drafts.Progress(slot, crossings, credited));
        }
        return earned;
    }

    /**
     * Deals one draft to this slot, {@code earned} thresholds into his schedule. An empty pool earns nothing: there
     * is nothing left to learn.
     */
    private boolean earnOne(Upgrades upgrades, Drafts drafts, Level level, int chooser, int earned) {
        int serial = drafts.nextSerial;
        int count = deal(chooser, upgrades, drafts, draft.options(), serial, earned);
        if (count == 0) {
            drafts.dry++;
            return false;
        }
        drafts.nextSerial++;
        drafts.pending.add(new Drafts.Draft(serial, chooser, level.tick, 0));
        for (int position = 0; position < count; position++) {
            drafts.cards.add(new Drafts.Card(serial, position, KINDS[dealt[position]]));
        }
        return true;
    }

    /** How many of the schedule's thresholds this slot has been credited with, which is what opens a tier for him. */
    private static int earnedOf(Drafts drafts, int slot) {
        int index = progressIndex(drafts, slot);
        return index < 0 ? 0 : drafts.progress.get(index).earned();
    }

    private static int progressIndex(Drafts drafts, int slot) {
        for (int i = 0; i < drafts.progress.size; i++) {
            if (drafts.progress.get(i).slot() == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Deals up to {@code options} kinds this chooser could take into {@link #dealt}, leaving out any
     * already on draft {@code serial} and, while others remain, what he passed over last time.
     */
    @SuppressWarnings("EnumOrdinal")
    private int deal(int chooser, Upgrades upgrades, Drafts drafts, int options, int serial, int earned) {
        for (int k = 0; k < KINDS.length; k++) {
            UpgradeKind kind = KINDS[k];
            weights[k] = !offers(drafts, serial, kind)
                            && eligible(kind, Side.PLAYER, chooser, upgrades, earned, drafts.planned)
                    ? catalogue.of(kind).weight()
                    : 0;
        }
        long avoid = 0L;
        for (int i = 0; i < drafts.passed.size; i++) {
            Drafts.Passed passed = drafts.passed.get(i);
            if (passed.slot() == chooser) {
                avoid |= 1L << passed.kind().ordinal();
            }
        }
        return Dealer.deal(rng, weights, avoid, options, dealt);
    }

    /**
     * Adapts the swarm once for every threshold of its plan the side's count has reached and it has not been credited
     * with (G0118). The count gains what this tick passed - sites newly taken, and biomes newly entered by the
     * furthest commander - each worth more for every player seated now. The first waits its gap, so the swarm adapts
     * as the side comes into a biome rather than as it sets out. Each is one card dealt from the aliens' pool and
     * taken at once, up to the most the players seated allow; an empty pool takes nothing, and draws nothing.
     */
    private void adapt(Upgrades upgrades, Drafts drafts, int taken) {
        int seatedNow = Seats.count(seated, commanders, returning);
        int entered = Math.max(upgrades.biomesEntered, entered());
        int sitesNew = Math.max(0, taken - upgrades.sitesCounted);
        int sourcesNew = draft.sources(sitesNew, entered - upgrades.biomesEntered);
        upgrades.passedHundredths += sourcesNew * adaptation.worth(seatedNow);
        upgrades.sitesCounted += sitesNew;
        upgrades.biomesEntered = entered;
        int owed = AdaptationSchedule.owed(
                upgrades.passedHundredths,
                drafts.sources,
                upgrades.adaptationsPlanned,
                adaptation.most(pool, seatedNow));
        while (upgrades.credited < owed) {
            upgrades.credited++;
            adaptOnce(upgrades);
        }
    }

    /**
     * The furthest biome any commander stands in, as the ridges behind it - never more than the map has, so a map
     * whose biomes no ridge divides counts none.
     */
    private int entered() {
        int furthest = 0;
        for (int i = 0; i < commanders.size(); i++) {
            Position at = POSITION.get(commanders.get(i));
            furthest = Math.max(furthest, map.biomeAt(at.x, at.y));
        }
        return Math.min(furthest, map.ridgeCount());
    }

    /**
     * Deals the swarm one adaptation, weighted, and takes it. An adaptation that answers an upgrade the
     * players hold is weighted up by {@code answerWeight} per rank they hold of it. Its tiers open on its count of
     * adaptations against its own plan, as a chooser's open against his.
     */
    private void adaptOnce(Upgrades upgrades) {
        for (int k = 0; k < KINDS.length; k++) {
            UpgradeKind kind = KINDS[k];
            if (!eligible(
                    kind, Side.ALIEN, Upgrades.SHARED, upgrades, upgrades.adaptations, upgrades.adaptationsPlanned)) {
                weights[k] = 0;
                continue;
            }
            UpgradeDef def = catalogue.of(kind);
            UpgradeKind answered = def.answers();
            int held = answered == null ? 0 : playersRanks(upgrades, answered);
            weights[k] = def.weight() * (1 + adaptation.answerWeight() * held);
        }
        if (Dealer.deal(adapting, weights, 0L, 1, dealt) == 0) {
            return;
        }
        grant(upgrades, KINDS[dealt[0]], Upgrades.SHARED);
        upgrades.adaptations++;
        upgrades.revision++;
    }

    /** Every rank the players hold of this upgrade, the side's and each player's own. */
    private static int playersRanks(Upgrades upgrades, UpgradeKind kind) {
        int sum = 0;
        for (int i = 0; i < upgrades.held.size; i++) {
            Upgrades.Held held = upgrades.held.get(i);
            if (held.kind() == kind) {
                sum += held.ranks();
            }
        }
        return sum;
    }

    /**
     * Whether this chooser could take this kind now: that side's, below its last rank, its tier open at
     * {@code earned} of {@code planned} drafts, what it requires held, nothing else in its group held, and not
     * switched off. The swarm is a chooser holding only what the side holds, and counts its tiers in adaptations
     * against its own plan.
     */
    private boolean eligible(UpgradeKind kind, Side side, int chooser, Upgrades upgrades, int earned, int planned) {
        UpgradeDef def = catalogue.of(kind);
        if (def.side() != side || def.weight() <= 0 || !draft.opens(def.tier(), earned, planned)) {
            return false;
        }
        if (ranks(upgrades, kind, def.personal() ? chooser : Upgrades.SHARED) >= def.ranks()) {
            return false;
        }
        UpgradeKind required = def.requires();
        if (required != null && !holds(upgrades, required, chooser)) {
            return false;
        }
        if (def.group() > 0) {
            for (UpgradeKind other : KINDS) {
                if (other != kind && catalogue.of(other).group() == def.group() && holds(upgrades, other, chooser)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int ranks(Upgrades upgrades, UpgradeKind kind, int slot) {
        for (int i = 0; i < upgrades.held.size; i++) {
            Upgrades.Held held = upgrades.held.get(i);
            if (held.kind() == kind && held.slot() == slot) {
                return held.ranks();
            }
        }
        return 0;
    }

    /** Whether the side holds it, or this chooser does. */
    private static boolean holds(Upgrades upgrades, UpgradeKind kind, int chooser) {
        for (int i = 0; i < upgrades.held.size; i++) {
            Upgrades.Held held = upgrades.held.get(i);
            if (held.kind() == kind && (held.slot() == Upgrades.SHARED || held.slot() == chooser)) {
                return true;
            }
        }
        return false;
    }

    private static int draftIndex(Drafts drafts, int serial) {
        for (int i = 0; i < drafts.pending.size; i++) {
            if (drafts.pending.get(i).serial() == serial) {
                return i;
            }
        }
        return -1;
    }

    private static boolean offers(Drafts drafts, int serial, UpgradeKind kind) {
        for (int i = 0; i < drafts.cards.size; i++) {
            Drafts.Card card = drafts.cards.get(i);
            if (card.serial() == serial && card.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    private static int cardsOf(Drafts drafts, int serial) {
        int count = 0;
        for (int i = 0; i < drafts.cards.size; i++) {
            if (drafts.cards.get(i).serial() == serial) {
                count++;
            }
        }
        return count;
    }

    private static void removeDraft(Drafts drafts, int index) {
        int serial = drafts.pending.get(index).serial();
        drafts.pending.removeIndex(index);
        for (int i = drafts.cards.size - 1; i >= 0; i--) {
            if (drafts.cards.get(i).serial() == serial) {
                drafts.cards.removeIndex(i);
            }
        }
    }
}
