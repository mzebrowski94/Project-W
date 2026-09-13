package pl.mzebrows.pwt.sim.def;

import org.jspecify.annotations.Nullable;

/**
 * What taking a pickup does. One value per effect, as data rather than as code.
 *
 * <p>A sealed hierarchy of records rather than a callback, an enum with a payload, or a switch on a
 * kind. The reasons are the ones this codebase already trades on:
 *
 * <ul>
 *   <li><strong>The compiler names every site that must change.</strong> Adding an effect adds a
 *       record here and fails the build in every exhaustive switch over it until each one has decided
 *       what to do. A callback would have compiled and silently done nothing new.
 *   <li><strong>It is inspectable and replayable.</strong> A grant is a value sitting in a
 *       definition table, visible in a debugger and identical on every run - which a lambda captured
 *       at load time is not.
 *   <li><strong>It allocates nothing at play time.</strong> Every grant is built once when the config
 *       is, and applying one is a pattern match over fields.
 * </ul>
 *
 * <p>Grants come in two scopes and the difference matters. {@link Feed} and {@link Mutate} change the
 * entity that took the pickup; {@link Reinforce} changes the level. The second sort is why the
 * simulation has a level entity at all - it is the ordinary-entity answer to state that belongs to
 * nobody in particular, and it is what keeps G0004 intact where a global would otherwise have argued
 * for a message queue.
 */
public sealed interface Grant {

    /** Offers a side nothing. A side with nothing on offer walks over the pickup and leaves it. */
    Grant NOTHING = new None();

    /** Destroys the pickup in the taker's face. What a barrel is worth to something hungry. */
    Grant DETONATE = new Detonate();

    /** Nothing happens, and the pickup is not consumed. */
    record None() implements Grant {}

    /** Counts towards whatever the taker's strain evolves into. */
    record Feed(int amount) implements Grant {
        public Feed {
            Require.atLeastOne("feed amount", amount);
        }
    }

    /** Turns the taker into a named strain at once, whatever it has eaten so far. */
    record Mutate(UnitKind into) implements Grant {}

    /** Puts a different gun in the taker's hands, replacing whatever it was carrying. */
    record Arm(WeaponKind weapon) implements Grant {}

    /**
     * Nothing reaches the taker at all. Taking the thing is what <em>destroys</em> it, and what it
     * does on being destroyed is its own death reaction rather than anything granted here.
     *
     * <p>It exists so that a side which sets a barrel off is a side the barrel is consumed by.
     * {@link None} would leave it standing and the alien would walk over it for the rest of the level.
     */
    record Detonate() implements Grant {}

    /** Adds troopers to the level rather than changing whoever picked it up. */
    record Reinforce(int troopers) implements Grant {
        public Reinforce {
            Require.atLeastOne("reinforce troopers", troopers);
        }
    }

    /** True if this grant is worth consuming a pickup for. */
    static boolean isSomething(@Nullable Grant grant) {
        return grant != null && !(grant instanceof None);
    }
}
