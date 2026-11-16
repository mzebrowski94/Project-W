package pl.mzebrows.pwt.sim.system.squad;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.collision.SpatialGrid;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Whole;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Sight;

/**
 * What a trooper shoots at, if anything: the fight half of {@link TrooperMindSystem}'s cascade, which
 * beats every other half.
 *
 * <p>Holds the tick's count of men already on each target, so the squad spreads its fire; rebuilt at
 * the start of every tick by {@link #beginTick()}, which also files every man on his feet, so a man never fires
 * down a line a mate fouls (G0084).
 */
final class TrooperFight {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Enemy> ENEMY = ComponentMapper.getFor(Enemy.class);
    private static final ComponentMapper<Handle> HANDLE = ComponentMapper.getFor(Handle.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    /**
     * Where across a fouled target he tries, middle first, as fractions of how far off it he may aim (G0084): the
     * order he tries them in, not a number to tune - how far is {@code aimAcross}.
     */
    private static final float[] ACROSS = {0f, 0.5f, -0.5f, 1f, -1f};

    private final Solid map;
    private final float threatBonus;
    private final float sharedTargetPenalty;
    private final float closeInFactor;
    private final float aimAcross;
    private final float widestMate;

    /** The chance, keyed per man and alien, that he notices that alien behind him from his whole sight (G0086). */
    private final float noticeBehind;

    /** The level's seed, which that chance is keyed from. */
    private final long levelSeed;

    /** Every man on his feet on the player's side, filed once a tick: whose edge a shot must clear. */
    private final SpatialGrid mates;

    @NotState("scratch refilled by every query of the mates")
    private final Array<Entity> nearby = new Array<>();

    /** Scratch: where the last open line asked for was aimed. */
    @NotState("scratch written and read within one trooper's choice")
    private float aimX;

    @NotState("scratch written and read within one trooper's choice")
    private float aimY;

    /** Scratch: whether the target he is keeping on is still on the field, this choice. */
    @NotState("scratch written and read within one trooper's choice")
    private boolean keptAlive;

    /** Scratch: whether something he could take is inside his all-round range, this choice. */
    @NotState("scratch written and read within one trooper's choice")
    private boolean pressed;

    /** Scratch: whether the target just chosen could only be had down a fouled line. */
    @NotState("scratch written and read within one trooper's choice")
    private boolean chosenFouled;

    /** Scratch: how many men are shooting at each enemy this tick, parallel to the family. */
    @NotState("scratch rebuilt every tick before any trooper chooses a target")
    private int[] shooters = new int[0];

    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> ours = new ImmutableArray<>(new Array<>());

    /** The boulders, which stop a shot though they hide nothing (G0015). */
    private ImmutableArray<Entity> obstacles = new ImmutableArray<>(new Array<>());

    TrooperFight(SimConfig config, Solid map) {
        this.map = map;
        this.threatBonus = config.trooper().threatBonus();
        this.sharedTargetPenalty = config.trooper().sharedTargetPenalty();
        this.closeInFactor = config.trooper().closeInFactor();
        this.aimAcross = config.trooper().aimAcross();
        this.widestMate = Math.max(config.trooper().radius(), config.commander().radius());
        this.noticeBehind = Whole.fraction(config.trooper().noticeBehindPercent());
        this.levelSeed = config.seed();
        this.mates = new SpatialGrid(map.map().worldWidth(), map.map().worldHeight(), config.largestRadius() * 2f);
    }

    /** Takes the views it reads from the engine, once, as its system is added. */
    void bind(Engine engine) {
        enemies = engine.getEntitiesFor(Family.all(Enemy.class, Position.class).get());
        obstacles = engine.getEntitiesFor(
                Family.all(Obstacle.class, Position.class, Body.class).get());
        ours = engine.getEntitiesFor(Life.onTheField());
    }

    /** Forgets who was shooting at what last tick. */
    void beginTick() {
        if (shooters.length < enemies.size()) {
            shooters = new int[enemies.size()];
        }
        for (int e = 0; e < enemies.size(); e++) {
            shooters[e] = 0;
        }
        mates.clear();
        for (int m = 0; m < ours.size(); m++) {
            Entity man = ours.get(m);
            if (Life.down(man)) {
                // A man down fouls nothing: the shot goes over him.
                continue;
            }
            Position where = POSITION.get(man);
            mates.insert(man, where.x, where.y);
        }
    }

    /**
     * Picks a target and heads for it, or reports that there is nothing worth shooting.
     *
     * <p>{@code shotWidth} is how far either side of the line his shot reaches, for {@link Sight#clearShot};
     * {@code reach} is how far his shot flies before it goes out, zero for until it hits something; and
     * {@code staysPut} is whether he is standing on ground he was told to hold or has taken.
     */
    boolean engages(Trooper trooper, long man, Position at, float shotWidth, float reach, boolean staysPut) {
        boolean wasClosing = trooper.mode == Trooper.Mode.FIGHT && trooper.closing;
        int quarry = enemyFor(trooper, man, at, shotWidth);
        // A nest he is keeping on stays his until it dies or something presses him (G0085): fouled or out of his
        // reach for a while, he fights what else he can and comes back to it.
        boolean stillKeeping = keptAlive && !pressed;
        if (quarry < 0) {
            trooper.closing = false;
            trooper.keeping = stillKeeping ? trooper.keeping : Handles.NOBODY;
            trooper.fouled = false;
            return false;
        }
        Entity target = enemies.get(quarry);
        if (!stillKeeping) {
            trooper.keeping = ENEMY.get(target).kept && HANDLE.has(target) ? Handles.of(target) : Handles.NOBODY;
        }
        Position where = POSITION.get(target);
        shooters[quarry]++;
        trooper.mode = Trooper.Mode.FIGHT;
        trooper.closing = !staysPut && outOfReach(at, where, reach, wasClosing);
        trooper.fouled = chosenFouled;
        trooper.headFor(aimX, aimY);
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
     * starting costs every declared test (G0015), staying the longer hold range and a clear line. Both
     * ranges are stretched by what the target {@code standsOut} by, so the hysteresis holds for a nest too.
     * <strong>Both need a clear shot</strong>: a boulder hides nothing but eats every round fired at
     * what stands behind it.
     *
     * <p><strong>The one it would most rather shoot</strong>, as an effective distance: {@code
     * threatBonus} nearer for something coming for somebody, {@code sharedTargetPenalty} further per man
     * already on it (threat ordering, Tremblay et al. 2014); a target that is a {@code threat} whatever it does - a
     * nest - counts as one. Re-resolved every tick (G0010); ties go to the first spawned.
     *
     * <p><strong>A kept target outranks the choice</strong> (G0085): the one he is keeping on is his whenever he can
     * take it - in reach, on a clear shot, down an open line - unless something he could take instead is inside his
     * all-round range. Whether it is still alive, and whether he was pressed, is left in {@link #keptAlive} and
     * {@link #pressed} for {@link #engages}.
     *
     * <p><strong>Never down a fouled line</strong> (G0084): a target is taken down the first open line across it; one
     * he has only fouled lines to is kept back, and chosen - to hold his fire on - only when there is no other.
     */
    private int enemyFor(Trooper trooper, long man, Position at, float shotWidth) {
        boolean alreadyFighting = trooper.mode == Trooper.Mode.FIGHT;
        boolean lookingAllRound = alreadyFighting || trooper.alertRemaining > 0;
        float range = alreadyFighting ? trooper.holdRange : trooper.sightRange;
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        float bestAimX = 0f;
        float bestAimY = 0f;
        int fallback = -1;
        float fallbackScore = Float.MAX_VALUE;
        int kept = -1;
        float keptAimX = 0f;
        float keptAimY = 0f;
        keptAlive = false;
        pressed = false;
        float allRoundSquared = trooper.allRoundRange * trooper.allRoundRange;
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (Life.dying(enemy) || Drivers.burrowed(enemy)) {
                // A body is not a target, nor a worm under the ground (G0096).
                continue;
            }
            boolean isKept =
                    trooper.keeping != Handles.NOBODY && HANDLE.has(enemy) && Handles.of(enemy) == trooper.keeping;
            keptAlive |= isKept;
            Position where = POSITION.get(enemy);
            float dx = where.x - at.x;
            float dy = where.y - at.y;
            float distanceSquared = dx * dx + dy * dy;
            Enemy asTarget = ENEMY.get(enemy);
            // A nest stands out: picked out, and held, from further off than anything else.
            float reach = range + asTarget.standsOut;
            if (distanceSquared > reach * reach) {
                continue;
            }
            boolean close = !isKept && distanceSquared <= allRoundSquared;
            float score = (float) Math.sqrt(distanceSquared) + sharedTargetPenalty * shooters[i];
            if (asTarget.threat || (PERCEPTION.has(enemy) && PERCEPTION.get(enemy).aggro)) {
                score -= threatBonus;
            }
            if (!isKept && !close && score >= bestScore && (best >= 0 || score >= fallbackScore)) {
                continue;
            }
            if (!inSight(trooper, man, at, enemy, where, reach, lookingAllRound)
                    || !Sight.clearShot(obstacles, at.x, at.y, where.x, where.y, shotWidth)) {
                // Seen or not, a shot a boulder would eat is not worth firing.
                continue;
            }
            float radius = BODY.has(enemy) ? BODY.get(enemy).radius : 0f;
            boolean open = openLine(at, where, radius, shotWidth);
            pressed |= close && open;
            if (isKept && open) {
                kept = i;
                keptAimX = aimX;
                keptAimY = aimY;
            }
            if (open && score < bestScore) {
                bestScore = score;
                best = i;
                bestAimX = aimX;
                bestAimY = aimY;
            } else if (!open && score < fallbackScore) {
                fallbackScore = score;
                fallback = i;
            }
        }
        chosenFouled = false;
        if (kept >= 0 && !pressed) {
            aimX = keptAimX;
            aimY = keptAimY;
            return kept;
        }
        if (best >= 0) {
            aimX = bestAimX;
            aimY = bestAimY;
            return best;
        }
        if (fallback >= 0) {
            // Every line he has is fouled: he stays on the best of them and holds his fire (G0084).
            Position where = POSITION.get(enemies.get(fallback));
            aimX = where.x;
            aimY = where.y;
            chosenFouled = true;
        }
        return fallback;
    }

    /**
     * Whether some line from him across this target is clear of every mate on his feet, and if so where it is aimed,
     * in {@link #aimX} and {@link #aimY} (G0084). Middle first, then out across it to {@code aimAcross} of its radius.
     *
     * <p>The mates near enough to matter are gathered once, from the {@link SpatialGrid}, and every line is asked of
     * that handful.
     */
    private boolean openLine(Position at, Position target, float targetRadius, float shotWidth) {
        float dx = target.x - at.x;
        float dy = target.y - at.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0f) {
            aimX = target.x;
            aimY = target.y;
            return true;
        }
        float half = length * 0.5f;
        mates.near(at.x + dx * 0.5f, at.y + dy * 0.5f, half + targetRadius + widestMate + shotWidth, nearby);
        float acrossX = -dy / length;
        float acrossY = dx / length;
        float furthest = targetRadius * aimAcross;
        for (int step = 0; step < ACROSS.length; step++) {
            float toX = target.x + acrossX * furthest * ACROSS[step];
            float toY = target.y + acrossY * furthest * ACROSS[step];
            if (!fouled(at, toX, toY, targetRadius, shotWidth)) {
                aimX = toX;
                aimY = toY;
                return true;
            }
            if (furthest <= 0f) {
                break;
            }
        }
        return false;
    }

    /**
     * Whether one of the mates {@link #openLine} gathered stands across the line from him to this point, ahead of him
     * and short of the target's near edge: nearer the line than his own edge and the shot's. A mate behind him or
     * beyond the target fouls nothing.
     */
    private boolean fouled(Position at, float toX, float toY, float targetRadius, float shotWidth) {
        float dx = toX - at.x;
        float dy = toY - at.y;
        float lengthSquared = dx * dx + dy * dy;
        float length = (float) Math.sqrt(lengthSquared);
        float shortOf = length <= 0f ? 0f : Math.max(0f, length - targetRadius) / length;
        float endX = at.x + dx * shortOf;
        float endY = at.y + dy * shortOf;
        for (int i = 0; i < nearby.size; i++) {
            Entity mate = nearby.get(i);
            Position where = POSITION.get(mate);
            if (where == at || (where.x - at.x) * dx + (where.y - at.y) * dy <= 0f) {
                // Himself, or behind him: the shot leaves the other way.
                continue;
            }
            float clear = BODY.get(mate).radius + shotWidth;
            if (Sight.nearLine(at.x, at.y, endX, endY, where.x, where.y, clear)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every test this trooper declares, cheapest first.
     *
     * <p><strong>A fight in progress forgives the arc and never the wall</strong>, and so does a man
     * who has just been hit: his sweep is his own doing, rock is not. So does anything inside his
     * all-round range, which turns him on what has come right up behind him before it hits him.
     */
    private boolean inSight(
            Trooper trooper,
            long man,
            Position at,
            Entity enemy,
            Position target,
            float reach,
            boolean lookingAllRound) {
        return (lookingAllRound
                        || Sight.withinNotice(
                                trooper.lookX,
                                trooper.lookY,
                                at.x,
                                at.y,
                                target.x,
                                target.y,
                                trooper.arcCosine,
                                trooper.allRoundRange,
                                reach,
                                noticeBehind,
                                levelSeed,
                                man,
                                Handles.of(enemy)))
                && Sight.clearLine(map, at.x, at.y, target.x, target.y, trooper.requiresClearLine);
    }
}
