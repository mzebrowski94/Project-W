package pl.mzebrows.pwt.sim.system.upgrade;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The one read port for every number an upgrade may move (ADR 0017). The reader hands over the base it
 * already holds - a definition, or the copy a component took at spawn - and gets back the number to
 * use, folded afresh from the ledger on every read. Nothing is written back, so a unit spawned after an
 * upgrade is identical to one that was standing when it landed.
 *
 * <p>One method per {@link Stat}, marked {@link Reads}, and each has exactly one caller: that caller is
 * the one place the number is read. {@code who} is whoever is reading - the shooter, the thrower, the
 * unit rolling the dice - which is what decides which held effects reach the number ({@link Scope}).
 *
 * <p>Costs a pass over what is held, a handful of entries, and allocates nothing. Built once per world
 * by {@code Simulation} and handed to the systems that read, as {@code Spawner} is.
 */
public final class Modifiers {

    private static final ComponentMapper<Upgrades> UPGRADES = ComponentMapper.getFor(Upgrades.class);

    private final ImmutableArray<Entity> ledgers;
    private final Defs<UpgradeKind, UpgradeDef> catalogue;
    private final Defs<Stat, StatDef> stats;

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

    /** How often {@code who} does this occasional thing, per second of opportunity. */
    @Reads(Stat.CHANCE)
    public float chance(Entity who, AiChance kind, float base) {
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
        return Fold.chance(base, perSecond);
    }

    /**
     * The sum, rank by rank, of every held effect on this stat that reaches whoever is reading - the side's
     * upgrades and the aliens' adaptations alike, since each names whom it reaches. {@code who} is null for
     * the side's own numbers; {@code gun} narrows a gun's stat to one gun, {@code card} a deck's to one card.
     * Whole numbers, so the sum is exact whatever order it is added in.
     */
    private int sum(Stat stat, @Nullable Entity who, @Nullable WeaponKind gun, @Nullable PickupKind card) {
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
