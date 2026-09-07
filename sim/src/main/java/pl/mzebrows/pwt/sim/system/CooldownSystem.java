package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Invulnerable;
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
 */
public final class CooldownSystem extends EntitySystem {

    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);
    private static final ComponentMapper<ContactDamage> CONTACT_DAMAGE = ComponentMapper.getFor(ContactDamage.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);

    private ImmutableArray<Entity> invulnerable = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> attacks = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> weapons = new ImmutableArray<>(new Array<>());

    @Override
    public void addedToEngine(Engine engine) {
        invulnerable = engine.getEntitiesFor(Family.all(Invulnerable.class).get());
        attacks = engine.getEntitiesFor(Family.all(ContactDamage.class).get());
        weapons = engine.getEntitiesFor(Family.all(Weapon.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < invulnerable.size(); i++) {
            Invulnerable protection = INVULNERABLE.get(invulnerable.get(i));
            if (protection.remainingSeconds > 0f) {
                protection.remainingSeconds -= deltaTime;
            }
        }
        for (int i = 0; i < attacks.size(); i++) {
            ContactDamage attack = CONTACT_DAMAGE.get(attacks.get(i));
            if (attack.remainingSeconds > 0f) {
                attack.remainingSeconds -= deltaTime;
            }
        }
        for (int i = 0; i < weapons.size(); i++) {
            Weapon weapon = WEAPON.get(weapons.get(i));
            if (weapon.remainingSeconds > 0f) {
                weapon.remainingSeconds -= deltaTime;
            }
        }
    }
}
