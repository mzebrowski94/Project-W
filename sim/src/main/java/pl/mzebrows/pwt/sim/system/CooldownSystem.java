package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Stunned;
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
 * <p>The phase machines - {@link Leap}, {@link Wander} and the wind-up half of {@link Melee} - keep
 * their clocks here
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

    private ImmutableArray<Entity> invulnerable = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> attacks = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> weapons = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> melees = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> stunned = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> dying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> leaps = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> wanders = new ImmutableArray<>(new Array<>());

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
