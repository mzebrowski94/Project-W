package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * The ledger: what each side holds, with ranks (ADR 0017). Level state on the level entity (G0007),
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

    /** Bumped whenever the ledger changes, so whoever shows it rebuilds only then. */
    public int revision;

    /** How many cards the players have taken this level. */
    public int playerPicks;

    @Override
    public void reset() {
        held.clear();
        revision = 0;
        playerPicks = 0;
    }
}
