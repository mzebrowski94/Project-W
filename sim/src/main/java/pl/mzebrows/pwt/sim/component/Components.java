package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Every kind of component there is, in one list - the table a snapshot numbers them by (ADR 0014).
 *
 * <p>A snapshot has to say which component it is writing, and a class name read off the wire is exactly
 * the decoder ADR 0012 rejects. So components are numbered by their place in this list, sorted by name,
 * which is the same on every machine running the same build. Adding a component is one line here;
 * {@code ComponentResetTest} fails the build if one is missing.
 */
public final class Components {

    /** Sorted by simple name, so a component's number does not depend on where in the list it was typed. */
    public static final List<Class<? extends Component>> ALL = Stream.<Class<? extends Component>>of(
                    Action.class,
                    Appearance.class,
                    Armour.class,
                    Approach.class,
                    Armoury.class,
                    Avoidance.class,
                    Barrage.class,
                    Blast.class,
                    BlastsOnDeath.class,
                    Bloat.class,
                    Body.class,
                    Collider.class,
                    Commander.class,
                    ContactDamage.class,
                    ControlPoint.class,
                    Crate.class,
                    Crew.class,
                    Damage.class,
                    Dead.class,
                    Dodge.class,
                    Door.class,
                    Drag.class,
                    Dragged.class,
                    Dying.class,
                    Enemy.class,
                    Facing.class,
                    Faction.class,
                    Feed.class,
                    Grip.class,
                    Gripped.class,
                    Focus.class,
                    Handle.class,
                    Health.class,
                    Heat.class,
                    Hop.class,
                    Intent.class,
                    Invulnerable.class,
                    Lair.class,
                    Leap.class,
                    Level.class,
                    Melee.class,
                    Mine.class,
                    Nest.class,
                    Obstacle.class,
                    Perception.class,
                    Pickup.class,
                    Position.class,
                    Post.class,
                    PreviousPosition.class,
                    Projectile.class,
                    Ranged.class,
                    Respawning.class,
                    RespawnsOnDeath.class,
                    Shake.class,
                    Shield.class,
                    Shock.class,
                    Slick.class,
                    Sling.class,
                    SlicksOnDeath.class,
                    Slowed.class,
                    Speed.class,
                    Steering.class,
                    Strain.class,
                    Strike.class,
                    Stunned.class,
                    Taking.class,
                    Tally.class,
                    Thrower.class,
                    Thrown.class,
                    Trooper.class,
                    Turret.class,
                    Velocity.class,
                    Vitality.class,
                    Wander.class,
                    Waves.class,
                    Upgrades.class,
                    Drafts.class,
                    Presence.class,
                    Weapon.class,
                    Weave.class)
            .sorted(Comparator.comparing(Class::getSimpleName))
            .toList();

    private Components() {}
}
