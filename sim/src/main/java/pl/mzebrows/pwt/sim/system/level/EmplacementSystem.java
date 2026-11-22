package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.collision.SpatialGrid;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Emplacement;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Cycle;
import pl.mzebrows.pwt.sim.def.EmplacementDef;
import pl.mzebrows.pwt.sim.def.EmplacementKind;
import pl.mzebrows.pwt.sim.system.combat.Guns;

/**
 * Runs every emplacement's cycle (G0113): awake, it fires one unaimed shot along each compass point of its next step
 * every period, through the one trigger ({@link Guns#volley}), and moves on to the step after; asleep, its clock and its
 * cycle stand still. <strong>No dice</strong>: the cycle was dealt when it was put down, so one emplacement fires the
 * same pattern for the whole level and it can be learnt (pillar 5).
 *
 * <p>One with a wake radius is awake while a man on his feet stands within it, asked of a {@link SpatialGrid} the men
 * are filed in once a tick - and not at all on a tick no emplacement asks. One with none is woken by something else
 * (ticket 09's wardens), and this leaves its {@code awake} alone.
 */
public final class EmplacementSystem extends EntitySystem {

    private static final ComponentMapper<Emplacement> EMPLACEMENT = ComponentMapper.getFor(Emplacement.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    @NotState("scratch: the men filed by where they stand, refilled each tick an emplacement asks")
    private final SpatialGrid grid;

    @NotState("scratch: what the grid hands back for one emplacement")
    private final Array<Entity> nearby = new Array<>(false, 16);

    private final Guns guns;

    private ImmutableArray<Entity> emplacements = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> men = new ImmutableArray<>(new Array<>());

    public EmplacementSystem(SimConfig config, TileMap map, Guns guns) {
        this.guns = guns;
        float widest = config.largestRadius() * 2f;
        for (EmplacementKind kind : EmplacementKind.values()) {
            widest = Math.max(widest, config.emplacement(kind).wakeRadius());
        }
        this.grid = new SpatialGrid(map.worldWidth(), map.worldHeight(), widest);
    }

    @Override
    public void addedToEngine(Engine engine) {
        emplacements = engine.getEntitiesFor(
                Family.all(Emplacement.class, Position.class, Body.class, Weapon.class, Faction.class)
                        .get());
        men = engine.getEntitiesFor(Life.onTheField());
    }

    @Override
    public void update(float deltaTime) {
        boolean filed = false;
        for (int i = 0; i < emplacements.size(); i++) {
            Entity entity = emplacements.get(i);
            Emplacement emplacement = EMPLACEMENT.get(entity);
            if (emplacement.wakeRadius > 0f) {
                if (!filed) {
                    fileTheMen();
                    filed = true;
                }
                emplacement.awake = anyoneWithin(POSITION.get(entity), emplacement.wakeRadius);
            }
            if (!emplacement.awake || emplacement.length <= 0) {
                continue;
            }
            if (emplacement.remainingTicks > 0) {
                emplacement.remainingTicks--;
            }
            if (emplacement.remainingTicks > 0) {
                continue;
            }
            fire(entity, emplacement.nextPoints());
            emplacement.next = (emplacement.next + 1) % emplacement.length;
            emplacement.remainingTicks = emplacement.periodTicks;
        }
    }

    /** One shot along each compass point named, all at once, credited to nobody. */
    private void fire(Entity entity, int points) {
        for (int point = 0; point < Cycle.POINTS; point++) {
            if ((points & (1 << point)) != 0) {
                guns.volley(entity, EmplacementDef.bearingX(point), EmplacementDef.bearingY(point), Tally.NOBODY);
            }
        }
    }

    private void fileTheMen() {
        grid.clear();
        for (int i = 0; i < men.size(); i++) {
            Entity man = men.get(i);
            Position at = POSITION.get(man);
            grid.insert(man, at.x, at.y);
        }
    }

    /** Whether a man on his feet stands within {@code radius} of here: one down or in his throes wakes nothing. */
    private boolean anyoneWithin(Position at, float radius) {
        grid.near(at.x, at.y, radius, nearby);
        for (int i = 0; i < nearby.size; i++) {
            Entity man = nearby.get(i);
            if (Life.down(man)) {
                continue;
            }
            Position where = POSITION.get(man);
            float dx = where.x - at.x;
            float dy = where.y - at.y;
            if (dx * dx + dy * dy <= radius * radius) {
                return true;
            }
        }
        return false;
    }
}
