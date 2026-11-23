package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Emplacement;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Site;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.WorkedSite;
import pl.mzebrows.pwt.sim.def.WorkedSiteDef;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Reach;

/**
 * What becomes of a <strong>worked site</strong> over a tick (G0111): the work put into it counted, and the site taken
 * once there is enough - a mainframe downloaded.
 *
 * <p><strong>A door's rules</strong> (G0069): whoever is at it this tick - a commander with the key held, put there by
 * {@code ActionSystem}, or the one trooper it went to - marks it worked, and a tick nobody did loses all of it. One
 * tick of work a tick, however many hands, so a commander and a trooper together are no faster than one. A wound on
 * the man at it loses it too.
 *
 * <p><strong>Each fresh attempt is loud, once</strong> (G0016): the tick its work goes from none to some, through
 * {@link Rouse}, at its kind's radius.
 *
 * <p><strong>One trooper a site</strong>: of the troopers settled at it - securing it, at arm's length, on their feet -
 * it goes to the first, a tie to the lower handle, and stays his while he is fit to work it. The others stand round it
 * and watch their sectors, which is {@code TrooperObjective}'s.
 *
 * <p><strong>Its guards wake on its first attempt and sleep once it is taken</strong> (G0114): the emplacements that
 * name it by handle start firing the tick its work first goes from none to some, and fire on whether anybody works
 * it or not; the tick it is taken they stop for good.
 *
 * <p><strong>Taken through the one step</strong> (G0110): {@link Sites#taken} marks it, credits the commanders at
 * arm's length and lays the caches, the medkit and the crates. Never worked again. Then its kind's hatch comes up
 * round it through {@link Brood}, outside its room and off the men (G0114).
 *
 * <p>After {@code ActionSystem}, which puts a commander's hands to it this tick, and after {@code DamageSystem}, which
 * wounds whoever was working it - straight after {@code DoorSystem}, the other thing worked on those rules.
 */
public final class WorkedSiteSystem extends EntitySystem {

    private static final ComponentMapper<WorkedSite> WORKED = ComponentMapper.getFor(WorkedSite.class);
    private static final ComponentMapper<Site> SITE = ComponentMapper.getFor(Site.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Handle> HANDLE = ComponentMapper.getFor(Handle.class);

    private static final ComponentMapper<Emplacement> EMPLACEMENT = ComponentMapper.getFor(Emplacement.class);

    private final SimConfig config;
    private final Sites sites;
    private final Brood brood;
    private final Rng rng;

    /** How far past arm's length of a site a commander counts as at it when it is taken: his own body. */
    private final float commanderRadius;

    private ImmutableArray<Entity> worked = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> guards = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> quarry = new ImmutableArray<>(new Array<>());

    /**
     * @param rng where what a taken site leaves falls - the stream a taken point's falls from
     * @param capacity how many slots the game holds
     */
    public WorkedSiteSystem(SimConfig config, TileMap map, Spawner spawner, Rng rng, int capacity) {
        this.config = config;
        this.sites = new Sites(config, map, spawner, rng, capacity);
        this.brood = new Brood(map, spawner, config.nest().hatchKeepOff());
        this.rng = rng;
        this.commanderRadius = config.commander().radius();
    }

    @Override
    public void addedToEngine(Engine engine) {
        worked =
                engine.getEntitiesFor(Family.all(WorkedSite.class, Site.class, Position.class, Body.class, Handle.class)
                        .get());
        troopers =
                engine.getEntitiesFor(Family.all(Trooper.class, Action.class, Handle.class, Position.class, Body.class)
                        .get());
        watchers = engine.getEntitiesFor(
                Family.all(Perception.class, Position.class).get());
        guards = engine.getEntitiesFor(Family.all(Emplacement.class).get());
        quarry = engine.getEntitiesFor(Family.all(Faction.class, Position.class).get());
        sites.addedToEngine(engine);
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < worked.size(); i++) {
            Entity entity = worked.get(i);
            WorkedSite site = WORKED.get(entity);
            if (SITE.get(entity).taken) {
                site.worked = false;
                continue;
            }
            boolean fresh = site.workedTicks == 0;
            work(site, trooperAt(entity, site));
            Position at = POSITION.get(entity);
            WorkedSiteDef def = config.workedSite(site.kind);
            if (fresh && site.workedTicks > 0) {
                // Through Rouse, shared with the point and the nest (G0016).
                Rouse.within(watchers, at.x, at.y, def.rouseRadius());
                wake(Handles.of(entity), true);
            }
            if (site.workedTicks >= site.workTicks) {
                site.workedTicks = 0;
                site.trooper = Handle.NOBODY;
                sites.taken(
                        entity, at, BODY.get(entity).radius + config.action().reach() + commanderRadius, true);
                wake(Handles.of(entity), false);
                hatch(def.hatch(), at, def.clearing());
            }
        }
    }

