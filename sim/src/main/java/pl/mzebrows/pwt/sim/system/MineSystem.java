package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Mine;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Arms a mine when the other side comes near it, counts its fuse down, and lets it die.
 *
 * <p>It lands no damage of its own and spawns no explosion. <strong>A mine explodes by dying</strong>,
 * through the one death reaction everything that explodes uses (G0018) - so this system knows nothing
 * about blasts, and a mine that is shot, caught in somebody else's blast or simply runs out of fuse
 * all end in the same circle.
 *
 * <p><strong>Only something carrying {@code Enemy} arms one.</strong> That is the whole of "a
 * commander does not set one off": not a faction test, which would have made a mine on nobody's side
 * impossible, and not a list of what may step on one - the marker that already says "on the other
 * side and worth shooting" says it here too.
 *
 * <p>A body in its throes arms nothing. A corpse lying across a mine would otherwise set it off
 * seconds after the fight that put it there was over, which reads as the mine misfiring.
 *
 * <p>It walks the mines against the enemies rather than going through the collision grid, exactly as
 * {@code ControlPointSystem} walks the points against everything on the player's side and for the
 * same reason: it is a short list against a long one, asked once a tick, and a level with enough
 * mines on it for that to matter would have a frame-time problem long before it had this one.
 *
 * <p>It runs after the solver, so "near" means where things actually ended up, and immediately
 * before {@code BlastSystem}, so a fuse that runs out this tick goes off in this tick's
 * death-reaction window rather than being owed to the next one.
 */
public final class MineSystem extends EntitySystem {

    private static final ComponentMapper<Mine> MINE = ComponentMapper.getFor(Mine.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    private final Spawner spawner;

    private ImmutableArray<Entity> mines = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());

    public MineSystem(Spawner spawner) {
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        mines = engine.getEntitiesFor(Family.all(Mine.class, Position.class).get());
        enemies = engine.getEntitiesFor(Family.all(Enemy.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = mines.size() - 1; i >= 0; i--) {
            Entity entity = mines.get(i);
            if (entity.isScheduledForRemoval()) {
                continue;
            }
            Mine mine = MINE.get(entity);
            if (!mine.armed) {
                if (anythingHostileNear(POSITION.get(entity), mine.triggerRadius)) {
                    mine.arm();
                }
                continue;
            }
            if (mine.remainingTicks > 0) {
                mine.remainingTicks--;
            }
            if (mine.spent()) {
                spawner.markDead(entity);
            }
        }
    }

    /**
     * Whether anything on the other side is inside the trigger.
     *
     * <p>Measured centre to centre, as a blast is, for the same reason: "your middle is inside the
     * circle" is a rule the player can see being applied, and "your edge clipped it" is one he would
     * have to be told about.
     */
    private boolean anythingHostileNear(Position at, float radius) {
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (Life.dying(enemy)) {
                continue;
            }
            if (Sight.withinRange(at.x, at.y, POSITION.get(enemy).x, POSITION.get(enemy).y, radius)) {
                return true;
            }
        }
        return false;
    }
}
