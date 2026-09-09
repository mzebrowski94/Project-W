package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.FlowField;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Asks every enemy that is hunting the commander to head for him at its own speed, round whatever is
 * in the way.
 *
 * <p>Whether a unit is hunting at all is {@code PerceptionSystem}'s answer, not this system's; a unit
 * that has not noticed him is simply not spoken for here, asks for nothing, and is left to whatever
 * drives an idle unit.
 *
 * <p>Routes come from a {@link FlowField} per strain, rebuilt only when the commander leaves the tile
 * it was pointed at. One grid pass serves every unit of that strain, however many there are, and each
 * field is built for that strain's body radius - which is what lets a grunt take a one-tile crack that
 * a brute has to walk around, without either of them knowing there is a rule about it.
 *
 * <p><strong>The last of the approach is steered straight at him, not by the field.</strong> A field
 * flattens out at its own goal, so once a unit is on the commander's tile it reports nothing useful;
 * that is also the honest answer when a body that size cannot reach him at all, and walking at him and
 * being stopped by rock is the right behaviour for a brute facing a crack he has backed into.
 *
 * <p>It writes a desired velocity rather than a velocity, so what it asks for is one opinion among
 * several rather than the last word. {@code DriveSystem} commits it; a unit this system says nothing
 * about asks for nothing and stands still, which is why there is no halt-everything branch here.
 *
 * <p>Closing the last of the distance is {@code EnemyLeapSystem}'s business, and it runs after
 * {@code DriveSystem} and overwrites the velocity of anything mid-leap outright. Neither system knows
 * about the other.
 *
 * <p>An {@code EntitySystem} rather than an {@code IteratingSystem} because the target is resolved
 * once per tick rather than once per enemy.
 */
@SuppressWarnings("EnumOrdinal")
public final class NavigationSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Strain> STRAIN = ComponentMapper.getFor(Strain.class);

    private static final float EPSILON = 1e-4f;

    private final TileMap map;

    /**
     * One field per strain, indexed by ordinal for the same reason {@code Defs} is: the array is built
     * from the enum's own constants in the process that reads it, so reordering the enum reorders the
     * array with it and nothing is persisted against the numbers.
     */
    private final FlowField[] fields;

    /** Scratch for one direction, so sampling a field costs no allocation per unit per tick. */
    private final float[] direction = new float[2];

    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    public NavigationSystem(SimConfig config, TileMap map) {
        this.map = map;
        this.fields = new FlowField[UnitKind.values().length];
        for (UnitKind kind : UnitKind.values()) {
            fields[kind.ordinal()] = FlowField.over(map, config.unit(kind).radius());
        }
    }

    @Override
    public void addedToEngine(Engine engine) {
        enemies = engine.getEntitiesFor(
                Family.all(Enemy.class, Position.class, Steering.class, Speed.class, Perception.class, Strain.class)
                        .get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        @Nullable Position target = Drivers.livingTarget(commanders);
        if (target == null) {
            return;
        }
        int goalCol = map.colAt(target.x);
        int goalRow = map.rowAt(target.y);
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (Drivers.held(enemy) || !PERCEPTION.get(enemy).aggro) {
                continue;
            }
            Position position = POSITION.get(enemy);
            float speed = SPEED.get(enemy).value;
            if (within(position, target, map.tileSize())) {
                straightAt(enemy, position, target, speed);
                continue;
            }
            FlowField field = fields[STRAIN.get(enemy).kind.ordinal()];
            field.rebuildFor(goalCol, goalRow);
            if (field.directionAt(position.x, position.y, direction)) {
                STEERING.get(enemy).set(direction[0] * speed, direction[1] * speed);
                continue;
            }
            straightAt(enemy, position, target, speed);
        }
    }

    /**
     * Whether the two are close enough that the grid has nothing left to say.
     *
     * <p>Inside a tile a field is not merely useless but wrong: it points at the goal <em>tile</em>,
     * so a unit standing on the commander would be steered at that tile's centre and orbit him a
     * couple of units out. The last tile of an approach is aimed at the man, not at the square he is
     * standing in.
     */
    private static boolean within(Position position, Position target, float range) {
        float toTargetX = target.x - position.x;
        float toTargetY = target.y - position.y;
        return toTargetX * toTargetX + toTargetY * toTargetY <= range * range;
    }

    /** The last of the approach, and what a unit falls back on when the field has nothing to say. */
    private static void straightAt(Entity enemy, Position position, Position target, float speed) {
        float toTargetX = target.x - position.x;
        float toTargetY = target.y - position.y;
        float distance = (float) Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
        if (distance <= EPSILON) {
            return;
        }
        STEERING.get(enemy).set(toTargetX / distance * speed, toTargetY / distance * speed);
    }
}
