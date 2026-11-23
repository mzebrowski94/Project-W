package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.RngStream;
import pl.mzebrows.pwt.sim.Seats;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.Site;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.WorkedSite;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Whole;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * What a taking earns and leaves, for any site (G0110): <strong>the one step a taken site goes through</strong>,
 * whichever system decided it was taken - {@code ControlPointSystem} today, a worked site's system tomorrow. A helper
 * each of them holds an instance of, never a system: no system reaches another (ADR 0016).
 *
 * <p>It marks the {@link Site} taken, which the draft and the goal line count; puts a goal on the tally of every
 * commander standing within the site's reach (G0044); and lays, once, in the ring a point's reinforcements lie in,
 * off every point and every pickup already lying there: the grenade caches, so many a seated slot (G0078), the
 * medkit on its keyed chance (G0086), and - for a biome's site - its crates, by the seated count (G0110). Allocates
 * a {@code Scatter} then - once per site taken, never per entity.
 */
final class Sites {

    private static final ComponentMapper<Site> SITE = ComponentMapper.getFor(Site.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Tally> TALLY = ComponentMapper.getFor(Tally.class);
    private static final ComponentMapper<WorkedSite> WORKED = ComponentMapper.getFor(WorkedSite.class);

    /** A medkit is one draw a site: its second key has nothing to tell apart. */
    private static final long ONE_A_SITE = 0L;

    private final SimConfig config;
    private final Spawner spawner;
    private final TileMap map;
    private final Rng rng;
    private final SimConfig.ControlPointConfig points;
    private final SimConfig.SiteConfig sites;
    private final float cacheRadius;
    private final float medkitRadius;
    private final float crateRadius;

    /** The level's seed, which whether a site lays a medkit is keyed from (G0086). */
    private final long levelSeed;

    /** Scratch for counting seats, by slot; overwritten before each read. */
    private final boolean[] seated;

    private ImmutableArray<Entity> pointsOnTheField = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> seatedCommanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> lying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> worked = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> returning = new ImmutableArray<>(new Array<>());

    /**
     * @param rng where what a taking leaves falls
     * @param capacity how many slots the game holds
     */
    Sites(SimConfig config, TileMap map, Spawner spawner, Rng rng, int capacity) {
        this.config = config;
        this.spawner = spawner;
        this.map = map;
        this.rng = rng;
        this.points = config.controlPoint();
        this.sites = config.site();
        this.cacheRadius = config.pickup(PickupKind.ARMOURY_GRENADES).radius();
        this.medkitRadius = config.pickup(PickupKind.MEDKIT).radius();
        this.crateRadius = config.pickup(PickupKind.CRATE).radius();
        this.levelSeed = config.seed();
        this.seated = new boolean[capacity];
    }

    /** Takes its views of the engine; from the holding system's {@code addedToEngine}. */
    void addedToEngine(Engine engine) {
        pointsOnTheField = engine.getEntitiesFor(
                Family.all(ControlPoint.class, Position.class).get());
        commanders =
                engine.getEntitiesFor(Family.all(Tally.class, Position.class).get());
        seatedCommanders = engine.getEntitiesFor(Family.all(Commander.class).get());
        lying = engine.getEntitiesFor(
                Family.all(Pickup.class, Position.class, Body.class).get());
        returning = engine.getEntitiesFor(Family.all(Respawning.class).get());
        worked = engine.getEntitiesFor(
                Family.all(WorkedSite.class, Position.class, Body.class).get());
    }

    /**
     * The site has just been taken: marks it, credits who stood within {@code reach} of it, and lays what a taking
     * leaves round {@code at}.
     *
     * @param laysCrates whether it is a biome's site, which leaves crates; the finale's hold point does not
     */
    void taken(Entity site, Position at, float reach, boolean laysCrates) {
        if (SITE.has(site)) {
            SITE.get(site).taken = true;
        }
        credit(at, reach);
        lay(site, at, laysCrates);
    }

    /**
     * Whether the site carrying this identity lays a medkit when it is taken (G0086): a function of the site and
     * the seed alone, so the same site on the same seed always does or never does.
     */
    static boolean laysAMedkit(long levelSeed, long site, float chance) {
        return Rng.keyed(levelSeed, RngStream.CACHE, site, ONE_A_SITE) < chance;
    }

    /**
     * A goal on the tally of every commander standing within reach the tick it is taken (G0044) - the same men
     * who count as holding it, so a body in its throes takes no credit.
     */
    private void credit(Position at, float reach) {
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (!Drivers.held(commander) && within(at, POSITION.get(commander), reach)) {
                TALLY.get(commander).goals++;
            }
        }
    }

    /**
     * What a site lays the tick it is taken: the grenade caches (G0078), so many for every seated slot - standing,
     * down or on his way back, away or not - a medkit on its keyed chance (G0086), and its crates by the seated
     * count (G0110). Outside every point and off every pickup lying there, within the reinforcements' reach of
     * this one. A cornered site lays fewer.
     */
    private void lay(Entity site, Position at, boolean laysCrates) {
        int players = Seats.count(seated, seatedCommanders, returning);
        int caches = points.grenadeCaches() * players;
        boolean medkit = laysAMedkit(levelSeed, Handles.of(site), Whole.fraction(points.medkitChance()));
        int crates = laysCrates ? sites.crates(players) : 0;
        if (caches <= 0 && !medkit && crates <= 0) {
            return;
        }
        Scatter scatter = new Scatter(map, rng, cacheRadius, cacheRadius);
        for (int i = 0; i < pointsOnTheField.size(); i++) {
            Position other = POSITION.get(pointsOnTheField.get(i));
            scatter.reserve(other.x, other.y, points.radius());
        }
        // Off every worked site's body, so nothing a mainframe leaves lies inside the mainframe (G0111) - and out of a
        // relic's room, so nothing it leaves lies among its wardens (G0114).
        for (int i = 0; i < worked.size(); i++) {
            Entity standing = worked.get(i);
            Position where = POSITION.get(standing);
            float room = config.workedSite(WORKED.get(standing).kind).room();
            scatter.reserve(where.x, where.y, Math.max(BODY.get(standing).radius, room));
        }
        // Off what already lies there - the point's supply drops share this ring - so nothing lands on anything.
        for (int i = 0; i < lying.size(); i++) {
            Entity pickup = lying.get(i);
            Position where = POSITION.get(pickup);
            scatter.reserve(where.x, where.y, BODY.get(pickup).radius);
        }
        TileRect ring = TileRect.around(map, at.x, at.y, points.reinforcementRadius());
        scatter.scatter(ring, caches, cacheRadius, (x, y) -> spawner.spawnPickup(PickupKind.ARMOURY_GRENADES, x, y));
        if (medkit) {
            scatter.scatter(ring, 1, medkitRadius, (x, y) -> spawner.spawnPickup(PickupKind.MEDKIT, x, y));
        }
        // Last, so the caches and the medkit fall as they did before a site left crates.
        scatter.scatter(ring, crates, crateRadius, (x, y) -> spawner.spawnPickup(PickupKind.CRATE, x, y));
    }

    private static boolean within(Position a, Position b, float range) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        return dx * dx + dy * dy <= range * range;
    }
}
