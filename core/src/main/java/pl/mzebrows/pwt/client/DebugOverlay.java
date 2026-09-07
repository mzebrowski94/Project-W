package pl.mzebrows.pwt.client;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import lombok.RequiredArgsConstructor;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.scenario.Scenario;

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
    private final Scenario scenario;

    /** Draws the overlay. Never called from inside the simulation, so it may allocate freely. */
    public void render(Simulation simulation, boolean paused) {
        hud.begin();
        hud.line(0, scenario.name() + (paused ? "   [PAUSED]" : ""));
        hud.line(1, "fps " + Gdx.graphics.getFramesPerSecond() + "    tick " + simulation.tick());
        hud.line(
                2,
                "commanders " + count(simulation, Commander.class) + "    enemies "
                        + count(simulation, Enemy.class) + "    projectiles "
                        + count(simulation, Projectile.class) + "    obstacles "
                        + count(simulation, Obstacle.class));
        hud.line(3, "entities " + simulation.engine().getEntities().size());
        hud.line(hud.rows() - 1, "P pause   . step   R restart   F1 overlay   ESC menu");
        hud.end();
    }

    private static int count(Simulation simulation, Class<? extends Component> marker) {
        return simulation.engine().getEntitiesFor(Family.all(marker).get()).size();
    }
}
