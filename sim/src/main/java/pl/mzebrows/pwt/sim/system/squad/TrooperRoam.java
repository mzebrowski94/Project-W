package pl.mzebrows.pwt.sim.system.squad;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Post;
import pl.mzebrows.pwt.sim.component.Trooper;

/** Somewhere to go when there is nothing else to do: the bottom of {@link TrooperMindSystem}'s cascade. */
final class TrooperRoam {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    /** Enough tries to find open ground on any map worth playing; a few more would not help. */
    private static final int ROAM_ATTEMPTS = 16;

    /** How many times a push out from the post is halved before it gives up and roams instead. */
    private static final int PUSH_ATTEMPTS = 5;

    /** Below this there is no direction away from the post worth normalising. */
    private static final float EPSILON = 1e-4f;

    private final TileMap map;
    private final Rng rng;
    private final float bodyRadius;
    private final float arriveRadius;
    private final float pushDistance;
    private final float patchRadius;

    private ImmutableArray<Entity> posts = new ImmutableArray<>(new Array<>());

    TrooperRoam(SimConfig config, TileMap map, Rng rng) {
        this.map = map;
        this.rng = rng;
        this.bodyRadius = config.trooper().radius();
        this.arriveRadius = config.trooper().arriveRadius();
        this.pushDistance = config.trooper().forwardDistance();
        this.patchRadius = config.trooper().patchRadius();
    }

    /** Takes the views it reads from the engine, once, as its system is added. */
    void bind(Engine engine) {
        posts = engine.getEntitiesFor(Family.all(Post.class, Position.class).get());
    }

    /** Explores: picks somewhere new once it has got where it was going, or had nowhere to go. */
    void explores(Trooper trooper, Position at) {
        trooper.mode = Trooper.Mode.EXPLORE;
        if (!trooper.hasTarget || arrived(at, trooper)) {
            roam(trooper, at);
        }
    }

    /**
     * Wanders round a place rather than across the level, off the circle of {@code keepOff} round it: the capture
     * crowd's spare man with no next point to go on to (G0086). A fresh spot the moment he takes it up, so a walk
     * he had begun into that circle is not finished.
     */
    void exploresAround(Trooper trooper, Position at, float x, float y, float keepOff) {
        boolean takenUp = trooper.mode != Trooper.Mode.EXPLORE;
        trooper.mode = Trooper.Mode.EXPLORE;
        if (takenUp || !trooper.hasTarget || arrived(at, trooper)) {
            roamsAround(trooper, x, y, keepOff);
        }
    }

    /** Somewhere on the map a trooper can actually stand, drawn from the squad's own stream. */
    private void roam(Trooper trooper, Position at) {
        if (trooper.pushingForward() && pushesOutFromThePost(trooper, at)) {
            return;
        }
        if (posts.size() > 0) {
            Position post = POSITION.get(posts.first());
            roamsAround(trooper, post.x, post.y, 0f);
            return;
        }
        for (int attempt = 0; attempt < ROAM_ATTEMPTS; attempt++) {
            float x = rng.nextFloat(0f, map.worldWidth());
            float y = rng.nextFloat(0f, map.worldHeight());
            if (map.isClear(x, y, bodyRadius)) {
                trooper.headFor(x, y);
                return;
            }
        }
        // A map with almost nowhere to stand. Better to hold still than to walk into rock.
        trooper.clearTarget();
    }

    /**
     * Pushes the front line out: on a level that names a post, "forward" means away from the thing
     * you were told to hold.
     *
     * <p>Everywhere else forward is a burst of pace on the way to an objective, and it needs no
     * direction of its own because there is one to walk to. A defence has none - there is nothing to
     * take - so without this the order would spend itself sending men to a seeded random corner,
     * which is the opposite of a line moving.
     *
     * <p>It walks the target back towards the man until it lands on ground he can stand on, rather
     * than giving up at the first refusal: a push that stopped because the full distance happened to
     * end in rock would read as the order being ignored.
     */
    private boolean pushesOutFromThePost(Trooper trooper, Position at) {
        if (posts.size() == 0) {
            return false;
        }
        Position post = POSITION.get(posts.first());
        float awayX = at.x - post.x;
        float awayY = at.y - post.y;
        float distance = (float) Math.sqrt(awayX * awayX + awayY * awayY);
        if (distance <= EPSILON) {
            // Standing on it. There is no "away" from here, so the ordinary roam is the better answer.
            return false;
        }
        float reach = pushDistance;
        for (int attempt = 0; attempt < PUSH_ATTEMPTS; attempt++) {
            float x = at.x + awayX / distance * reach;
            float y = at.y + awayY / distance * reach;
            if (map.isClear(x, y, bodyRadius)) {
                trooper.headFor(x, y);
                return true;
            }
            reach *= 0.5f;
        }
        return false;
    }

    /**
     * Roams inside the squad's patch around a place - the post, or a crowded point (G0086) - rather than across the
     * whole level.
     *
     * <p>A defence has nothing to take, so with nothing in front of them the squad falls through to
     * roaming - and roaming the map is the wrong answer on the one level whose whole instruction is
     * <em>stay here</em>. Men who wander off leave the post held by whoever happened to be standing
     * on it, which reads as the squad ignoring the level.
     *
     * <p>It is the same idea as an idle creature's patch, aimed at the post instead of at where the
     * unit was spawned - and it costs the player nothing, because "forward" still pushes the line out
     * past it and a fight still outranks all of this.
     *
     * <p>Drawn inside the square and rejected outside the circle - and inside {@code keepOff} - so no trigonometry
     * is needed to pick a point in a disc; {@code Math.sin} and {@code Math.cos} are banned here (pillar 6).
     */
    private void roamsAround(Trooper trooper, float centreX, float centreY, float keepOff) {
        for (int attempt = 0; attempt < ROAM_ATTEMPTS; attempt++) {
            float offsetX = rng.nextFloat(-patchRadius, patchRadius);
            float offsetY = rng.nextFloat(-patchRadius, patchRadius);
            float offset = offsetX * offsetX + offsetY * offsetY;
            if (offset > patchRadius * patchRadius || offset < keepOff * keepOff) {
                continue;
            }
            float x = centreX + offsetX;
            float y = centreY + offsetY;
            if (map.isClear(x, y, bodyRadius)) {
                trooper.headFor(x, y);
                return;
            }
        }
        // Nowhere to stand anywhere near it. Better to hold where it is than to walk into rock.
        trooper.clearTarget();
    }

    private boolean arrived(Position at, Trooper trooper) {
        float dx = trooper.targetX - at.x;
        float dy = trooper.targetY - at.y;
        return dx * dx + dy * dy <= arriveRadius * arriveRadius;
    }
}
