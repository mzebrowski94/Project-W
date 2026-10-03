package pl.mzebrows.pwt.client;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;
import pl.mzebrows.pwt.ui.hud.Posture;
import pl.mzebrows.pwt.ui.hud.Readout;

/**
 * What the simulation is doing right now, in text.
 *
 * <p>Drawn by {@link TopBarPainter}: its best lines in the top bar's right-hand panel, or all of them over
 * the view's corner when expanded.
 *
 * <p>The alpha phase exists to watch systems work, and a system you cannot see the state of is a
 * system you are guessing about. Entity counts catch leaks - projectiles that never despawn,
 * enemies that never die - long before they are visible on screen.
 */
public final class DebugOverlay {

    /**
     * The overlay's lines, most useful first, so a panel with room for four shows the four that matter
     * most. Never called from inside the simulation, so it may allocate freely.
     *
     * @param readout this frame's, filled before the overlay is asked: the commander is described from it, as
     *     his panel is
     * @param ownSlot the slot this machine's keyboard plays, whose commander the overlay describes
     * @param fps the frames drawn in the last second
     * @param connection a line about the online session, or null offline
     * @param invitation how friends join, while this copy hosts; null otherwise
     */
    public List<String> lines(
            Simulation simulation,
            Readout readout,
            int ownSlot,
            int fps,
            String scenarioName,
            boolean paused,
            @Nullable Order lastOrder,
            @Nullable String connection,
            @Nullable String invitation) {
        List<String> lines = new ArrayList<>();
        lines.add(scenarioName + (paused ? "   [PAUSED]" : ""));
        lines.add("fps " + fps + "   tick " + simulation.tick());
        if (connection != null) {
            lines.add(connection);
        }
        if (invitation != null) {
            lines.add(invitation);
        }
        lines.add("commander " + commanderState(readout.player(ownSlot), simulation.commanderInvulnerable()));
        lines.add("commanders " + count(simulation, Commander.class) + "  enemies "
                + count(simulation, Enemy.class) + "  projectiles "
                + count(simulation, Projectile.class) + "  obstacles "
                + count(simulation, Obstacle.class));
        lines.add("entities " + simulation.engine().getEntities().size());
        lines.add("last order " + (lastOrder == null ? "-" : lastOrder.name().toLowerCase(Locale.ROOT)));
        lines.add("1 follow 2 forward 3 defence 4 cancel  P pause . step R restart G god F1 F2 lead F3 lag ESC menu");
        return lines;
    }

    /**
     * His wounds, or what he is doing instead of standing (G0053), the gun his panel shows, and whatever
     * protects him. Dying and respawning is invisible without this - the commander simply vanishes for three
     * seconds and reappears somewhere else.
     *
     * @param godMode whether the harness has made every commander invulnerable
     */
    static String commanderState(PlayerReadout player, boolean godMode) {
        if (player.posture == Posture.ABSENT) {
            return "dead, respawning";
        }
        boolean down = player.posture == Posture.CASUALTY || player.posture == Posture.DYING;
        String standing = down ? "down" : "wounds " + player.wounds + "/" + player.maxWounds;
        String state = standing + "   " + player.weapon;
        if (godMode) {
            return state + "   GOD MODE";
        }
        if (player.invulnerable) {
            return state + "   invulnerable";
        }
        return state;
    }

    private static int count(Simulation simulation, Class<? extends Component> marker) {
        return simulation.engine().getEntitiesFor(Family.all(marker).get()).size();
    }
}
