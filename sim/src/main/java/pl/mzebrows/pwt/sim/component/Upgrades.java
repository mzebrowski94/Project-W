package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * The ledger: what each side holds, with ranks - the players' upgrades and the aliens' adaptations (ADR 0017). Level state on the level entity (G0007),
 * so a snapshot, a drop-in and a heal carry it with no code of their own. Written only by the upgrade
 * package and read only through {@code Modifiers}; {@code ArchitectureTest} fences it.
 */
public final class Upgrades implements Component, Pool.Poolable {

    /** The slot a shared upgrade is held by: the side's, not any one player's. */
    public static final int SHARED = -1;

    /** One upgrade held: by the side ({@link #SHARED}) or, for a personal one, by the slot that took it. */
    public record Held(UpgradeKind kind, int slot, int ranks) {}

    /** Everything held, in the order it was first taken. */
    public final Array<Held> held = new Array<>();

    /**
     * Bumped whenever the ledger changes, so whoever shows it rebuilds only then - and {@code Modifiers} folds
     * its sums afresh: a write that moves neither this nor the size of {@link #held} would read stale numbers.
     */
    public int revision;

    /** How many adaptations the swarm has taken this level (ADR 0017) - what unlocks an adaptation's tier. */
    public int adaptations;

    /**
     * How many adaptations the level plans (G0118), fixed on its first tick with the drafts' schedule, its spread
     * drawn - what an adaptation's tier opens by a share of.
     */
    public int adaptationsPlanned;

    /** How many sites taken the swarm's count has been given. */
    public int sitesCounted;

    /** The furthest biome any commander has entered, as a count of the ridges behind it (G0118). */
    public int biomesEntered;

    /** The swarm's count of sources passed, in hundredths, each worth what it was with the players then seated. */
    public int passedHundredths;

    /** How many adaptations the schedule has been credited with so far, dealt or not. */
    public int credited;

    @Override
    public void reset() {
        held.clear();
        revision = 0;
        adaptations = 0;
        adaptationsPlanned = 0;
        sitesCounted = 0;
        biomesEntered = 0;
        passedHundredths = 0;
        credited = 0;
    }
}
