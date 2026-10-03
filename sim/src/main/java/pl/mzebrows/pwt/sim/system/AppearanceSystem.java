package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Barrage;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Bloat;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Mine;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Shield;
import pl.mzebrows.pwt.sim.component.Shock;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Publishes what each entity is doing, so the renderer can show it without asking (G0003, pillar 5).
 *
 * <p>Every state is derived here, in one place and one order, never written by whichever system
 * caused it. The precedence is worst-news-first, with one deliberate exception: <strong>a
 * <em>fatal</em> lock outranks being dead</strong>, so both parties read as {@code GRAPPLING} for the
 * whole lock - a narrow, knowing exception to pillar 5. Anything shot reads as {@code DYING} at once.
 */
public final class AppearanceSystem extends IteratingSystem {

    private static final ComponentMapper<Appearance> APPEARANCE = ComponentMapper.getFor(Appearance.class);
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Slowed> SLOWED = ComponentMapper.getFor(Slowed.class);
    private static final ComponentMapper<Bloat> BLOAT = ComponentMapper.getFor(Bloat.class);
    private static final ComponentMapper<Shock> SHOCK = ComponentMapper.getFor(Shock.class);
    private static final ComponentMapper<Projectile> PROJECTILE = ComponentMapper.getFor(Projectile.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Feed> FEED = ComponentMapper.getFor(Feed.class);
    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Nest> NEST = ComponentMapper.getFor(Nest.class);
    private static final ComponentMapper<Blast> BLAST = ComponentMapper.getFor(Blast.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Shield> SHIELD = ComponentMapper.getFor(Shield.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Strain> STRAIN = ComponentMapper.getFor(Strain.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);
    private static final ComponentMapper<Heat> HEAT = ComponentMapper.getFor(Heat.class);
    private static final ComponentMapper<Gripped> GRIPPED = ComponentMapper.getFor(Gripped.class);
    private static final ComponentMapper<Crew> CREW = ComponentMapper.getFor(Crew.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Slick> SLICK = ComponentMapper.getFor(Slick.class);
    private static final ComponentMapper<Mine> MINE = ComponentMapper.getFor(Mine.class);
    private static final ComponentMapper<Barrage> BARRAGE = ComponentMapper.getFor(Barrage.class);
    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);

    private final Defs<UnitKind, UnitDef> units;
    private final Defs<PickupKind, PickupDef> pickups;

    /**
     * How far into the level everything that shines is, in seconds, for this tick.
     *
     * <p>One phase for the whole level, worked out from the level's tick each update - a clock kept in
     * a system is state no snapshot sees (ADR 0014).
     */
    @NotState("scratch: recomputed from the level's tick at the start of every update")
    private float shineSeconds;

    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

    /**
     * How much of a nest's core is showing before its clock has started, as a fraction.
     *
     * <p>A dot, not work in progress.
     */
    private static final float WAITING = 0.3f;

    public AppearanceSystem(SimConfig config) {
        super(Family.all(Appearance.class).get());
        this.units = config.units();
        this.pickups = config.pickups();
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        levels = engine.getEntitiesFor(Family.all(Level.class).get());
    }

    @Override
    public void update(float deltaTime) {
        // Once for the tick. Plus one: the level's tick is counted after the systems run.
        long tick = levels.size() == 0 ? 0L : LEVEL.get(levels.first()).tick;
        shineSeconds = (float) ((tick + 1L) * (double) deltaTime);
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Appearance appearance = APPEARANCE.get(entity);
        appearance.state = stateOf(entity);
        publishCharge(entity, appearance);
        appearance.ordered = TROOPER.has(entity)
                && (TROOPER.get(entity).order != null || TROOPER.get(entity).pushingForward());
        // A fact about the gun, not the creature.
        appearance.charged = WEAPON.has(entity) && WEAPON.get(entity).blastRadius > 0f;
        publishTrail(entity, appearance);
        // How hot its gun is, and whether that has taken it away (pillar 5) - but never a trooper's: his
        // gun stops itself all the same, and a bar over every man in the squad was clutter, not news.
        boolean showsHeat = HEAT.has(entity) && !TROOPER.has(entity);
        appearance.heat = showsHeat ? HEAT.get(entity).value : 0f;
        appearance.overheated = showsHeat && HEAT.get(entity).locked;
        // How many wounds a man on his feet is carrying, one mark each (G0053, pillar 5).
        appearance.wounds = VITALITY.has(entity) && VITALITY.get(entity).wounded() ? VITALITY.get(entity).wounds : 0;
        appearance.shield = SHIELD.has(entity) ? SHIELD.get(entity).fraction() : 0f;
    }

    /**
     * Where a shot is drawn back to: the muzzle it came from, no further back than its gun allows.
     *
     * <p><strong>Every pixel of that line is on the path a real simulated shot took</strong> - no hitscan.
     */
    private static void publishTrail(Entity entity, Appearance appearance) {
        if (!PROJECTILE.has(entity)) {
            return;
        }
        Projectile shot = PROJECTILE.get(entity);
        if (shot.trailLength <= 0f) {
            return;
        }
        Position at = POSITION.get(entity);
        float backX = shot.originX - at.x;
        float backY = shot.originY - at.y;
        float flown = (float) Math.sqrt(backX * backX + backY * backY);
        if (flown <= shot.trailLength || flown <= 0f) {
            appearance.trailingFrom(shot.originX, shot.originY);
            return;
        }
        float reach = shot.trailLength / flown;
        appearance.trailingFrom(at.x + backX * reach, at.y + backY * reach);
    }

    /**
     * How far through something this entity is, in {@code [0, 1]}, and what that something is.
     *
     * <p>A fraction, so the renderer knows nothing about thresholds (pillar 5). Number and look are
     * published in one pass, so they cannot come from different branches.
     */
    private void publishCharge(Entity entity, Appearance appearance) {
        appearance.chargeLook = Look.NONE;
        if (ACTION.has(entity) && ACTION.get(entity).progress > 0f) {
            // The work shows on the man doing it; the casualty's circle shows his clock. Its own
            // colour: the one clock the player is winning.
            appearance.chargeLook = Look.MENDING;
            appearance.charge = ACTION.get(entity).progress;
            return;
        }
        if (BLOAT.has(entity) && BLOAT.get(entity).charged()) {
            // A body being pumped up: above everything else here, in its own colour. On the living
            // too; the ring grows over the body and the body is untouched.
            appearance.chargeLook = Look.BLOAT;
            appearance.charge = BLOAT.get(entity).charge;
            return;
        }
        if (SHOCK.has(entity) && SHOCK.get(entity).charged()) {
            // How close a bolt-held trigger is to stunning it (G0059), in the bolts' own colour. Below a
            // bloat: what is about to go off matters more than what is about to be held still.
            appearance.chargeLook = Look.SHOCK;
            appearance.charge = SHOCK.get(entity).charge;
            return;
        }
        if (VITALITY.has(entity) && VITALITY.get(entity).begun) {
            // How long a man on the ground has left, read backwards (pillar 5).
            appearance.charge = VITALITY.get(entity).fraction();
            return;
        }
        if (GRIPPED.has(entity) && GRIPPED.get(entity).held()) {
            // How long a man in something's jaws has left, read backwards like a casualty's (G0045).
            appearance.charge = GRIPPED.get(entity).fraction;
            return;
        }
        if (BLAST.has(entity)) {
            // A blast burning out, read backwards.
            appearance.charge = BLAST.get(entity).fade();
            return;
        }
        if (MINE.has(entity)) {
            // The fuse, in its own colour; nothing before it is armed.
            appearance.chargeLook = Look.FUSE;
            appearance.charge = MINE.get(entity).flash();
            return;
        }
        if (SLICK.has(entity)) {
            // Ground that is changing says how far through it is; ground that is not says nothing.
            Slick slick = SLICK.get(entity);
            appearance.charge = slick.animated() ? slick.fade() : 0f;
            return;
        }
        if (POINT.has(entity)) {
            // A point filling up, the same tell as a unit filling up.
            appearance.charge = POINT.get(entity).progress();
            return;
        }
        if (NEST.has(entity)) {
            // What comes out of it, in that strain's colour, and how close the next lot is. Never
            // nothing: half of it is an identity (pillar 5).
            appearance.chargeLook = units.of(NEST.get(entity).kind).look();
            appearance.charge = WAITING + (1f - WAITING) * NEST.get(entity).progress();
            return;
        }
        if (PICKUP.has(entity)) {
            // Something small on a large map, saying so: never full, never empty.
            PickupDef def = pickups.of(PICKUP.get(entity).kind);
            if (def.shines()) {
                appearance.chargeLook = Look.GLEAM;
                appearance.charge = pulse(def.shineSeconds());
            } else {
                appearance.charge = 0f;
            }
            return;
        }
        if (BARRAGE.has(entity) && BARRAGE.get(entity).enabled && HEALTH.has(entity)) {
            // A boss says how much of it is left, read down like every other clock (pillar 5): no
            // player can count seven thousand points of damage by eye (G0048).
            appearance.charge = HEALTH.get(entity).fraction();
            return;
        }
        if (!FEED.has(entity) || !STRAIN.has(entity)) {
            appearance.charge = 0f;
            return;
        }
        UnitDef def = units.of(STRAIN.get(entity).kind);
        if (!def.canEvolveByFeeding()) {
            appearance.charge = 0f;
            return;
        }
        float progress = (float) FEED.get(entity).count / def.feedToEvolve();
        appearance.charge = progress < 1f ? progress : 1f;
    }

    /**
     * One slow triangle wave, in {@code [0, 1]}, for something that shines.
     *
     * <p>A triangle rather than a sine: {@code sin} is banned in {@code sim} (pillar 6).
     */
    private float pulse(float periodSeconds) {
        float turns = shineSeconds / periodSeconds;
        float phase = turns - (float) Math.floor(turns);
        return 1f - Math.abs(phase * 2f - 1f);
    }

    private static Appearance.State stateOf(Entity entity) {
        boolean held = STUNNED.has(entity) && STUNNED.get(entity).active();
        if (held && STUNNED.get(entity).grappling) {
            return Appearance.State.GRAPPLING;
        }
        if (DYING.has(entity) && DYING.get(entity).begun) {
            return Appearance.State.DYING;
        }
        if (VITALITY.has(entity) && VITALITY.get(entity).begun) {
            // Above staggered, below dying: worst news first.
            return Appearance.State.CASUALTY;
        }
        if (GRIPPED.has(entity) && GRIPPED.get(entity).held()) {
            // Below the states that say he is down; above one that says he was shoved.
            return Appearance.State.GRIPPED;
        }
        if (held && STUNNED.get(entity).dazed) {
            return Appearance.State.STUNNED;
        }
        if (held) {
            return Appearance.State.STAGGERED;
        }
        if (TURRET.has(entity) && TURRET.get(entity).crewed) {
            return Appearance.State.CREWED;
        }
        if (CREW.has(entity) && CREW.get(entity).aboard()) {
            // Below the states that say he is hurt.
            return Appearance.State.ABOARD;
        }
        if (BARRAGE.has(entity) && BARRAGE.get(entity).windingUp()) {
            // The tell before a pattern, the same one a leap gives: something is about to come at you.
            return Appearance.State.WINDING_UP;
        }
        if (LEAP.has(entity)) {
            Leap leap = LEAP.get(entity);
            if (leap.airborne()) {
                return Appearance.State.AIRBORNE;
            }
            if (leap.phase == Leap.Phase.HESITATING) {
                return Appearance.State.WINDING_UP;
            }
        }
        if (SLOWED.has(entity) && SLOWED.get(entity).factor() < 1f) {
            // Above unaware: being shot at means it has noticed somebody.
            return Appearance.State.SLOWED;
        }
        if (PERCEPTION.has(entity) && !PERCEPTION.get(entity).aggro) {
            return Appearance.State.UNAWARE;
        }
        return Appearance.State.NORMAL;
    }
}
