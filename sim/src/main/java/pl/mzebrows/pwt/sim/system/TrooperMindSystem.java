package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;

/**
 * Decides, once a tick, what each trooper is doing and where it is headed.
 *
 * <p>Deciding is here and driving is {@code TrooperMoveSystem}; that split is G0010's shape and it is
 * what will let orders slot in above this without touching how a trooper walks. Everything downstream
 * reads {@link Trooper#mode} and the target, and asks the question again for nobody.
 *
 * <p><strong>Nearest means nearest by route</strong> (2.e). A point twenty units away through a wall
 * is not nearer than one forty units away down a corridor, and choosing on a straight line marches a
 * squad into rock. Each point owns a flow field built for a trooper's body, so the same grid pass
 * answers both "which is nearest" and "which way do I walk", for every trooper at once - which is the
 * whole reason to reach for a field rather than a search per unit.
 *
 * <p>The fields are built <strong>once</strong>. Control points do not move and are never removed, so
 * a rebuild would be a full grid pass per tick for an answer that cannot have changed.
 *
 * <p><strong>The order of precedence is fight, order, casualty, objective.</strong> A fight beats
 * everything because a squad that walked past one to do anything else reads as the AI being broken.
 * A standing order beats going to fetch somebody because an order that a body on the ground could
 * override is not an order (G0017); a man told to hold a corner holds it. And fetching somebody
 * beats the level's objectives, because a squad that walked past one of its own to stand on a square
 * would be the same failure as walking past a fight.
 *
 * <p>Whether it goes at all is a roll - {@code AiChance.AID_A_TROOPER}, or the much likelier
 * {@code AID_THE_COMMANDER} - and it is rolled only while the trooper is not already going. Rolling
 * every tick of an approach would have men setting off and thinking better of it halfway, which
 * reads as indecision rather than as judgement.
 *
 * <p>Something in front of it is dealt with before anywhere it was going, because the alternative is
 * a squad walking past a fight to stand on a square - which is the version that reads as the AI being
 * broken. Where it was headed is not
 * remembered: the objective is a pure function of where the trooper is standing and what is still
 * untaken, so it is re-derived the moment the shooting stops rather than saved and restored.
 *
 * <p>With nothing left to take, a trooper roams instead: a seeded target somewhere it can walk to,
 * picked again whenever it arrives. That is what "explore the map to clear it" is until a trooper can
 * notice an enemy for itself - and because roaming and advancing differ only in <em>how the target is
 * chosen</em>, they are the same driver downstream rather than a second behaviour.
 */
public final class TrooperMindSystem extends EntitySystem {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    // The cascade, highest first. Each half owns its own queries and scratch; this class owns the order.
    private final TrooperFight fight;
    private final TrooperOrders orders;
    private final TrooperAid aid;
    private final TrooperCrew crew;
    private final TrooperObjective objective;
    private final TrooperRoam roam;

    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());

    public TrooperMindSystem(SimConfig config, TileMap map, Rng rng, Rng nerve) {
        this.fight = new TrooperFight(config, map);
        this.orders = new TrooperOrders(config, map);
        this.aid = new TrooperAid(config, nerve);
        this.crew = new TrooperCrew(config, nerve);
        this.objective = new TrooperObjective(config, map, rng);
        this.roam = new TrooperRoam(config, map, rng);
    }

    @Override
    public void addedToEngine(Engine engine) {
        troopers =
                engine.getEntitiesFor(Family.all(Trooper.class, Position.class).get());
        fight.bind(engine);
        orders.bind(engine);
        aid.bind(engine);
        crew.bind(engine);
        objective.bind(engine);
        roam.bind(engine);
    }

    @Override
    public void update(float deltaTime) {
        objective.buildRoutesIfNeeded();
        fight.beginTick();
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
            if (Drivers.held(entity)) {
                continue;
            }
            Trooper trooper = TROOPER.get(entity);
            Position at = POSITION.get(entity);
            if (objective.takenPointUnder(at) < 0) {
                // Off every taken point, so the next one it holds is a hold of its own. Asked before
                // anything else, so a fight or an errand that took it off the point ends the hold too.
                trooper.holding = false;
            }

            if (trooper.alertRemaining > 0) {
                trooper.alertRemaining--;
            }

            if (fight.engages(trooper, at)
                    || orders.obeys(trooper, at)
                    || aid.aids(entity, trooper, at, deltaTime)
                    || crew.crews(entity, trooper, at, deltaTime)
                    || objective.holdsWhatItHasTaken(trooper, at, deltaTime)
                    || objective.advances(trooper, at)) {
                continue;
            }
            roam.explores(trooper, at);
        }
        objective.splitIntoSectors();
    }
}
