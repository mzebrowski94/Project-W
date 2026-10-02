package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * What it costs to pull a trigger and what comes out of it, in one place, for everything that pulls
 * one - a commander, a trooper, a shooting alien, a turret and the boss's barrage.
 *
 * <p>Every shooter asks the same questions - may I, what goes in the air, what did it cost - and the
 * answers must not depend on whose hands the gun is in. So the gun, where it stands, how big it is and
 * whose side it is on are all read off the firer here rather than handed in; what a caller still says
 * is only what the firer cannot: which way, whose tally a kill counts for (a turret's is its crew's,
 * G0044), and - for a creature that fires faster up close - the cooldown. Something with no
 * {@link Heat} never runs hot.
 *
 * <p>One per simulation, built with the shared scatter stream, so every pellet any shooter throws is
 * drawn from the one place.
 */
public final class Guns {

    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Heat> HEAT = ComponentMapper.getFor(Heat.class);
    private static final ComponentMapper<Slowed> SLOWED = ComponentMapper.getFor(Slowed.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);

    private final Spawner spawner;
    private final Modifiers modifiers;
    private final Rng scatter;
    private final float muzzleGap;
    private final float[] muzzle = new float[2];

    /**
     * @param scatter how loosely a gun throws, for every shooter; not how badly a creature aims
     * @param muzzleGap how far clear of the firer's body a shot leaves, for every gun there is
     */
    public Guns(Spawner spawner, Modifiers modifiers, Rng scatter, float muzzleGap) {
        this.spawner = spawner;
        this.modifiers = modifiers;
        this.scatter = scatter;
        this.muzzleGap = muzzleGap;
    }

    /** Whether this thing may fire at all this tick: the gun is off cooldown and has not stopped itself. */
    boolean ready(Entity firer) {
        if (!WEAPON.get(firer).ready()) {
            return false;
        }
        return !HEAT.has(firer) || !HEAT.get(firer).locked;
    }

    /** One pull of the firer's gun along this aim, charged at the gun's own cooldown. */
    void pull(Entity firer, float aimX, float aimY, int credit) {
        pull(firer, aimX, aimY, credit, WEAPON.get(firer).cooldownTicks);
    }

    /**
     * One pull of the firer's gun along this aim, and what it costs: {@link #volley}, then {@link
     * #spend}. Assumes {@link #ready} was asked first.
     *
     * @param cooldownTicks the wait this pull charges before upgrades and wounds are folded in - the
     *     gun's own for everything but a creature that fires faster up close
     */
    void pull(Entity firer, float aimX, float aimY, int credit, int cooldownTicks) {
        volley(firer, aimX, aimY, credit);
        spend(firer, cooldownTicks);
    }

    /**
     * Everything one pull of the firer's gun puts in the air, along the direction it was aimed, charged
     * to nothing. A barrage calls this straight: its pattern, not the gun, keeps the time.
     *
     * <p>The aim must be a unit vector already carrying the shooter's cone; the gun's scatter is
     * applied here per pellet, by rotation. {@code credit} is the slot of the player whose tally a kill
     * by these shots counts for, or {@link Tally#NOBODY} (G0044). What each shot does is read once, for
     * the whole pull.
     */
    void volley(Entity firer, float aimX, float aimY, int credit) {
        Weapon weapon = WEAPON.get(firer);
        Position at = POSITION.get(firer);
        float bodyRadius = BODY.get(firer).radius;
        int pellets = weapon.pellets < 1 ? 1 : weapon.pellets;
        int damage = modifiers.gunDamage(firer, weapon.kind, weapon.damage);
        for (int pellet = 0; pellet < pellets; pellet++) {
            float directionX = aimX;
            float directionY = aimY;
            if (weapon.halfSpreadRadians > 0f) {
                float turn = (scatter.nextFloat() * 2f - 1f) * weapon.halfSpreadRadians;
                float cos = (float) StrictMath.cos(turn);
                float sin = (float) StrictMath.sin(turn);
                directionX = aimX * cos - aimY * sin;
                directionY = aimX * sin + aimY * cos;
            }
            Muzzle.next(at.x, at.y, bodyRadius, weapon, muzzleGap, directionX, directionY, muzzle);
            spawner.spawnProjectile(
                    muzzle[0], muzzle[1], directionX, directionY, weapon, damage, FACTION.get(firer).side, credit);
        }
    }

    /**
     * Charges a shot to the cooldown, to the heat, and to the shooter's own pace - whichever of the
     * three this gun actually asks for.
     *
     * <p>A shooter with no {@code Slowed} - a turret - is unaffected by a self-slow. The cooldown is the
     * one place {@code FIRE_COOLDOWN} is read, for every shooter there is.
     */
    private void spend(Entity firer, int cooldownTicks) {
        Weapon weapon = WEAPON.get(firer);
        // A wounded man is slower on the trigger; his hand strike and his throw are not (G0053).
        int base = VITALITY.has(firer)
                ? Math.round(cooldownTicks * VITALITY.get(firer).cooldownFactor())
                : cooldownTicks;
        weapon.remainingTicks = modifiers.fireCooldownTicks(firer, weapon.kind, base);
        if (HEAT.has(firer)) {
            HEAT.get(firer).spend(weapon.heatPerShot);
        }
        if (SLOWED.has(firer)) {
            SLOWED.get(firer).apply(weapon.selfSlowFactor, weapon.selfSlowTicks);
        }
    }
}
