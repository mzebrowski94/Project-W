package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Turns what a unit has eaten into what it becomes.
 *
 * <p>Two ways in and one way out. A pickup that named a strain outright turns the unit into that
 * strain at once, whatever it had eaten before; anything else adds to a count, and crossing the
 * strain's threshold turns it into whatever that strain says it grows into. Three mushrooms and one
 * flower are the same rule with different numbers, which is why there is one system rather than
 * an evolution mechanic per fiction.
 *
 * <p>Evolving writes fields on components the unit already carries - it never adds or removes one -
 * so a strain change is invisible to every family being iterated. That is the entire reason a strain
 * is an enum on {@code Strain} rather than a marker component, and it is what makes this system four
 * lines long instead of a deferred-command queue.
 *
 * <p>Everything about what the unit becomes is read from the new definition by
 * {@code Spawner.applyStrain}, so an evolved unit is indistinguishable from one that spawned in that
 * strain. Nothing is carried over, health included: a unit that has just become something else
 * arrives whole.
 *
 * <p>One consequence worth knowing: a unit that evolves mid-leap comes down. {@code applyStrain}
 * rewrites its leap from the new strain's numbers, which resets the phase machine, so it lands and
 * starts again as the thing it now is. That reads correctly and is cheaper than preserving a leap
 * whose speed and duration have both just changed underneath it.
 */
public final class EvolutionSystem extends IteratingSystem {

    private static final ComponentMapper<Strain> STRAIN = ComponentMapper.getFor(Strain.class);
    private static final ComponentMapper<Feed> FEED = ComponentMapper.getFor(Feed.class);

    private final Spawner spawner;
    private final Defs<UnitKind, UnitDef> units;

    public EvolutionSystem(SimConfig config, Spawner spawner) {
        super(Family.all(Strain.class, Feed.class).get());
        this.spawner = spawner;
        this.units = config.units();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (Life.dying(entity)) {
            return;
        }
        Feed feed = FEED.get(entity);
        Strain strain = STRAIN.get(entity);
        UnitKind into = target(feed, units.of(strain.kind));
        if (into == null) {
            return;
        }
        feed.spend();
        if (into != strain.kind) {
            spawner.applyStrain(entity, into);
        }
    }

    /**
     * What this unit turns into now, or null if it stays as it is.
     *
     * <p>A named strain wins over a count: eating the thing that transforms you transforms you, and
     * whatever you had eaten before it stops mattering.
     */
    private static @Nullable UnitKind target(Feed feed, UnitDef def) {
        UnitKind named = feed.mutateInto;
        if (named != null) {
            return named;
        }
        if (!def.canEvolveByFeeding() || feed.count < def.feedToEvolve()) {
            return null;
        }
        return def.evolvesTo();
    }
}
