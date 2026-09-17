package pl.mzebrows.pwt.sim.system;

import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;

/**
 * The one way anything in the simulation asks the dice a question.
 *
 * <p>Every occasional behaviour goes through here, which is what makes {@link AiChance} an index
 * rather than a list somebody has to keep current: a system that wanted its own roll would have to
 * write its own conversion, and a search for callers of this class finds all of them.
 *
 * <p><strong>A chance per second, converted with the tick length.</strong> The alternative - a chance
 * per tick - is a number nobody can reason about and one that silently changes what the game does if
 * the tick rate is ever retuned. The conversion is the whole of what this class does that a caller
 * could not, and having it in one place is why every such number in {@code game.properties} means the
 * same thing.
 *
 * <p><strong>Zero draws nothing.</strong> A behaviour switched off must not consume a number from the
 * stream, or turning one off in a scenario would reshuffle every roll made after it - reproducible,
 * so no test catches it, and miserable to iterate against (pillar 6, and the reason {@code RngStream}
 * exists at all).
 */
final class Odds {

    private Odds() {}

    /** Whether this behaviour happens during a tick of this length, given a tick's worth of chance. */
    static boolean rolls(Rng rng, Chances chances, AiChance kind, float deltaTime) {
        return rolls(rng, chances.of(kind), deltaTime);
    }

    /**
     * The same question against a number the caller already has in hand - a strain's own override,
     * copied onto a component at spawn rather than looked up per entity.
     */
    static boolean rolls(Rng rng, float chancePerSecond, float deltaTime) {
        if (chancePerSecond <= 0f) {
            return false;
        }
        return rng.nextFloat() < chancePerSecond * deltaTime;
    }
}
