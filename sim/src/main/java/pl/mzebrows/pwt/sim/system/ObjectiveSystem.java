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
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Post;
import pl.mzebrows.pwt.sim.component.Waves;
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

    /**
     * The post held: there is one, everything the level had to send has been sent, and nothing
     * hostile is left standing.
     *
     * <p>Two of those three are questions about the world and the middle one is not, which is the
     * whole shape of a defence: an empty field means you have won if there is nothing left to come,
     * and means you are between waves if there is. The schedule is level state exactly as the
     * objectives are, so this is still recomputed from scratch every tick and latches nothing.
     *
     * <p><strong>"Nothing hostile standing" is measured against the post's own watch</strong>, and
     * the whole level only when it names none. A level the size of one fight means the same thing
     * either way; a march seven screens long does not, because there are nests still working four
     * biomes back and the ground the level told you to hold is not the ground they are standing on.
     */
    private boolean postHeld(Entity level, boolean cleared) {
        if (posts.size() == 0 || (WAVES.has(level) && !WAVES.get(level).spent())) {
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
     * <p>Only asked of a post that names a watch. One that does not is a level the size of a fight,
     * where the whole field <em>is</em> the ground around the post and the cheaper count above says
     * the same thing.
     */
    private boolean clearAround(Position at, float watch) {
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (DYING.has(enemy) && DYING.get(enemy).begun) {
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
