package pl.mzebrows.pwt.sim.upgrade;

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
        return Fold.scaleTicks(baseTicks, percent(Stat.FIRE_COOLDOWN, who, gun), stat.minPercent(), stat.maxPercent());
    }

    /** What each shot of one pull of this gun does. */
    @Reads(Stat.GUN_DAMAGE)
    public int gunDamage(Entity who, WeaponKind gun, int base) {
        StatDef stat = stats.of(Stat.GUN_DAMAGE);
        return Fold.scaleWhole(base, percent(Stat.GUN_DAMAGE, who, gun), stat.minPercent(), stat.maxPercent());
    }

    /** How many men a crate of reinforcements lands. The side's number: no one entity reads it. */
    @Reads(Stat.MEN_PER_CRATE)
    public int menPerCrate(int base) {
        return Fold.count(
                base, amount(Stat.MEN_PER_CRATE), stats.of(Stat.MEN_PER_CRATE).min());
    }

    /** How many men past its starting size the squad may hold. The side's number. */
    @Reads(Stat.SQUAD_HEADROOM)
    public int squadHeadroom(int base) {
        return Fold.count(
                base, amount(Stat.SQUAD_HEADROOM), stats.of(Stat.SQUAD_HEADROOM).min());
    }

    /** A thrower's wait between grenades, in ticks. */
    @Reads(Stat.GRENADE_COOLDOWN)
    public int grenadeCooldownTicks(Entity who, int baseTicks) {
        StatDef stat = stats.of(Stat.GRENADE_COOLDOWN);
        return Fold.scaleTicks(
                baseTicks, percent(Stat.GRENADE_COOLDOWN, who, null), stat.minPercent(), stat.maxPercent());
    }

    /** The share of its pace a driven unit goes at: the pace {@code who} was going to be driven at, folded. */
    @Reads(Stat.SPEED)
    public float pace(Entity who, float base) {
        StatDef stat = stats.of(Stat.SPEED);
        return Fold.scale(base, percent(Stat.SPEED, who, null), stat.minPercent(), stat.maxPercent());
    }

    /** How much of a casualty's stabilise one tick of {@code who}'s work does, in seconds of it. */
    @Reads(Stat.STABILISE_SPEED)
    public float stabiliseWork(Entity who, float base) {
        StatDef stat = stats.of(Stat.STABILISE_SPEED);
        return Fold.scale(base, percent(Stat.STABILISE_SPEED, who, null), stat.minPercent(), stat.maxPercent());
    }

    /** How long a stun a source declared leaves {@code who} unable to act, in ticks (G0053). */
    @Reads(Stat.STUN_DURATION)
    public int stunTicks(Entity who, int baseTicks) {
        StatDef stat = stats.of(Stat.STUN_DURATION);
        return Fold.scaleTicks(baseTicks, percent(Stat.STUN_DURATION, who, null), stat.minPercent(), stat.maxPercent());
    }

    /** How many wounds {@code who} carries on his feet (G0053). */
    @Reads(Stat.MAX_WOUNDS)
    public int maxWounds(Entity who, int base) {
        return Fold.count(
                base, amount(Stat.MAX_WOUNDS, who), stats.of(Stat.MAX_WOUNDS).min());
    }

    /** How long after his last wound {@code who}'s wounds clear on their own, in seconds; zero never. */
    @Reads(Stat.MEND_SECONDS)
    public int mendSeconds(Entity who, int base) {
        return Fold.count(
                base,
                amount(Stat.MEND_SECONDS, who),
                stats.of(Stat.MEND_SECONDS).min());
    }

    /** How many cards of this cache the armoury deck holds (G0050). The side's number. */
    @Reads(Stat.ARMOURY_CARDS)
    public int armouryCards(PickupKind card, int base) {
        return Fold.count(base, dealt(card), stats.of(Stat.ARMOURY_CARDS).min());
    }

    /** The fewest pickups a crate is dealt (G0056). The side's number. */
    @Reads(Stat.CRATE_LEAST)
    public int crateLeast(int base) {
        return Fold.count(
                base, amount(Stat.CRATE_LEAST), stats.of(Stat.CRATE_LEAST).min());
    }

    /** How many of this card every crate holds at least (G0056). The side's number. */
    @Reads(Stat.CRATE_STOCK)
    public int crateStock(PickupKind card) {
        return Fold.count(0, stocked(card), stats.of(Stat.CRATE_STOCK).min());
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

    /** The sum of every held deal of this cache, rank by rank. */
    private int dealt(PickupKind card) {
        int sum = 0;
        for (int l = 0; l < ledgers.size(); l++) {
            Upgrades upgrades = UPGRADES.get(ledgers.get(l));
            for (int h = 0; h < upgrades.held.size; h++) {
                Upgrades.Held entry = upgrades.held.get(h);
                List<Effect> effects = catalogue.of(entry.kind()).effects();
                for (int e = 0; e < effects.size(); e++) {
                    if (effects.get(e) instanceof Effect.Deal deal && deal.card() == card) {
                        sum += deal.amount() * entry.ranks();
                    }
                }
            }
        }
        return sum;
    }

    /** The sum of every held stock of this card, rank by rank. */
    private int stocked(PickupKind card) {
        int sum = 0;
        for (int l = 0; l < ledgers.size(); l++) {
            Upgrades upgrades = UPGRADES.get(ledgers.get(l));
            for (int h = 0; h < upgrades.held.size; h++) {
                Upgrades.Held entry = upgrades.held.get(h);
                List<Effect> effects = catalogue.of(entry.kind()).effects();
                for (int e = 0; e < effects.size(); e++) {
                    if (effects.get(e) instanceof Effect.Stock stock && stock.card() == card) {
                        sum += stock.amount() * entry.ranks();
                    }
                }
            }
        }
        return sum;
    }

    /**
     * The sum of every held percentage on this stat that reaches {@code who}, for {@code gun} if it is a
     * gun's - the side's upgrades and the aliens' adaptations alike, since each names whom it reaches.
     */
    private int percent(Stat stat, Entity who, @Nullable WeaponKind gun) {
        int sum = 0;
        for (int l = 0; l < ledgers.size(); l++) {
            Upgrades upgrades = UPGRADES.get(ledgers.get(l));
            for (int h = 0; h < upgrades.held.size; h++) {
                Upgrades.Held entry = upgrades.held.get(h);
                List<Effect> effects = catalogue.of(entry.kind()).effects();
                for (int e = 0; e < effects.size(); e++) {
                    Effect effect = effects.get(e);
                    if (effect instanceof Effect.Scale scale
                            && scale.stat() == stat
                            && Scope.reaches(scale.target(), who, entry.slot())) {
                        sum += scale.percent() * entry.ranks();
                    } else if (effect instanceof Effect.Gun only
                            && only.gun() == gun
                            && only.stat() == stat
                            && Scope.reaches(only.target(), who, entry.slot())) {
                        sum += only.percent() * entry.ranks();
                    } else if (effect instanceof Effect.Breed breed
                            && breed.stat() == stat
                            && Scope.reaches(breed.strain(), who)) {
                        sum += breed.percent() * entry.ranks();
                    }
                }
            }
        }
        return sum;
    }

    /** The sum of every held amount on this count that reaches {@code who} - a unit's own count. */
    private int amount(Stat stat, Entity who) {
        int sum = 0;
        for (int l = 0; l < ledgers.size(); l++) {
            Upgrades upgrades = UPGRADES.get(ledgers.get(l));
            for (int h = 0; h < upgrades.held.size; h++) {
                Upgrades.Held entry = upgrades.held.get(h);
                List<Effect> effects = catalogue.of(entry.kind()).effects();
                for (int e = 0; e < effects.size(); e++) {
                    if (effects.get(e) instanceof Effect.Count count
                            && count.stat() == stat
                            && Scope.reaches(count.target(), who, entry.slot())) {
                        sum += count.amount() * entry.ranks();
                    }
                }
            }
        }
        return sum;
    }

    /** The sum of every held amount on this side-wide count. */
    private int amount(Stat stat) {
        int sum = 0;
        for (int l = 0; l < ledgers.size(); l++) {
            Upgrades upgrades = UPGRADES.get(ledgers.get(l));
            for (int h = 0; h < upgrades.held.size; h++) {
                Upgrades.Held entry = upgrades.held.get(h);
                List<Effect> effects = catalogue.of(entry.kind()).effects();
                for (int e = 0; e < effects.size(); e++) {
                    if (effects.get(e) instanceof Effect.Count count && count.stat() == stat) {
                        sum += count.amount() * entry.ranks();
                    }
                }
            }
        }
        return sum;
    }
}
