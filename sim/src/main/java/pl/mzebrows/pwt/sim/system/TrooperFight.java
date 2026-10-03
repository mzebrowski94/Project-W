package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;

/**
 * What a trooper shoots at, if anything: the fight half of {@link TrooperMindSystem}'s cascade, which
 * beats every other half.
 *
 * <p>Holds the tick's count of men already on each target, so the squad spreads its fire; rebuilt at
 * the start of every tick by {@link #beginTick()}.
 */
final class TrooperFight {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);

    private final TileMap map;
    private final float threatBonus;
    private final float sharedTargetPenalty;
    private final float closeInFactor;

    /** Scratch: how many men are shooting at each enemy this tick, parallel to the family. */
    @NotState("scratch rebuilt every tick before any trooper chooses a target")
    private int[] shooters = new int[0];

    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());

    /** The boulders, which stop a shot though they hide nothing (G0015). */
    private ImmutableArray<Entity> obstacles = new ImmutableArray<>(new Array<>());

    TrooperFight(SimConfig config, TileMap map) {
        this.map = map;
        this.threatBonus = config.trooper().threatBonus();
        this.sharedTargetPenalty = config.trooper().sharedTargetPenalty();
        this.closeInFactor = config.trooper().closeInFactor();
    }

    /** Takes the views it reads from the engine, once, as its system is added. */
    void bind(Engine engine) {
        enemies = engine.getEntitiesFor(Family.all(Enemy.class, Position.class).get());
        obstacles = engine.getEntitiesFor(
                Family.all(Obstacle.class, Position.class, Body.class).get());
    }

    /** Forgets who was shooting at what last tick. */
    void beginTick() {
        if (shooters.length < enemies.size()) {
            shooters = new int[enemies.size()];
        }
        for (int e = 0; e < enemies.size(); e++) {
            shooters[e] = 0;
        }
    }

    /**
     * Picks a target and heads for it, or reports that there is nothing worth shooting.
     *
     * <p>{@code shotWidth} is how far either side of the line his shot reaches, for {@link Sight#clearShot};
     * {@code reach} is how far his shot flies before it goes out, zero for until it hits something; and
     * {@code staysPut} is whether he is standing on ground he was told to hold or has taken.
     */
    boolean engages(Trooper trooper, Position at, float shotWidth, float reach, boolean staysPut) {
        boolean wasClosing = trooper.mode == Trooper.Mode.FIGHT && trooper.closing;
        int quarry = enemyFor(trooper, at, shotWidth);
        if (quarry < 0) {
            trooper.closing = false;
            return false;
        }
        Position where = POSITION.get(enemies.get(quarry));
        shooters[quarry]++;
        trooper.mode = Trooper.Mode.FIGHT;
        trooper.closing = !staysPut && outOfReach(at, where, reach, wasClosing);
        trooper.headFor(where.x, where.y);
        return true;
    }

    /**
     * Whether his gun's fire would go out short of the target, so he should walk in on it first.
     *
     * <p><strong>Hysteresis</strong>: he sets off once it is past the reach and walks to well inside it,
     * {@code closeInFactor} of it, so a target that steps back half a pace does not start him again.
     * Centre to centre, which leaves the two bodies as margin. A gun with no reach is never short.
     */
    private boolean outOfReach(Position at, Position target, float reach, boolean wasClosing) {
        if (reach <= 0f) {
            return false;
        }
        float stopAt = wasClosing ? reach * closeInFactor : reach;
        return !Sight.withinRange(at.x, at.y, target.x, target.y, stopAt);
    }

    /**
     * The enemy this trooper is dealing with, as its index in the family, or {@code -1} if it has
     * nothing to shoot at.
     *
     * <p><strong>Starting a fight and staying in one are different questions</strong> (the hysteresis):
     * starting costs every declared test (G0015), staying the longer hold range and a clear line.
     * <strong>Both need a clear shot</strong>: a boulder hides nothing but eats every round fired at
     * what stands behind it.
     *
     * <p><strong>The one it would most rather shoot</strong>, as an effective distance: {@code
     * threatBonus} nearer for something coming for somebody, {@code sharedTargetPenalty} further per man
     * already on it (threat ordering, Tremblay et al. 2014). Re-resolved every tick (G0010); ties go to
     * the first spawned.
     */
    private int enemyFor(Trooper trooper, Position at, float shotWidth) {
        boolean alreadyFighting = trooper.mode == Trooper.Mode.FIGHT;
        boolean lookingAllRound = alreadyFighting || trooper.alertRemaining > 0;
        float range = alreadyFighting ? trooper.holdRange : trooper.sightRange;
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (Life.dying(enemy)) {
                // A body is not a target.
                continue;
            }
            Position where = POSITION.get(enemy);
            float dx = where.x - at.x;
            float dy = where.y - at.y;
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared > range * range) {
                continue;
            }
            float score = (float) Math.sqrt(distanceSquared) + sharedTargetPenalty * shooters[i];
            if (PERCEPTION.has(enemy) && PERCEPTION.get(enemy).aggro) {
                score -= threatBonus;
            }
            if (score >= bestScore) {
                continue;
            }
            if (!inSight(trooper, at, where, lookingAllRound)
                    || !Sight.clearShot(obstacles, at.x, at.y, where.x, where.y, shotWidth)) {
                // Seen or not, a shot a boulder would eat is not worth firing.
                continue;
            }
            bestScore = score;
            best = i;
        }
        return best;
    }

    /**
     * Every test this trooper declares, cheapest first.
     *
     * <p><strong>A fight in progress forgives the arc and never the wall</strong>, and so does a man
     * who has just been hit: his sweep is his own doing, rock is not.
     */
    private boolean inSight(Trooper trooper, Position at, Position target, boolean lookingAllRound) {
        return (lookingAllRound
                        || Sight.withinArc(
                                trooper.lookX, trooper.lookY, at.x, at.y, target.x, target.y, trooper.arcCosine))
                && Sight.clearLine(map, at.x, at.y, target.x, target.y, trooper.requiresClearLine);
    }
}
