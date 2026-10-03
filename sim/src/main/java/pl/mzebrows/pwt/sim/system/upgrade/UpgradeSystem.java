package pl.mzebrows.pwt.sim.system.upgrade;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Drafts;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Presence;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.Ticks;

/**
 * The only writer of the ledger and the drafts (ADR 0017, G0049). Once per tick, at the end of it:
 *
 * <ol>
 *   <li>applies this tick's picks, each from the next tick on;
 *   <li>passes the drafts of away slots on (G0052);
 *   <li>re-deals any card a pick or a pass made stale, in its place;
 *   <li>earns drafts from what the level published this tick - points taken, hold waves cleared,
 *       bosses killed - at their configured rates, and deals their cards;
 *   <li>adapts the swarm, if an adaptation is due by level time or by the players' picks: one card from
 *       the aliens' pool, on a stream of its own, taken at once - there is nobody to choose.
 * </ol>
 *
 * <p>Nothing here waits: a draft is state, a pick is input, and a draft nobody picks is simply still
 * there. Cards are dealt from a stream of their own when a draft is earned, so what is offered does
 * not depend on when anybody looks.
 */
public final class UpgradeSystem extends EntitySystem {

    private static final ComponentMapper<Upgrades> UPGRADES = ComponentMapper.getFor(Upgrades.class);
    private static final ComponentMapper<Drafts> DRAFTS = ComponentMapper.getFor(Drafts.class);
    private static final ComponentMapper<Presence> PRESENCE = ComponentMapper.getFor(Presence.class);
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<Waves> WAVES = ComponentMapper.getFor(Waves.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Respawning> RESPAWNING = ComponentMapper.getFor(Respawning.class);
    private static final ComponentMapper<ControlPoint> CONTROL_POINT = ComponentMapper.getFor(ControlPoint.class);

    private static final UpgradeKind[] KINDS = UpgradeKind.values();

    private final DraftDef draft;
    private final AdaptationDef adaptation;
    private final Defs<UpgradeKind, UpgradeDef> catalogue;
    private final Rng rng;
    private final Rng adapting;

    /** One adaptation per this many ticks of level time; zero is off. */
    private final int everyTicks;

    /** Scratch, overwritten before each read: by slot, whether it has a commander, and whether it is away. */
    private final boolean[] seated;

    private final boolean[] away;

    /** Scratch for a deal: a weight per kind, and the kinds dealt. */
    private final int[] weights = new int[KINDS.length];

    private final int[] dealt = new int[KINDS.length];

    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> returning = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());

    /**
     * @param rng what drafts are dealt from
     * @param adapting what the swarm's adaptations are dealt from
     * @param capacity how many slots the game holds - what the rotation goes round
     */
    public UpgradeSystem(SimConfig config, Rng rng, Rng adapting, int capacity) {
        this.draft = config.draft();
        this.adaptation = config.adaptation();
        this.catalogue = config.upgrades();
        this.rng = rng;
        this.adapting = adapting;
        this.everyTicks = Ticks.of(config.adaptation().everySeconds(), config.tickSeconds());
        this.seated = new boolean[capacity];
        this.away = new boolean[capacity];
    }

