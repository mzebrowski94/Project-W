package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Dodge;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Thrower;
import pl.mzebrows.pwt.sim.component.Wander;
import pl.mzebrows.pwt.sim.component.Weapon;

/**
 * Everything that counts down, counted down in one place, at the front of the tick.
 *
 * <p>Timers scattered through the systems that consume them drift apart: one decrements before its
 * check, another after, and whether a cooldown of exactly one tick lets you act depends on which
 * system you are reading. Here they all get the same treatment on the same tick boundary, and a
 * consumer only ever asks {@code ready()}.
 *
 * <p>Timed values are counted down but their components are never removed. Adding or removing a
 * component mid-tick mutates the families being iterated, which silently skips entities; a spent
 * timer simply reads as inactive.
 *
 * <p>The phase machines - {@link Leap}, {@link Wander}, {@link Dodge} and the wind-up half of {@link
 * Melee} - keep their clocks here
 * for the same reason, and read them where the phases are decided. Counting down is not deciding.
 */
public final class CooldownSystem extends EntitySystem {

    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);
    private static final ComponentMapper<ContactDamage> CONTACT_DAMAGE = ComponentMapper.getFor(ContactDamage.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Melee> MELEE = ComponentMapper.getFor(Melee.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Wander> WANDER = ComponentMapper.getFor(Wander.class);
    private static final ComponentMapper<Dodge> DODGE = ComponentMapper.getFor(Dodge.class);
    private static final ComponentMapper<Blast> BLAST = ComponentMapper.getFor(Blast.class);
    private static final ComponentMapper<Slick> SLICK = ComponentMapper.getFor(Slick.class);
    private static final ComponentMapper<Shake> SHAKE = ComponentMapper.getFor(Shake.class);
    private static final ComponentMapper<Thrower> THROWER = ComponentMapper.getFor(Thrower.class);
    private static final ComponentMapper<Heat> HEAT = ComponentMapper.getFor(Heat.class);

    private ImmutableArray<Entity> invulnerable = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> attacks = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> weapons = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> melees = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> stunned = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> dying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> leaps = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> wanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> dodges = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> blasts = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> slicks = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> shakes = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> throwers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> heats = new ImmutableArray<>(new Array<>());

    @Override
    public void addedToEngine(Engine engine) {
        invulnerable = engine.getEntitiesFor(Family.all(Invulnerable.class).get());
        attacks = engine.getEntitiesFor(Family.all(ContactDamage.class).get());
        weapons = engine.getEntitiesFor(Family.all(Weapon.class).get());
        melees = engine.getEntitiesFor(Family.all(Melee.class).get());
        stunned = engine.getEntitiesFor(Family.all(Stunned.class).get());
        dying = engine.getEntitiesFor(Family.all(Dying.class).get());
        leaps = engine.getEntitiesFor(Family.all(Leap.class).get());
        wanders = engine.getEntitiesFor(Family.all(Wander.class).get());
        dodges = engine.getEntitiesFor(Family.all(Dodge.class).get());
        blasts = engine.getEntitiesFor(Family.all(Blast.class).get());
        slicks = engine.getEntitiesFor(Family.all(Slick.class).get());
        shakes = engine.getEntitiesFor(Family.all(Shake.class).get());
        throwers = engine.getEntitiesFor(Family.all(Thrower.class).get());
        heats = engine.getEntitiesFor(Family.all(Heat.class, Weapon.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < invulnerable.size(); i++) {
            Invulnerable protection = INVULNERABLE.get(invulnerable.get(i));
            protection.remainingSeconds = countDown(protection.remainingSeconds, deltaTime);
        }
        for (int i = 0; i < attacks.size(); i++) {
            ContactDamage attack = CONTACT_DAMAGE.get(attacks.get(i));
            attack.remainingSeconds = countDown(attack.remainingSeconds, deltaTime);
        }
        for (int i = 0; i < weapons.size(); i++) {
            Weapon weapon = WEAPON.get(weapons.get(i));
            weapon.remainingSeconds = countDown(weapon.remainingSeconds, deltaTime);
        }
        for (int i = 0; i < melees.size(); i++) {
            Melee melee = MELEE.get(melees.get(i));
            melee.remainingSeconds = countDown(melee.remainingSeconds, deltaTime);
            melee.windupRemaining = countDown(melee.windupRemaining, deltaTime);
        }
        for (int i = 0; i < stunned.size(); i++) {
            Stunned hold = STUNNED.get(stunned.get(i));
            hold.remainingSeconds = countDown(hold.remainingSeconds, deltaTime);
        }
        for (int i = 0; i < dying.size(); i++) {
            Dying throes = DYING.get(dying.get(i));
            if (throes.begun) {
                throes.remainingSeconds = countDown(throes.remainingSeconds, deltaTime);
            }
        }
        for (int i = 0; i < leaps.size(); i++) {
            Leap leap = LEAP.get(leaps.get(i));
            leap.remainingSeconds = countDown(leap.remainingSeconds, deltaTime);
            leap.cooldownRemaining = countDown(leap.cooldownRemaining, deltaTime);
        }
        for (int i = 0; i < wanders.size(); i++) {
            Wander wander = WANDER.get(wanders.get(i));
            wander.remainingSeconds = countDown(wander.remainingSeconds, deltaTime);
        }
        for (int i = 0; i < dodges.size(); i++) {
            Dodge dodge = DODGE.get(dodges.get(i));
            dodge.remainingSeconds = countDown(dodge.remainingSeconds, deltaTime);
            dodge.commitRemaining = countDown(dodge.commitRemaining, deltaTime);
        }
        for (int i = 0; i < blasts.size(); i++) {
            Blast blast = BLAST.get(blasts.get(i));
            blast.remainingSeconds = countDown(blast.remainingSeconds, deltaTime);
        }
        for (int i = 0; i < slicks.size(); i++) {
            Slick slick = SLICK.get(slicks.get(i));
            slick.remainingSeconds = countDown(slick.remainingSeconds, deltaTime);
        }
        for (int i = 0; i < shakes.size(); i++) {
            Shake shake = SHAKE.get(shakes.get(i));
            shake.remainingSeconds = countDown(shake.remainingSeconds, deltaTime);
        }
        for (int i = 0; i < throwers.size(); i++) {
            Thrower thrower = THROWER.get(throwers.get(i));
            thrower.remainingSeconds = countDown(thrower.remainingSeconds, deltaTime);
        }
        for (int i = 0; i < heats.size(); i++) {
            Entity entity = heats.get(i);
            // Always, whoever is holding it and whether or not anybody is: a gun left standing cools
            // at exactly the rate one in your hands does, so letting go of a hot turret is not a way
            // of putting the bar back. The gun's own numbers, read where they are needed.
            Weapon weapon = WEAPON.get(entity);
            HEAT.get(entity).cool(weapon.heatCoolPerSecond, weapon.heatResumeBelow, deltaTime);
        }
    }

    /** Never runs below zero, so a spent timer cannot drift into a number nothing compares usefully. */
    private static float countDown(float remaining, float deltaTime) {
        if (remaining <= 0f) {
            return remaining;
        }
        float left = remaining - deltaTime;
        return left > 0f ? left : 0f;
    }
}
