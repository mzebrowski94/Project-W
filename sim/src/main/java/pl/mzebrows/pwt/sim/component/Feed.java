package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * What a unit has eaten, and where that is taking it.
 *
 * <p>The two fields are the two ways an enemy can change strain, and they are deliberately one
 * component rather than two. {@code count} is the slow way - eat enough of anything and the strain's
 * threshold turns you into whatever it says. {@code mutateInto} is the fast way - eat one specific
 * thing and become one specific creature, whatever you had eaten before. {@code EvolutionSystem}
 * reads both and writes one strain, so the two paths cannot disagree about what a unit turned into.
 *
 * <p>Carried permanently by anything that can evolve, rather than added when it first eats. Adding or
 * removing a component mid-tick mutates the families being iterated, which silently skips entities.
 */
public final class Feed implements Component, Pool.Poolable {

    /** How much has been consumed towards this strain's threshold. */
    public int count;

    /** A strain something it ate named outright, or null if nothing has. */
    public @Nullable UnitKind mutateInto;

    /** Adds to what has been eaten. */
    public void add(int amount) {
        count += amount;
    }

    /**
     * Records a strain to turn into at once. The first such instruction of a tick wins; a unit that
     * somehow ate two transforming things in one tick becomes the first of them rather than a race.
     */
    public void mutateInto(UnitKind kind) {
        if (mutateInto == null) {
            mutateInto = kind;
        }
    }

    /** Clears the ledger, which is what having evolved means. */
    public void spend() {
        count = 0;
        mutateInto = null;
    }

    @Override
    public void reset() {
        count = 0;
        mutateInto = null;
    }
}