    @Override
    public void addedToEngine(Engine engine) {
        levels =
                engine.getEntitiesFor(Family.all(Upgrades.class, Drafts.class, Presence.class, Level.class, Waves.class)
                        .get());
        commanders = engine.getEntitiesFor(Family.all(Commander.class).get());
        returning = engine.getEntitiesFor(Family.all(Respawning.class).get());
        points = engine.getEntitiesFor(Family.all(ControlPoint.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < levels.size(); i++) {
            Entity level = levels.get(i);
            Upgrades upgrades = UPGRADES.get(level);
            Drafts drafts = DRAFTS.get(level);
            seat(PRESENCE.get(level));
            boolean changed = applyPicks(upgrades, drafts);
            changed |= passOn(drafts);
            if (changed) {
                refresh(upgrades, drafts);
            }
            changed |= earn(upgrades, drafts, LEVEL.get(level), WAVES.get(level));
            if (changed) {
                drafts.revision++;
            }
            adapt(upgrades, LEVEL.get(level));
        }
    }

    /** Who has a commander this tick, standing, down or on his way back, and who is away. */
    private void seat(Presence presence) {
        for (int slot = 0; slot < seated.length; slot++) {
            seated[slot] = false;
            away[slot] = presence.isAway(slot);
        }
        for (int i = 0; i < commanders.size(); i++) {
            sit(COMMANDER.get(commanders.get(i)).slot);
        }
        for (int i = 0; i < returning.size(); i++) {
            sit(RESPAWNING.get(returning.get(i)).slot);
        }
    }

    private void sit(int slot) {
        if (slot >= 0 && slot < seated.length) {
            seated[slot] = true;
        }
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
                    || !eligible(pick.kind(), Side.PLAYER, pick.slot(), upgrades, drafts.earned)) {
                continue;
            }
            UpgradeDef def = catalogue.of(pick.kind());
            grant(upgrades, pick.kind(), def.personal() ? pick.slot() : Upgrades.SHARED);
            rememberPassedOver(drafts, pick);
            removeDraft(drafts, index);
            upgrades.revision++;
            upgrades.playerPicks++;
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
        UpgradeDef def = config.upgrades().of(kind);
        int held = Math.min(ranks, def.ranks());
        ImmutableArray<Entity> levels =
                engine.getEntitiesFor(Family.all(Upgrades.class).get());
        for (int i = 0; held > 0 && i < levels.size(); i++) {
            Upgrades upgrades = UPGRADES.get(levels.get(i));
            upgrades.held.add(new Upgrades.Held(kind, def.personal() ? 0 : Upgrades.SHARED, held));
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

    /** While a slot is away, each of its drafts moves to the next present slot, its cards unchanged. */
    private boolean passOn(Drafts drafts) {
        boolean moved = false;
        for (int i = 0; i < drafts.pending.size; i++) {
            Drafts.Draft pending = drafts.pending.get(i);
            if (!away[pending.chooser()]) {
                continue;
            }
            int next = Rotation.next(pending.chooser(), seated, away);
            if (next == pending.chooser() || away[next]) {
                continue;
            }
            drafts.pending.set(
                    i, new Drafts.Draft(pending.serial(), next, pending.earnedTick(), pending.revision() + 1));
            moved = true;
        }
        return moved;
    }

    /**
     * Re-deals, in its place, every card that can no longer be taken by its draft's chooser - its last
     * rank went, or its group was taken. A card nothing can replace is dropped, and a draft left with
     * none goes too. The draft's revision moves, so whoever shows it can tell.
     */
    private void refresh(Upgrades upgrades, Drafts drafts) {
        for (int d = drafts.pending.size - 1; d >= 0; d--) {
            Drafts.Draft pending = drafts.pending.get(d);
            boolean redealt = false;
            for (int c = drafts.cards.size - 1; c >= 0; c--) {
                Drafts.Card card = drafts.cards.get(c);
                if (card.serial() != pending.serial()
                        || eligible(card.kind(), Side.PLAYER, pending.chooser(), upgrades, drafts.earned)) {
                    continue;
                }
                redealt = true;
                int count = deal(pending.chooser(), upgrades, drafts, 1, pending.serial());
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
     * Earns every draft the level's published facts are owed, points first, then waves, then bosses:
     * the first of each source at once, then one every so many.
     */
    private boolean earn(Upgrades upgrades, Drafts drafts, Level level, Waves waves) {
        boolean earned = false;
        int taken = 0;
        for (int i = 0; i < points.size(); i++) {
            if (CONTROL_POINT.get(points.get(i)).captured) {
                taken++;
            }
        }
        while (drafts.fromPoints < due(taken, draft.pointsPerDraft())) {
            drafts.fromPoints++;
            earned |= earnOne(upgrades, drafts, level);
        }
        while (drafts.fromWaves < due(Long.bitCount(waves.cleared), draft.wavesPerDraft())) {
            drafts.fromWaves++;
            earned |= earnOne(upgrades, drafts, level);
        }
        while (drafts.fromBosses < due(level.bossesDown, draft.bossesPerDraft())) {
            drafts.fromBosses++;
            earned |= earnOne(upgrades, drafts, level);
        }
        return earned;
    }

    /** How many drafts {@code count} of a source is worth at one per {@code per}; zero is off. */
    private static int due(int count, int per) {
        return per <= 0 ? 0 : (count + per - 1) / per;
    }

    /** Deals one draft to whoever's turn it is. An empty pool earns nothing: there is nothing left to learn. */
    private boolean earnOne(Upgrades upgrades, Drafts drafts, Level level) {
        int chooser = Rotation.next(drafts.lastChooser, seated, away);
        int serial = drafts.nextSerial;
        int count = deal(chooser, upgrades, drafts, draft.options(), serial);
        if (count == 0) {
            drafts.dry++;
            return false;
        }
        drafts.nextSerial++;
        drafts.pending.add(new Drafts.Draft(serial, chooser, level.tick, 0));
        for (int position = 0; position < count; position++) {
            drafts.cards.add(new Drafts.Card(serial, position, KINDS[dealt[position]]));
        }
        drafts.lastChooser = chooser;
        drafts.earned++;
        return true;
    }

    /**
     * Deals up to {@code options} kinds this chooser could take into {@link #dealt}, leaving out any
     * already on draft {@code serial} and, while others remain, what he passed over last time.
     */
    @SuppressWarnings("EnumOrdinal")
    private int deal(int chooser, Upgrades upgrades, Drafts drafts, int options, int serial) {
        for (int k = 0; k < KINDS.length; k++) {
            UpgradeKind kind = KINDS[k];
            weights[k] = !offers(drafts, serial, kind) && eligible(kind, Side.PLAYER, chooser, upgrades, drafts.earned)
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
     * Adapts the swarm once for every adaptation it is owed and has not been credited with: by level
     * time, counting this tick, and by the players' picks, counting this tick's. Each is one card dealt
     * from the aliens' pool and taken at once; an empty pool takes nothing, and draws nothing.
     */
    private void adapt(Upgrades upgrades, Level level) {
        int byTime = everyTicks <= 0 ? 0 : (int) ((level.tick + 1) / everyTicks);
        while (upgrades.fromTime < byTime) {
            upgrades.fromTime++;
            adaptOnce(upgrades);
        }
        int per = adaptation.perPlayerUpgrades();
        int byPicks = per <= 0 ? 0 : upgrades.playerPicks / per;
        while (upgrades.fromPicks < byPicks) {
            upgrades.fromPicks++;
            adaptOnce(upgrades);
        }
    }

    /**
     * Deals the swarm one adaptation, weighted, and takes it. An adaptation that answers an upgrade the
     * players hold is weighted up by {@code answerWeight} per rank they hold of it.
     */
    private void adaptOnce(Upgrades upgrades) {
        for (int k = 0; k < KINDS.length; k++) {
            UpgradeKind kind = KINDS[k];
            if (!eligible(kind, Side.ALIEN, Upgrades.SHARED, upgrades, upgrades.adaptations)) {
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
     * Whether this chooser could take this kind now: that side's, below its last rank, its tier
     * unlocked, what it requires held, nothing else in its group held, and not switched off. The swarm
     * is a chooser holding only what the side holds, and counts its tiers in adaptations.
     */
    private boolean eligible(UpgradeKind kind, Side side, int chooser, Upgrades upgrades, int earned) {
        UpgradeDef def = catalogue.of(kind);
        if (def.side() != side || def.weight() <= 0 || earned < draft.unlockedAt(def.tier())) {
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
