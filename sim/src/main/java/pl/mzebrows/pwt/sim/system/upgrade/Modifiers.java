package pl.mzebrows.pwt.sim.system.upgrade;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The one read port for every number an upgrade may move (ADR 0017). The reader hands over the base it
 * already holds - a definition, or the copy a component took at spawn - and gets back the number to
 * use, folded from the ledger as it stands at the read. Nothing is written back, so a unit spawned after an
 * upgrade is identical to one that was standing when it landed.
 *
 * <p>One method per {@link Stat}, marked {@link Reads}, and each has exactly one caller: that caller is
 * the one place the number is read. {@code who} is whoever is reading - the shooter, the thrower, the
 * unit rolling the dice - which is what decides which held effects reach the number ({@link Scope}).
 *
 * <p><strong>A sum is folded once per ledger and kept</strong>, per stat, per kind of reader ({@link
 * Scope#kindOf}) and per gun or card: every reader of one kind is reached by the same held effects, so the
 * second asks an array. A commander's is folded every time - his slot is his own kind, and there are few. All of it is dropped the moment a ledger's {@code revision} or size moves - every
 * write the upgrade package makes bumps the one, and a test's straight into the ledger the other - so a read
 * answers exactly what a fresh fold would. A cache of the ledger, not state: no snapshot carries it, and a
 * world restored from one starts with it empty. Allocates nothing past the constructor. Built once per world
 * by {@code Simulation} and handed to the systems that read, as {@code Spawner} is.
 */
public final class Modifiers {

    private static final ComponentMapper<Upgrades> UPGRADES = ComponentMapper.getFor(Upgrades.class);

    private final ImmutableArray<Entity> ledgers;
    private final Defs<UpgradeKind, UpgradeDef> catalogue;
    private final Defs<Stat, StatDef> stats;

    /** The narrowings a stat's sum is kept for: none, then every gun, then every card. */
    private static final int NARROWINGS = 1 + Math.max(WeaponKind.values().length, PickupKind.values().length);

    @NotState("cache of the ledger's sums, keyed by its revision and size: a pure function of what is held")
    private final int[] sums = new int[Stat.values().length * Scope.KINDS * NARROWINGS];

    @NotState("cache of the ledger's chances per second, keyed as the sums")
    private final float[] chances = new float[AiChance.values().length * Scope.KINDS];

    /** Which generation each sum was folded in; one from an earlier generation is folded again. */
    @NotState("cache: the generation each entry of sums was folded in")
    private final int[] summedIn = new int[sums.length];

    @NotState("cache: the generation each entry of chances was folded in")
    private final int[] chancedIn = new int[chances.length];

    /** The generation now: moved on each time the ledger is seen to change, which drops every entry at once. */
    @NotState("cache: the generation of the entries kept")
    private int generation = 1;

    @NotState("cache: the ledger's revision and size the generation was folded from")
    private long folded = Long.MIN_VALUE;

    public Modifiers(Engine engine, SimConfig config) {
        this.ledgers = engine.getEntitiesFor(Family.all(Upgrades.class).get());
        this.catalogue = config.upgrades();
        this.stats = config.stats();
    }

    /** A gun's cooldown in ticks, for every shooter: the commander, a trooper, an alien, a turret. */
    @Reads(Stat.FIRE_COOLDOWN)
    public int fireCooldownTicks(Entity who, WeaponKind gun, int baseTicks) {
        StatDef stat = stats.of(Stat.FIRE_COOLDOWN);
        return Fold.scaleTicks(
                baseTicks, sum(Stat.FIRE_COOLDOWN, who, gun, null), stat.minPercent(), stat.maxPercent());
    }

    /** What each shot of one pull of this gun does. */
    @Reads(Stat.GUN_DAMAGE)
    public int gunDamage(Entity who, WeaponKind gun, int base) {
        StatDef stat = stats.of(Stat.GUN_DAMAGE);
        return Fold.scaleWhole(base, sum(Stat.GUN_DAMAGE, who, gun, null), stat.minPercent(), stat.maxPercent());
    }

    /** How many men a crate of reinforcements lands. The side's number: no one entity reads it. */
    @Reads(Stat.MEN_PER_CRATE)
    public int menPerCrate(int base) {
        return Fold.count(
                base,
                sum(Stat.MEN_PER_CRATE, null, null, null),
                stats.of(Stat.MEN_PER_CRATE).min());
    }

    /** How many men past its starting size the squad may hold. The side's number. */
    @Reads(Stat.SQUAD_HEADROOM)
    public int squadHeadroom(int base) {
        return Fold.count(
                base,
                sum(Stat.SQUAD_HEADROOM, null, null, null),
                stats.of(Stat.SQUAD_HEADROOM).min());
    }

    /** A thrower's wait between grenades, in ticks. */
    @Reads(Stat.GRENADE_COOLDOWN)
    public int grenadeCooldownTicks(Entity who, int baseTicks) {
        StatDef stat = stats.of(Stat.GRENADE_COOLDOWN);
        return Fold.scaleTicks(
                baseTicks, sum(Stat.GRENADE_COOLDOWN, who, null, null), stat.minPercent(), stat.maxPercent());
    }

    /** The share of its pace a driven unit goes at: the pace {@code who} was going to be driven at, folded. */
    @Reads(Stat.SPEED)
    public float pace(Entity who, float base) {
        StatDef stat = stats.of(Stat.SPEED);
        return Fold.scale(base, sum(Stat.SPEED, who, null, null), stat.minPercent(), stat.maxPercent());
    }

    /**
     * How much of a casualty's stabilise one tick of {@code who}'s work does, in the hundredths of a tick
     * the stabilise is counted in: whole, so the work adds up exactly.
     */
    @Reads(Stat.STABILISE_SPEED)
    public int stabiliseWork(Entity who, int base) {
        StatDef stat = stats.of(Stat.STABILISE_SPEED);
        return Fold.scaleWhole(base, sum(Stat.STABILISE_SPEED, who, null, null), stat.minPercent(), stat.maxPercent());
    }

    /** How long a stun a source declared leaves {@code who} unable to act, in ticks (G0053). */
    @Reads(Stat.STUN_DURATION)
    public int stunTicks(Entity who, int baseTicks) {
        StatDef stat = stats.of(Stat.STUN_DURATION);
        return Fold.scaleTicks(
                baseTicks, sum(Stat.STUN_DURATION, who, null, null), stat.minPercent(), stat.maxPercent());
    }

    /** How many wounds {@code who} carries on his feet (G0053). */
    @Reads(Stat.MAX_WOUNDS)
    public int maxWounds(Entity who, int base) {
        return Fold.count(
                base,
                sum(Stat.MAX_WOUNDS, who, null, null),
                stats.of(Stat.MAX_WOUNDS).min());
    }

    /** How long after his last wound {@code who}'s wounds clear on their own, in seconds; zero never. */
    @Reads(Stat.MEND_SECONDS)
    public int mendSeconds(Entity who, int base) {
        return Fold.count(
                base,
                sum(Stat.MEND_SECONDS, who, null, null),
                stats.of(Stat.MEND_SECONDS).min());
    }

    /** How many cards of this cache the armoury deck holds (G0056). The side's number. */
    @Reads(Stat.ARMOURY_CARDS)
    public int armouryCards(PickupKind card, int base) {
        return Fold.count(
                base,
                sum(Stat.ARMOURY_CARDS, null, null, card),
                stats.of(Stat.ARMOURY_CARDS).min());
    }

    /** The fewest pickups a crate is dealt (G0056). The side's number. */
    @Reads(Stat.CRATE_LEAST)
    public int crateLeast(int base) {
        return Fold.count(
                base,
                sum(Stat.CRATE_LEAST, null, null, null),
                stats.of(Stat.CRATE_LEAST).min());
    }

    /** How many of this card every crate holds at least (G0056). The side's number. */
    @Reads(Stat.CRATE_STOCK)
    public int crateStock(PickupKind card) {
        return Fold.count(
                0,
                sum(Stat.CRATE_STOCK, null, null, card),
                stats.of(Stat.CRATE_STOCK).min());
    }

    /** How fast {@code who}'s leap flies, read as it leaves the ground and held for that flight. */
    @Reads(Stat.LEAP_SPEED)
    public float leapSpeed(Entity who, float base) {
        StatDef stat = stats.of(Stat.LEAP_SPEED);
        return Fold.scale(base, sum(Stat.LEAP_SPEED, who, null, null), stat.minPercent(), stat.maxPercent());
    }

    /** How close a man must be before {@code who} commits to a leap at him. */
    @Reads(Stat.LEAP_TRIGGER_RANGE)
    public float leapTriggerRange(Entity who, float base) {
        StatDef stat = stats.of(Stat.LEAP_TRIGGER_RANGE);
        return Fold.scale(base, sum(Stat.LEAP_TRIGGER_RANGE, who, null, null), stat.minPercent(), stat.maxPercent());
    }

    /** How long {@code who}'s leap is in the air, in ticks: never shortened below one. */
    @Reads(Stat.LEAP_AIRBORNE_TICKS)
    public int leapAirborneTicks(Entity who, int baseTicks) {
        StatDef stat = stats.of(Stat.LEAP_AIRBORNE_TICKS);
        return Fold.scaleTicks(
                baseTicks, sum(Stat.LEAP_AIRBORNE_TICKS, who, null, null), stat.minPercent(), stat.maxPercent());
    }

    /** How many leaps {@code who} strings together once it commits at a man. */
    @Reads(Stat.LEAP_SEQUENCE_LENGTH)
    public int leapSequenceLength(Entity who, int base) {
        return Fold.count(
                base,
                sum(Stat.LEAP_SEQUENCE_LENGTH, who, null, null),
                stats.of(Stat.LEAP_SEQUENCE_LENGTH).min());
    }

    /** How far from its patch's middle idle {@code who} picks somewhere to amble to. */
    @Reads(Stat.WANDER_RADIUS)
    public float wanderRadius(Entity who, float base) {
        StatDef stat = stats.of(Stat.WANDER_RADIUS);
        return Fold.scale(base, sum(Stat.WANDER_RADIUS, who, null, null), stat.minPercent(), stat.maxPercent());
    }

    /** How close a man must come before unaware {@code who} notices him. */
    @Reads(Stat.DETECTION_RANGE)
    public float detectionRange(Entity who, float base) {
        StatDef stat = stats.of(Stat.DETECTION_RANGE);
        return Fold.scale(base, sum(Stat.DETECTION_RANGE, who, null, null), stat.minPercent(), stat.maxPercent());
    }

    /** How far a man must get before hunting {@code who} gives up on him. */
    @Reads(Stat.LOSE_RANGE)
    public float loseRange(Entity who, float base) {
        StatDef stat = stats.of(Stat.LOSE_RANGE);
        return Fold.scale(base, sum(Stat.LOSE_RANGE, who, null, null), stat.minPercent(), stat.maxPercent());
    }

    /** How many this nest's next brood holds, given the size it drew. */
    @Reads(Stat.NEST_BROOD_SIZE)
    public int broodSize(Entity nest, int base) {
        return Fold.count(
                base,
                sum(Stat.NEST_BROOD_SIZE, nest, null, null),
                stats.of(Stat.NEST_BROOD_SIZE).min());
    }

    /** How many circles of venom {@code who} leaves where it dies, given its strain's own (G0076). */
    @Reads(Stat.DEATH_SLICKS)
    public int deathSlicks(Entity who, int base) {
        return Fold.count(
                base,
                sum(Stat.DEATH_SLICKS, who, null, null),
                stats.of(Stat.DEATH_SLICKS).min());
    }

    /** How often {@code who} does this occasional thing, per second of opportunity. */
    @Reads(Stat.CHANCE)
    @SuppressWarnings("EnumOrdinal") // an index into this world's own cache, never stored or sent
    public float chance(Entity who, AiChance kind, float base) {
        int reader = Scope.kindOf(who);
        if (reader == Scope.COMMANDER_KIND) {
            return Fold.chance(base, foldChance(who, kind));
        }
        refresh();
        int index = kind.ordinal() * Scope.KINDS + reader;
        if (chancedIn[index] != generation) {
            chances[index] = foldChance(who, kind);
            chancedIn[index] = generation;
        }
        return Fold.chance(base, chances[index]);
    }

    /** Every held chance on this behaviour that reaches {@code who}, per second, rank by rank. */
    private float foldChance(Entity who, AiChance kind) {
        float perSecond = 0f;
        for (int l = 0; l < ledgers.size(); l++) {
            Upgrades upgrades = UPGRADES.get(ledgers.get(l));
            for (int h = 0; h < upgrades.held.size; h++) {
                Upgrades.Held entry = upgrades.held.get(h);
                List<Effect> effects = catalogue.of(entry.kind()).effects();
                for (int e = 0; e < effects.size(); e++) {
                    if (effects.get(e) instanceof Effect.Chance chance
                            && chance.kind() == kind
                            && Scope.reaches(chance.target(), who, entry.slot())) {
                        perSecond += chance.perSecond() * entry.ranks();
                    }
                }
            }
        }
        return perSecond;
    }

    /**
     * The sum, rank by rank, of every held effect on this stat that reaches whoever is reading - the side's
     * upgrades and the aliens' adaptations alike, since each names whom it reaches. {@code who} is null for
     * the side's own numbers; {@code gun} narrows a gun's stat to one gun, {@code card} a deck's to one card.
     * Whole numbers, so the sum is exact whatever order it is added in.
     */
    @SuppressWarnings("EnumOrdinal") // an index into this world's own cache, never stored or sent
    private int sum(Stat stat, @Nullable Entity who, @Nullable WeaponKind gun, @Nullable PickupKind card) {
        int reader = Scope.kindOf(who);
        if (reader == Scope.COMMANDER_KIND) {
            return fold(stat, who, gun, card);
        }
        refresh();
        int narrowing = gun != null ? 1 + gun.ordinal() : card != null ? 1 + card.ordinal() : 0;
        int index = (stat.ordinal() * Scope.KINDS + reader) * NARROWINGS + narrowing;
        if (summedIn[index] != generation) {
            sums[index] = fold(stat, who, gun, card);
            summedIn[index] = generation;
        }
        return sums[index];
    }

    /** Drops every entry kept the moment any ledger's revision or size has moved since they were folded. */
    private void refresh() {
        long now = ledgers.size();
        for (int l = 0; l < ledgers.size(); l++) {
            Upgrades upgrades = UPGRADES.get(ledgers.get(l));
            now = now * 31 + (((long) upgrades.revision << 32) | upgrades.held.size);
        }
        if (now != folded) {
            folded = now;
            generation++;
        }
    }

    /** The same sum, folded from the ledger itself. */
    private int fold(Stat stat, @Nullable Entity who, @Nullable WeaponKind gun, @Nullable PickupKind card) {
        int sum = 0;
        for (int l = 0; l < ledgers.size(); l++) {
            Upgrades upgrades = UPGRADES.get(ledgers.get(l));
            for (int h = 0; h < upgrades.held.size; h++) {
                Upgrades.Held entry = upgrades.held.get(h);
                List<Effect> effects = catalogue.of(entry.kind()).effects();
                for (int e = 0; e < effects.size(); e++) {
                    sum += share(effects.get(e), stat, who, gun, card, entry.slot()) * entry.ranks();
                }
            }
        }
        return sum;
    }

    /**
     * What one rank of one effect adds to this stat for this reader: the one place an effect is matched to
     * a number, so a new kind of effect does not compile until it says what it adds. A chance is a rate per
     * second, not a whole number, and has {@link #chance} of its own.
     */
    private static int share(
            Effect effect,
            Stat stat,
            @Nullable Entity who,
            @Nullable WeaponKind gun,
            @Nullable PickupKind card,
            int slot) {
        return switch (effect) {
            case Effect.Scale scale ->
                scale.stat() == stat && who != null && Scope.reaches(scale.target(), who, slot) ? scale.percent() : 0;
            case Effect.Gun only ->
                only.stat() == stat && only.gun() == gun && who != null && Scope.reaches(only.target(), who, slot)
                        ? only.percent()
                        : 0;
            case Effect.Breed breed ->
                breed.stat() == stat && who != null && Scope.reaches(breed.strain(), who) ? breed.percent() : 0;
            case Effect.Count count ->
                count.stat() == stat && card == null && (who == null || Scope.reaches(count.target(), who, slot))
                        ? count.amount()
                        : 0;
            case Effect.Deal deal -> stat == Stat.ARMOURY_CARDS && deal.card() == card ? deal.amount() : 0;
            case Effect.Stock stock -> stat == Stat.CRATE_STOCK && stock.card() == card ? stock.amount() : 0;
            case Effect.Chance _ -> 0;
        };
    }
}