    /**
     * Wakes, or puts to sleep, every emplacement guarding this site (G0114). Only on the tick of an attempt or a taking,
     * never every tick.
     */
    private void wake(long site, boolean awake) {
        for (int i = 0; i < guards.size(); i++) {
            Emplacement guard = EMPLACEMENT.get(guards.get(i));
            if (guard.guards == site) {
                guard.awake = awake;
            }
        }
    }

    /** What comes up round a site the tick it is taken (G0114): outside its room, off the men. */
    private void hatch(WorkedSiteDef.Hatch hatch, Position at, float room) {
        int count = hatch.max() > 0 ? hatch.count(rng) : 0;
        if (count > 0) {
            float radius = config.unit(hatch.strain()).radius();
            brood.hatch(rng, hatch.strain(), radius, 0, at, room, hatch.radius(), count, quarry);
        }
    }

    /**
     * The trooper this site has gone to, kept while he is still fit to work it and otherwise given to the first man at
     * it, lowest handle on a tie - or null. Writes the answer on the site.
     */
    private @Nullable Entity trooperAt(Entity entity, WorkedSite site) {
        @Nullable Entity kept = Handles.resolve(troopers, site.trooper);
        if (kept != null && works(kept, entity)) {
            return kept;
        }
        Entity first = null;
        long firstHandle = Long.MAX_VALUE;
        for (int i = 0; i < troopers.size(); i++) {
            Entity trooper = troopers.get(i);
            long id = HANDLE.get(trooper).id;
            if (id < firstHandle && works(trooper, entity)) {
                first = trooper;
                firstHandle = id;
            }
        }
        site.trooper = first == null ? Handle.NOBODY : firstHandle;
        return first;
    }

    /**
     * Whether a trooper could be working this site now: settled on it - securing it, headed for its middle - at arm's
     * length, on his feet. A man who turned to fight is no longer securing it, and drops the work.
     */
    private static boolean works(Entity man, Entity site) {
        Trooper trooper = TROOPER.get(man);
        Position at = POSITION.get(site);
        return trooper.mode == Trooper.Mode.SECURE
                && trooper.hasTarget
                && trooper.targetX == at.x
                && trooper.targetY == at.y
                && !Drivers.held(man)
                && Reach.gap(man, site) <= ACTION.get(man).reach;
    }

    /** Counts this tick's work on a site: a tick of it if anybody was at it, all of it lost if nobody was. */
    private static void work(WorkedSite site, @Nullable Entity trooper) {
        if (trooper != null && VITALITY.has(trooper) && VITALITY.get(trooper).woundedThisTick()) {
            // A wound on the man at it loses the work, as it does on a commander (G0111).
            site.workedTicks = 0;
        } else if (trooper != null) {
            site.worked = true;
        }
        boolean worked = site.worked;
        site.worked = false;
        if (!worked) {
            // A commitment, not a total (G0021): a tick nobody worked loses every tick that was.
            site.workedTicks = 0;
            return;
        }
        site.workedTicks++;
    }
}
