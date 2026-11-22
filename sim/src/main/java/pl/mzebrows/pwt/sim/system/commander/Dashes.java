package pl.mzebrows.pwt.sim.system.commander;

import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Dash;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * How many dashes a commander holds and how long one takes to come back (G0107), in one place, for the dash and for
 * his panel - so the pips are the number the next press meets. Folded at the read, as his grenades are (G0077).
 *
 * <p>One per simulation.
 */
public final class Dashes {

    private final Modifiers modifiers;

    private final int most;

    private final int rechargeTicks;

    public Dashes(Modifiers modifiers, SimConfig.DashConfig dash, float tickSeconds) {
        this.modifiers = modifiers;
        this.most = dash.most();
        this.rechargeTicks = Ticks.of(dash.rechargeSeconds(), tickSeconds);
    }

    /** How many dashes he holds at most, upgrades included. */
    public int most(Entity entity) {
        return modifiers.dashMost(entity, most);
    }

    /** How many he has left. */
    public int left(Entity entity, Dash dash) {
        return Math.max(0, most(entity) - dash.spent);
    }

    /** How long one he spent takes to come back, in whole ticks, upgrades included. */
    public int rechargeTicks(Entity entity) {
        return modifiers.dashRechargeTicks(entity, rechargeTicks);
    }
}
