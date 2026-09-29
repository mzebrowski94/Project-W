package pl.mzebrows.pwt.sim.upgrade;

import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The one read port for every number an upgrade may move (ADR 0017). The reader hands over the base it
 * already holds - a definition, or the copy a component took at spawn - and gets back the number to
 * use, folded afresh on every read. Nothing is written back, so a unit spawned after an upgrade is
 * identical to one that was standing when it landed.
 *
 * <p>One method per {@link Stat}, marked {@link Reads}, and each has exactly one caller: that caller is
 * the one place the number is read. {@code who} is whoever is reading - the shooter, the thrower, the
 * unit rolling the dice - which is what decides which upgrades reach the number.
 *
 * <p>Nothing is held yet, so every method hands back its base; the ledger arrives with the draft. Built
 * once per world by {@code Simulation} and handed to the systems that read, as {@code Spawner} is.
 */
public final class Modifiers {

    /** A gun's cooldown in ticks, for every shooter: the commander, a trooper, an alien, a turret. */
    @Reads(Stat.FIRE_COOLDOWN)
    public int fireCooldownTicks(Entity who, WeaponKind gun, int baseTicks) {
        return baseTicks;
    }

    /** What each shot of one pull of this gun does. */
    @Reads(Stat.GUN_DAMAGE)
    public float gunDamage(Entity who, WeaponKind gun, float base) {
        return base;
    }

    /** How many men a crate of reinforcements lands. The side's number: no one entity reads it. */
    @Reads(Stat.MEN_PER_CRATE)
    public int menPerCrate(int base) {
        return base;
    }

    /** How many men past its starting size the squad may hold. The side's number. */
    @Reads(Stat.SQUAD_HEADROOM)
    public int squadHeadroom(int base) {
        return base;
    }

    /** A thrower's wait between grenades, in ticks. */
    @Reads(Stat.GRENADE_COOLDOWN)
    public int grenadeCooldownTicks(Entity who, int baseTicks) {
        return baseTicks;
    }

    /** How often {@code who} does this occasional thing, per second of opportunity. */
    @Reads(Stat.CHANCE)
    public float chance(Entity who, AiChance kind, float base) {
        return base;
    }
}
