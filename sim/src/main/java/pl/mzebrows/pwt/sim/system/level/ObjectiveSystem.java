package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Lair;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Post;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.def.Objective;

/**
 * Answers, once a tick, whether the level's objectives are met - and the first tick they all are, that
 * the level is won (G0066).
 *
 * <p>Every objective is <strong>recomputed from the world</strong>, never latched; the win is, through
 * {@link Level#decide}, unless the level was lost first.
 */
public final class ObjectiveSystem extends EntitySystem {

    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Lair> LAIR = ComponentMapper.getFor(Lair.class);
    private static final ComponentMapper<Waves> WAVES = ComponentMapper.getFor(Waves.class);
    private static final ComponentMapper<Post> POST = ComponentMapper.getFor(Post.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> posts = new ImmutableArray<>(new Array<>());

    @Override
    public void addedToEngine(Engine engine) {
        levels = engine.getEntitiesFor(Family.all(Level.class).get());
        enemies = engine.getEntitiesFor(Family.all(Enemy.class, Position.class).get());
        points = engine.getEntitiesFor(Family.all(ControlPoint.class).get());
        posts = engine.getEntitiesFor(Family.all(Post.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (levels.size() == 0) {
            return;
        }
        boolean cleared = nothingLeftStanding();
        boolean taken = everyPointTaken();
        for (int i = 0; i < levels.size(); i++) {
            Entity entity = levels.get(i);
            Level level = LEVEL.get(entity);
            level.setMet(Objective.CLEAR_ENEMIES, cleared);
            level.setMet(Objective.CAPTURE_POINTS, taken);
            level.setMet(Objective.HOLD_POINT, postHeld(entity, cleared));
            if (level.complete()) {
                level.decide(true);
            }
        }
    }

    /**
     * Nothing hostile still on its feet.
     *
     * <p>A body in its throes does not count against it.
     */
    private boolean nothingLeftStanding() {
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            // Dead, not merely held: a stunned enemy is still a problem.
            if (!Life.dying(enemy)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The post held: there is one, everything the level had to send has been sent - every wave, and
     * whatever a lair holds (G0048) - and nothing hostile is left standing.
     *
     * <p>Recomputed every tick. <strong>"Nothing hostile standing" is measured against the post's own
     * watch</strong>, and the whole level only when it names none.
     */
    private boolean postHeld(Entity level, boolean cleared) {
        if (posts.size() == 0
                || (WAVES.has(level) && !WAVES.get(level).spent())
                || (LAIR.has(level) && LAIR.get(level).waiting())) {
            return false;
        }
        for (int i = 0; i < posts.size(); i++) {
            Entity post = posts.get(i);
            float watch = POST.get(post).watchRadius;
            if (watch <= 0f ? !cleared : !clearAround(POSITION.get(post), watch)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Nothing hostile still on its feet within the ground this post is responsible for.
     *
     * <p>Only asked of a post that names a watch.
     */
    private boolean clearAround(Position at, float watch) {
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (Life.dying(enemy)) {
                continue;
            }
            Position where = POSITION.get(enemy);
            float dx = where.x - at.x;
            float dy = where.y - at.y;
            if (dx * dx + dy * dy <= watch * watch) {
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
