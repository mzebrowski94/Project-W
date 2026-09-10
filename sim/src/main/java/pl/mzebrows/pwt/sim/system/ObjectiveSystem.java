package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.def.Objective;

/**
 * Answers, once a tick, whether the level's objectives are met.
 *
 * <p>Every objective is <strong>recomputed from the world</strong> rather than latched when it first
 * becomes true. A latch would turn "the map is clear" into "the map was clear once", so a wave
 * arriving after the last kill would leave the level saying it was finished; recomputing costs two
 * counts and cannot drift from what is actually standing on the field.
 *
 * <p><strong>Nothing reads the answer.</strong> That is this stage's boundary, not an oversight: what
 * a completed level does - extract, cut away, send another wave - is an open question, and inventing
 * one here would answer it by accident. The level entity knows; the game does not react yet, and
 * `docs/design/alpha-phase.md` lists that as scaffolding with the question it is waiting on.
 *
 * <p>It runs after everything that can kill something and after {@code ControlPointSystem}, so the
 * answer is about the world at the end of the tick rather than the middle of it.
 */
public final class ObjectiveSystem extends EntitySystem {

    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);

    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());

    @Override
    public void addedToEngine(Engine engine) {
        levels = engine.getEntitiesFor(Family.all(Level.class).get());
        enemies = engine.getEntitiesFor(Family.all(Enemy.class).get());
        points = engine.getEntitiesFor(Family.all(ControlPoint.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (levels.size() == 0) {
            return;
        }
        boolean cleared = nothingLeftStanding();
        boolean taken = everyPointTaken();
        for (int i = 0; i < levels.size(); i++) {
            Level level = LEVEL.get(levels.get(i));
            level.setMet(Objective.CLEAR_ENEMIES, cleared);
            level.setMet(Objective.CAPTURE_POINTS, taken);
        }
    }

    /**
     * Nothing hostile still on its feet.
     *
     * <p>A body in its throes does not count against it. The throes are a second of animation after a
     * unit is already dead, and a level that refused to acknowledge its last kill for that second
     * would read as the objective sticking.
     */
    private boolean nothingLeftStanding() {
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            // Dead, not merely held. A stunned enemy is very much still a problem, and asking
            // Drivers.held here would have declared the map clear every time the commander landed a
            // blow on the last one.
            if (!DYING.has(enemy) || !DYING.get(enemy).begun) {
                return false;
            }
        }
        return true;
    }

    /** Every point taken - and a map with no points on it has not taken them all. */
    private boolean everyPointTaken() {
        if (points.size() == 0) {
            return false;
        }
        for (int i = 0; i < points.size(); i++) {
            if (!POINT.get(points.get(i)).captured) {
                return false;
            }
        }
        return true;
    }
}
