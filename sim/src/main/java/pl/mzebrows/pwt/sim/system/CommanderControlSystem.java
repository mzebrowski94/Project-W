package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Drag;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Presence;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.GroundDef;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Pace;

/**
 * Turns a commander's intent into movement and gunfire.
 *
 * <p>Movement and aim are read separately and never derived from each other (G0002): the velocity
 * this writes says nothing about where the commander is pointing, and the direction a projectile
 * leaves in says nothing about where the commander is walking.
 *
 * <p>A held commander runs none of this, and his velocity is {@code StunSystem}'s.
 *
 * <p><strong>The leash lives here because this system owns a commander's velocity</strong> (pillar
 * 4): the outward part of a step past {@code leashRadius} from the middle of the commanders is refused
 * exactly. <strong>It never pushes</strong> (pillar 2) - one past the line walks home himself.
 *
 * <p><strong>It holds together the commanders at one machine, and nobody else</strong> (G0056). Players
 * sharing a screen share a camera that has to frame them all; a player at a machine of his own has a
 * camera of his own, and a rope tied to somebody else's screen would only be an invisible wall.
 */
public final class CommanderControlSystem extends IteratingSystem {

    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Drag> DRAG = ComponentMapper.getFor(Drag.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Presence> PRESENCE = ComponentMapper.getFor(Presence.class);

    /** Below this a direction is treated as no direction, rather than normalised into a huge one. */
    private static final float EPSILON = 1e-4f;

    private final Guns guns;

    private final float leashRadius;

    /** The ground he is standing on, and what each kind of it costs him. */
    private final TileMap map;

    private final Defs<Terrain, GroundDef> ground;

    /**
     * The middle of the commanders at each machine, worked out once a tick before any of them is driven:
     * one group per machine, at most one per player, so sized from the capacity (pillar 7).
     */
    @NotState("scratch: recomputed at the start of every update")
    private final int[] groupMachine;

    @NotState("scratch: recomputed at the start of every update")
    private final float[] groupX;

    @NotState("scratch: recomputed at the start of every update")
    private final float[] groupY;

    @NotState("scratch: recomputed at the start of every update")
    private final int[] groupSize;

    @NotState("scratch: recomputed at the start of every update")
    private int groups;

    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

    /** @param capacity how many slots the game holds - the most machines its commanders can sit at */
    public CommanderControlSystem(SimConfig config, TileMap map, Guns guns, int capacity) {
        this.guns = guns;
        super(Family.all(
                        Commander.class,
                        Intent.class,
                        Position.class,
                        Velocity.class,
                        Speed.class,
                        Body.class,
                        Weapon.class,
                        Faction.class)
                .get());
        this.map = map;
        this.ground = config.ground();
        this.leashRadius = config.commander().leashRadius();
        this.groupMachine = new int[capacity];
        this.groupX = new float[capacity];
        this.groupY = new float[capacity];
        this.groupSize = new int[capacity];
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        levels = engine.getEntitiesFor(Family.all(Presence.class).get());
    }

    /**
     * Where the commanders are, before any of them has been driven.
     *
     * <p>Once a tick, so every commander at one machine is leashed to the same place.
     */
    @Override
    public void update(float deltaTime) {
        findCentres();
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (Drivers.held(entity)) {
            return;
        }
        Intent intent = INTENT.get(entity);
        drive(entity, intent);
        leash(entity, deltaTime);
        if (Drivers.handsFull(entity)) {
            return;
        }
        fire(entity, intent);
    }

    private void findCentres() {
        groups = 0;
        ImmutableArray<Entity> commanders = getEntities();
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            int group = groupOf(machineOf(commander));
            if (group < 0) {
                if (groups == groupMachine.length) {
                    throw new IllegalStateException(
                            "commanders at more machines than the game has slots: " + groupMachine.length);
                }
                group = groups++;
                groupMachine[group] = machineOf(commander);
                groupX[group] = 0f;
                groupY[group] = 0f;
                groupSize[group] = 0;
            }
            Position at = POSITION.get(commander);
            groupX[group] += at.x;
            groupY[group] += at.y;
            groupSize[group]++;
        }
        for (int group = 0; group < groups; group++) {
            groupX[group] /= groupSize[group];
            groupY[group] /= groupSize[group];
        }
    }

