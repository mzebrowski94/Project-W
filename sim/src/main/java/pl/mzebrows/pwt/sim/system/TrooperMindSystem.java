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
import pl.mzebrows.pwt.sim.component.Weapon;

/**
 * Decides, once a tick, what each trooper is doing and where it is headed.
 *
 * <p>Deciding is here and driving is {@code TrooperMoveSystem} (G0010); downstream reads
 * {@link Trooper#mode} and the target and asks again for nobody.
 *
 * <p><strong>The cascade is fight, order, errands (aid, mend, crew), objective, roam</strong>, each half in
 * its own class; this one owns only the order. A standing order beats fetching somebody (G0017). An
 * errand is rolled only while not already going, so men do not set off and think better of it.
 * Nothing is remembered across the cascade: the objective is re-derived the moment a fight ends.
 */
public final class TrooperMindSystem extends EntitySystem {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);

    // The cascade, highest first. Each half owns its own queries and scratch; this class owns the order.
    private final TrooperFight fight;
    private final TrooperOrders orders;
    private final TrooperAid aid;
    private final TrooperMend mend;
    private final TrooperCrew crew;
    private final TrooperObjective objective;
    private final TrooperRoam roam;

    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());

    public TrooperMindSystem(SimConfig config, TileMap map, Rng rng, Odds odds) {
        this.fight = new TrooperFight(config, map);
        this.orders = new TrooperOrders(config, map);
        this.aid = new TrooperAid(config, odds);
        this.mend = new TrooperMend(config);
        this.crew = new TrooperCrew(config, odds);
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
        mend.bind(engine);
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

            if (fight.engages(trooper, at, shotWidth(entity))
                    || orders.obeys(trooper, at)
                    || aid.aids(entity, trooper, at, deltaTime)
                    || mend.mends(entity, trooper, at)
                    || crew.crews(entity, trooper, at, deltaTime)
                    || objective.holdsWhatItHasTaken(trooper, at, deltaTime)
                    || objective.advances(trooper, at)) {
                continue;
            }
            roam.explores(trooper, at);
        }
        objective.splitIntoSectors();
    }

    /** How far either side of the line this man's shot reaches: the round itself, and a twin mount's offset. */
    private static float shotWidth(Entity entity) {
        if (!WEAPON.has(entity)) {
            return 0f;
        }
        Weapon weapon = WEAPON.get(entity);
        return weapon.projectileRadius + weapon.muzzleOffset;
    }
}
