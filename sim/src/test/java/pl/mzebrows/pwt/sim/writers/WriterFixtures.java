package pl.mzebrows.pwt.sim.writers;

import com.badlogic.ashley.core.EntitySystem;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Drafts;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Harm;

/** Small systems and helpers for {@code ComponentWritersTest} to measure, each doing one kind of thing to a component. */
public final class WriterFixtures {

    private WriterFixtures() {}

    /** Sets a field from outside the component. */
    public static final class Steers extends EntitySystem {
        void steer(Velocity velocity) {
            velocity.x = 1f;
        }
    }

    /** Calls a method of the component that changes it. */
    public static final class Hits extends EntitySystem {
        void hit(Damage damage) {
            damage.land(1, Harm.WOUND);
        }
    }

    /** Reads a field and calls a method that changes nothing. */
    public static final class Watches extends EntitySystem {
        boolean watch(Position position, Projectile projectile, Drafts drafts) {
            return position.x > 0f && projectile.bounces() && drafts.submitted.size > 0;
        }
    }

    /** Changes a collection a field of the component holds. */
    public static final class Clears extends EntitySystem {
        void clear(Drafts drafts) {
            drafts.submitted.clear();
        }
    }

    /** Writes from inside a lambda of its own. */
    public static final class Defers extends EntitySystem {
        void defer(Projectile projectile) {
            Runnable later = () -> projectile.bounceAt(1f, 1f);
            later.run();
        }
    }

    /** Hands the write to a helper that makes it inside a lambda. */
    public static final class Delegates extends EntitySystem {
        void delegate(Projectile projectile) {
            Lag.bounce(projectile);
        }
    }

    /** Hands the write to a helper through an interface the helper implements. */
    public static final class Pushes extends EntitySystem {
        private final Push push = new Shove();

        void push(Position position) {
            push.apply(position);
        }
    }

    /** Hands the write to helpers that call each other round in a ring. */
    public static final class Circles extends EntitySystem {
        void circle(Position position) {
            Ring.there(position, 2);
        }
    }

    /** Hands the write to a helper. */
    public static final class Ricochets extends EntitySystem {
        void ricochet(Velocity velocity) {
            Deflect.reflect(velocity);
        }
    }

    /** Writes, and is no system, and no system calls it - the way {@code Simulation.step} writes intents. */
    static final class Orders {

        private Orders() {}

        static void order(Velocity velocity) {
            velocity.x = 0f;
        }
    }

    /** Asks the spawner to change an entity that already exists. */
    public static final class Rearms extends EntitySystem {
        void rearm(Velocity velocity) {
            Spawner.applyWeapon(velocity);
        }
    }

    /** Named as the real one is: what it spawns is assembly, what it changes for a system is that system's. */
    static final class Spawner {

        private Spawner() {}

        static void spawnUnit(Velocity velocity) {
            velocity.set(3f, 3f);
        }

        static void applyWeapon(Velocity velocity) {
            velocity.y = 4f;
        }
    }

    interface Push {
        void apply(Position position);
    }

    static final class Shove implements Push {
        @Override
        public void apply(Position position) {
            position.x += 1f;
        }
    }

    static final class Ring {

        private Ring() {}

        static void there(Position position, int laps) {
            if (laps > 0) {
                back(position, laps - 1);
            }
        }

        static void back(Position position, int laps) {
            position.set(0f, 0f);
            there(position, laps);
        }
    }

    static final class Lag {

        private Lag() {}

        static void bounce(Projectile projectile) {
            Runnable later = () -> projectile.bounceAt(2f, 2f);
            later.run();
        }
    }

    /** A helper that writes, the way {@code Bounce} does for the systems that call it. */
    static final class Deflect {

        private Deflect() {}

        static void reflect(Velocity velocity) {
            velocity.y = -velocity.y;
        }
    }
}