    /** The machine this commander's player sits at (G0056); 0 for all of them offline. */
    private int machineOf(Entity commander) {
        return levels.size() == 0 ? 0 : PRESENCE.get(levels.first()).machineOf(COMMANDER.get(commander).slot);
    }

    /** Which of this tick's groups gathers this machine's commanders, or -1. */
    private int groupOf(int machine) {
        for (int group = 0; group < groups; group++) {
            if (groupMachine[group] == machine) {
                return group;
            }
        }
        return -1;
    }

    /**
     * Takes off whatever part of this step would carry him past the end of the rope.
     *
     * <p>What survives lands him exactly on the line; the position is never clamped.
     */
    private void leash(Entity entity, float deltaTime) {
        if (leashRadius <= 0f || deltaTime <= 0f) {
            return;
        }
        int group = groupOf(machineOf(entity));
        if (group < 0 || groupSize[group] < 2) {
            return;
        }
        Position at = POSITION.get(entity);
        float outX = at.x - groupX[group];
        float outY = at.y - groupY[group];
        float distance = (float) Math.sqrt(outX * outX + outY * outY);
        if (distance <= EPSILON) {
            return;
        }
        outX /= distance;
        outY /= distance;
        Velocity velocity = VELOCITY.get(entity);
        float outward = velocity.x * outX + velocity.y * outY;
        if (outward <= 0f) {
            return;
        }
        float rope = leashRadius - distance;
        float allowance = rope > 0f ? rope / deltaTime : 0f;
        if (outward <= allowance) {
            return;
        }
        float excess = outward - allowance;
        velocity.set(velocity.x - excess * outX, velocity.y - excess * outY);
    }

    /**
     * A move intent longer than one unit is normalised, so holding two keys is not faster than
     * holding one; a shorter one is kept as-is, so a half-pushed stick moves at half speed.
     *
     * <p>The speed is {@link Pace}d first: his velocity is not committed by {@code DriveSystem}.
     */
    private void drive(Entity entity, Intent intent) {
        Velocity velocity = VELOCITY.get(entity);
        float length = (float) Math.sqrt(intent.moveX * intent.moveX + intent.moveY * intent.moveY);
        if (length <= EPSILON) {
            velocity.set(0f, 0f);
            return;
        }
        float speed = SPEED.get(entity).value * Pace.of(entity, map, ground);
        float scale = length > 1f ? speed / length : speed;
        haul(entity, velocity, intent.moveX * scale, intent.moveY * scale, speed);
    }

    /**
     * Writes the velocity he actually gets, which is the one he asked for unless he is hauling
     * something.
     *
     * <p><strong>Backing away, and only backing away</strong>: the part towards the load is dropped,
     * the sideways part partly kept, and the rest slowed. Here, not in {@code DragSystem}, because this
     * system owns his velocity.
     */
    private static void haul(Entity entity, Velocity velocity, float wantX, float wantY, float speed) {
        if (!Drivers.handsFull(entity)) {
            velocity.set(wantX, wantY);
            return;
        }
        Drag drag = DRAG.get(entity);
        float awayX = -drag.towardLoadX;
        float awayY = -drag.towardLoadY;
        float along = wantX * awayX + wantY * awayY;
        // Split first, clamp second, or a step straight at the load survives as "sideways".
        float sidewaysX = (wantX - along * awayX) * drag.lateralFactor;
        float sidewaysY = (wantY - along * awayY) * drag.lateralFactor;
        if (along < 0f) {
            along = 0f;
        }
        float x = along * awayX + sidewaysX;
        float y = along * awayY + sidewaysY;
        float cap = speed * drag.speedFactor;
        float length = (float) Math.sqrt(x * x + y * y);
        if (length > cap && length > EPSILON) {
            x = x / length * cap;
            y = y / length * cap;
        }
        velocity.set(x, y);
    }

    /** The cooldown itself is counted down by {@code CooldownSystem}, before this runs. */
    private void fire(Entity entity, Intent intent) {
        if (!intent.firing || !guns.ready(entity)) {
            return;
        }
        Position position = POSITION.get(entity);
        float toAimX = intent.aimX - position.x;
        float toAimY = intent.aimY - position.y;
        float distance = (float) Math.sqrt(toAimX * toAimX + toAimY * toAimY);
        if (distance <= EPSILON) {
            return;
        }
        guns.pull(entity, toAimX / distance, toAimY / distance, Players.credit(entity));
    }
}
