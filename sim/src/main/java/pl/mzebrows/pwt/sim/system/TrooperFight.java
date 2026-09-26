package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
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
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);

    private final TileMap map;
    private final float threatBonus;
    private final float sharedTargetPenalty;

    /** Scratch: how many men are shooting at each enemy this tick, parallel to the family. */
    @NotState("scratch rebuilt every tick before any trooper chooses a target")
    private int[] shooters = new int[0];

    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());

    TrooperFight(SimConfig config, TileMap map) {
        this.map = map;
        this.threatBonus = config.trooper().threatBonus();
        this.sharedTargetPenalty = config.trooper().sharedTargetPenalty();
    }

    /** Takes the views it reads from the engine, once, as its system is added. */
    void bind(Engine engine) {
        enemies = engine.getEntitiesFor(Family.all(Enemy.class, Position.class).get());
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

    /** Picks a target and heads for it, or reports that there is nothing worth shooting. */
    boolean engages(Trooper trooper, Position at) {
        int quarry = enemyFor(trooper, at);
        if (quarry < 0) {
            return false;
        }
        Position where = POSITION.get(enemies.get(quarry));
        shooters[quarry]++;
        trooper.mode = Trooper.Mode.FIGHT;
        trooper.headFor(where.x, where.y);
        return true;
    }

    /**
     * The enemy this trooper is dealing with, as its index in the family, or {@code -1} if it has
     * nothing to shoot at.
     *
     * <p><strong>Starting a fight and staying in one are different questions</strong>, and that
     * asymmetry is the whole of the hysteresis. Noticing something costs it every test the trooper
     * declares - range, arc, a clear line (G0015). Staying on it costs range alone, out to the longer
     * hold range: a trooper that lost its target every time it turned its head, or every time the
     * thing stepped behind a rock, would stutter between fighting and walking on at exactly the
     * distance a fight actually happens at. A man who has just been hit forgives the arc too: he is
     * looking all round for what did it.
     *
     * <p><strong>Of what it can see, the one it would most rather shoot</strong>, as a distance it
     * would rather it were. Something coming for somebody counts as {@code threatBonus} nearer than
     * something that is not - a nest, an alien that has noticed nobody - so a man shoots the grunt
     * walking at him before the nest behind it. And every man already shooting at a thing this tick
     * makes it {@code sharedTargetPenalty} further, so a squad spreads its fire instead of all emptying
     * into the nearest one. Threat ordering beat nearest-first for AI companions in the one study that
     * compared them (Tremblay et al., 2014); this is the cheapest form of it that a player can read.
     *
     * <p>Re-resolved every tick and never held between them (G0010). What survives a tick is the
     * mode, not the thing - so an enemy reaped between ticks cannot come back as whatever was built
     * from the recycled instance. The count of who is shooting what is scratch, rebuilt every tick.
     *
     * <p>Ties go to whichever was spawned first, because the family is in insertion order and the
     * comparison is strict.
     */
    private int enemyFor(Trooper trooper, Position at) {
        boolean alreadyFighting = trooper.mode == Trooper.Mode.FIGHT;
        boolean lookingAllRound = alreadyFighting || trooper.alertRemaining > 0;
        float range = alreadyFighting ? trooper.holdRange : trooper.sightRange;
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (DYING.has(enemy) && DYING.get(enemy).begun) {
                // A body is not a target. Standing over one shooting is how a squad misses the next
                // thing coming at it.
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
            if (!inSight(trooper, at, where, lookingAllRound)) {
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
     * <p><strong>A fight in progress forgives the arc and never the wall</strong> - and so does a man
     * who has just been hit, for as long as he is looking round for what did it. The two are not
     * the same kind of hysteresis: where a man is looking is his own doing and changes twice a
     * second as he sweeps, so dropping a target the moment his head passes it would make a trooper
     * unable to hold a fight at all. Rock is not his doing and does not move. Forgiving both - which
     * is what this did - is a squad standing in the open firing into a wall the thing it was
     * shooting at stepped behind, which is the one thing a trooper must never look like doing.
     */
    private boolean inSight(Trooper trooper, Position at, Position target, boolean lookingAllRound) {
        return (lookingAllRound
                        || Sight.withinArc(
                                trooper.lookX, trooper.lookY, at.x, at.y, target.x, target.y, trooper.arcCosine))
                && Sight.clearLine(map, at.x, at.y, target.x, target.y, trooper.requiresClearLine);
    }
}
