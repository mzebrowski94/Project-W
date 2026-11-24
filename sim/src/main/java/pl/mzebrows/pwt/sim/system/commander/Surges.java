package pl.mzebrows.pwt.sim.system.commander;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.collision.SpatialGrid;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.query.Sight;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * A Rocketman's surge (G0108): on the tick a dash launches, every alien whose middle is within his reach, along a
 * line rock and a shut door leave clear, takes a harmless blow that stuns it - through {@link Damage}, so a shield it
 * has breaks and it is provoked towards him, as by any blow (G0057). Never a man, a commander, anything neutral or a
 * door. The reach is folded from what he holds, and none is none.
 *
 * <p>The second non-lethal radial effect in the game, and deliberately no blast (G0018): a blast is on nobody's side
 * and fatal, this is the aliens' alone and harms nothing.
 *
 * <p>A sentry going off stuns the aliens round it the same way, through {@link #stunRound} (G0129).
 *
 * <p>The aliens are filed in a {@link SpatialGrid} at most once a tick, on the first surge of it - most ticks none goes
 * off, and those cost nothing. One per simulation, handed to {@code CommanderControlSystem}, which launches the dash.
 */
public final class Surges {

    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    private final Modifiers modifiers;

    private final Solid solid;

    private final Spawner spawner;

    /** How long a surge stuns what it reaches, in whole ticks, before the stun-duration fold. */
    private final int stunTicks;

    @NotState("scratch: the aliens filed by where they stand, refilled on the first surge of a tick")
    private final SpatialGrid grid;

    @NotState("scratch: what the grid hands back for one surge")
    private final Array<Entity> nearby = new Array<>(false, 16);

    @NotState("scratch: whether this tick's aliens are in the grid yet")
    private boolean filed;

    private ImmutableArray<Entity> aliens = new ImmutableArray<>(new Array<>());

    public Surges(SimConfig config, Solid solid, Modifiers modifiers, Spawner spawner) {
        this.modifiers = modifiers;
        this.solid = solid;
        this.spawner = spawner;
        this.stunTicks = Ticks.of(config.commander().dash().stunSeconds(), config.tickSeconds());
        this.grid = new SpatialGrid(solid.map().worldWidth(), solid.map().worldHeight(), config.largestRadius() * 2f);
    }

    /** Finds what a surge may reach, once, as its system is added to the engine. */
    void bind(Engine engine) {
        aliens = engine.getEntitiesFor(Family.all(Faction.class, Damage.class, Position.class)
                .one(Health.class, Vitality.class)
                .exclude(Door.class)
                .get());
    }

    /** A new tick: whatever was filed on the last one is stale. */
    void tick() {
        filed = false;
    }

    /**
     * Sets off his surge where he stands, if he holds one: every alien within its reach stunned, and the ring it is
     * drawn as left there.
     */
    void goOff(Entity commander, float x, float y) {
        int reach = modifiers.surgeReach(commander);
        if (reach <= 0) {
            return;
        }
        stunRound(x, y, reach, stunTicks, Players.credit(commander));
        spawner.spawnSurge(x, y, reach);
    }

    /**
     * Every alien whose middle is within {@code reach} of here, along a line rock and a shut door leave clear, takes a
     * harmless blow that stuns it for {@code ticks} - a surge's, and a sentry's as it goes off (G0129). Never a man.
     */
    void stunRound(float x, float y, float reach, int ticks, int credit) {
        fileTheAliens();
        grid.near(x, y, reach, nearby);
        for (int i = 0; i < nearby.size; i++) {
            Entity alien = nearby.get(i);
            Position at = POSITION.get(alien);
            if (!Sight.withinRange(x, y, at.x, at.y, reach) || !Sight.clearLine(solid, x, y, at.x, at.y, true)) {
                continue;
            }
            Damage owed = DAMAGE.get(alien);
            owed.landStunning(ticks, credit);
            owed.cameFrom(x, y);
        }
    }

    /** Every alien on the field into the grid, once a tick, on the first surge that needs them. */
    private void fileTheAliens() {
        if (filed) {
            return;
        }
        filed = true;
        grid.clear();
        for (int i = 0; i < aliens.size(); i++) {
            Entity entity = aliens.get(i);
            if (FACTION.get(entity).side != Side.ALIEN) {
                continue;
            }
            Position at = POSITION.get(entity);
            grid.insert(entity, at.x, at.y);
        }
    }
}
