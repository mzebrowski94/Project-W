package pl.mzebrows.pwt.client;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * What the simulation is doing right now, in text.
 *
 * <p>The alpha phase exists to watch systems work, and a system you cannot see the state of is a
 * system you are guessing about. Entity counts catch leaks - projectiles that never despawn,
 * enemies that never die - long before they are visible on screen.
 */
@RequiredArgsConstructor
public final class DebugOverlay {

    private final Hud hud;

    /**
     * Draws the overlay. Never called from inside the simulation, so it may allocate freely.
     *
     * @param ownSlot the slot this machine's keyboard plays, whose commander the overlay describes
     * @param connection a line about the online session, or null offline
     * @param invitation how friends join, while this copy hosts; null otherwise
     */
    public void render(
            Simulation simulation,
            int ownSlot,
            String scenarioName,
            boolean paused,
            @Nullable Order lastOrder,
            @Nullable String connection,
            @Nullable String invitation) {
        hud.begin();
        hud.line(0, scenarioName + (paused ? "   [PAUSED]" : ""));
        hud.line(1, "fps " + Gdx.graphics.getFramesPerSecond() + "    tick " + simulation.tick());
        hud.line(
                2,
                "commanders " + count(simulation, Commander.class) + "    enemies "
                        + count(simulation, Enemy.class) + "    projectiles "
                        + count(simulation, Projectile.class) + "    obstacles "
                        + count(simulation, Obstacle.class));
        hud.line(3, "entities " + simulation.engine().getEntities().size());
        hud.line(4, "commander " + commanderState(simulation, ownSlot));
        hud.line(5, "last order " + (lastOrder == null ? "-" : lastOrder.name().toLowerCase(Locale.ROOT)));
        if (connection != null) {
            hud.line(6, connection);
        }
        if (invitation != null) {
            hud.line(7, invitation);
        }
        hud.line(
                hud.rows() - 1,
                "1 follow  2 forward  3 defence  4 cancel      P pause  . step  R restart  G god  F1  F2 lead  F3 lag  ESC");
        hud.end();
    }

    /**
     * What an online screen shows before it has a world: where it is connecting, or why it is not.
     * Escape still leaves.
     */
    public void renderWaiting(String connection) {
        hud.begin();
        hud.line(0, connection);
        hud.line(hud.rows() - 1, "ESC back to the menu");
        hud.end();
    }

    /**
     * Health, or what it is doing instead of having any. Dying and respawning is invisible without
     * this - the commander simply vanishes for three seconds and reappears somewhere else.
     */
    private static String commanderState(Simulation simulation, int slot) {
        ImmutableArray<Entity> commanders = simulation
                .engine()
                .getEntitiesFor(Family.all(Commander.class, Health.class).get());
        Entity commander = Players.inSlot(commanders, slot);
        if (commander == null) {
            return "dead, respawning";
        }
        Health health = commander.getComponent(Health.class);
        Invulnerable protection = commander.getComponent(Invulnerable.class);
        Weapon weapon = commander.getComponent(Weapon.class);
        String state =
                Math.round(health.current) + "/" + Math.round(health.max) + (weapon == null ? "" : "   " + weapon.kind);
        if (simulation.commanderInvulnerable()) {
            return state + "   GOD MODE";
        }
        if (protection != null && protection.active()) {
            return state + "   invulnerable";
        }
        return state;
    }

    private static int count(Simulation simulation, Class<? extends Component> marker) {
        return simulation.engine().getEntitiesFor(Family.all(marker).get()).size();
    }
}
