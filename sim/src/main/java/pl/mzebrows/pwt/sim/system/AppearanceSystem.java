package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Bloat;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Mine;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Velocity;
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
 * <p>Every state an entity can be in is derived here, in one place and in one order, rather than
 * written by whichever system happened to cause it. Two systems writing the same field is how a unit
 * ends up drawn as winding up while it is actually stunned - which would be worse than no tell at
 * all, because the player would have learned to trust it.
 *
 * <p>The precedence is worst-news-first, with one deliberate exception at the top. <strong>A
 * <em>fatal</em> lock outranks being dead</strong>, and only a fatal one: something swatted while it
 * walked is stunned as it dies and reads as dying, because the player has no reason to be in suspense
 * about it. Both parties to a landed blow read as {@code GRAPPLING} for the
 * whole lock, the loser included, so that for that beat the player cannot tell from the picture which
 * of them is getting back up - only when the lock ends does the loser fail to stand.
 *
 * <p>That is a knowing exception to pillar 5, and it is narrow: it lasts exactly as long as the lock,
 * it applies only to an entity that has just been in an exchange, and everything else about the two
 * is still on screen. A death with no exchange behind it - anything shot - reads as {@code DYING}
 * immediately, as it should.
 */
public final class AppearanceSystem extends IteratingSystem {

    private static final ComponentMapper<Appearance> APPEARANCE = ComponentMapper.getFor(Appearance.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Slowed> SLOWED = ComponentMapper.getFor(Slowed.class);
    private static final ComponentMapper<Bloat> BLOAT = ComponentMapper.getFor(Bloat.class);
    private static final ComponentMapper<Projectile> PROJECTILE = ComponentMapper.getFor(Projectile.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Feed> FEED = ComponentMapper.getFor(Feed.class);
    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Nest> NEST = ComponentMapper.getFor(Nest.class);
    private static final ComponentMapper<Blast> BLAST = ComponentMapper.getFor(Blast.class);
    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Strain> STRAIN = ComponentMapper.getFor(Strain.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Ranged> RANGED = ComponentMapper.getFor(Ranged.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);
    private static final ComponentMapper<Heat> HEAT = ComponentMapper.getFor(Heat.class);
    private static final ComponentMapper<Crew> CREW = ComponentMapper.getFor(Crew.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Slick> SLICK = ComponentMapper.getFor(Slick.class);
    private static final ComponentMapper<Mine> MINE = ComponentMapper.getFor(Mine.class);

    private final Defs<UnitKind, UnitDef> units;
    private final Defs<PickupKind, PickupDef> pickups;

    /**
     * How far through its pulse everything that shines currently is, in {@code [0, 1)}.
     *
     * <p>One phase for the whole level rather than one per entity, and that is the decision rather
     * than a shortcut: a crate carrying a phase of its own would be a field on a pooled component
     * that has to be reset, for a difference the player cannot see - two crates are rarely on screen
     * close enough together for anybody to read whether they pulse together. It is advanced once per
     * tick from the fixed step, so it is a function of the tick count and a replay matches (pillar 6).
     */
    private float shinePhase;

    /**
     * How much of a nest's core is showing before its clock has started, as a fraction.
     *
     * <p>Small enough to read as a dot rather than as work in progress, and large enough to be seen -
     * it is what a nest that has noticed nobody says about itself.
     */
    private static final float WAITING = 0.3f;

    public AppearanceSystem(SimConfig config) {
        super(Family.all(Appearance.class).get());
        this.units = config.units();
        this.pickups = config.pickups();
    }

    @Override
    public void update(float deltaTime) {
        // Once for the tick, before anything is published, so every shining thing on the field
        // reports the same moment of the same pulse.
        shinePhase += deltaTime;
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Appearance appearance = APPEARANCE.get(entity);
        appearance.state = stateOf(entity);
        publishCharge(entity, appearance);
        face(entity, appearance);
        appearance.ordered = TROOPER.has(entity)
                && (TROOPER.get(entity).order != null || TROOPER.get(entity).pushingForward());
        // What it is carrying goes off where it lands. A fact about the gun, so nothing here asks
        // what kind of creature is holding it.
        appearance.charged = WEAPON.has(entity) && WEAPON.get(entity).blastRadius > 0f;
        publishTrail(entity, appearance);
        // How hot its gun is, and whether that has taken it away. Pillar 5: a bar the player cannot
        // see is a bar he is guessing at, and this one is entirely his own doing.
        appearance.heat = HEAT.has(entity) ? HEAT.get(entity).value : 0f;
        appearance.overheated = HEAT.has(entity) && HEAT.get(entity).locked;
    }

    /**
     * Points the entity where it is looking, for anything whose facing the player has a reason to
     * read.
     *
     * <p><strong>Aim first, and never a heading, for anything with an {@link Intent}</strong> - that
     * is G0002 at the one place the two are easiest to conflate. A commander backing away while
     * shooting forwards must be drawn facing his shots, or the tell contradicts the bullets.
     *
     * <p>A shooter faces what it is aiming at, and everything else that steers itself faces the way it
     * is going. What decides whether a facing is published at all is whether the entity drives itself - an {@link Intent} or a
     * {@link Steering} - rather than what it is, so a bullet reports none and the renderer never has
     * to ask what it is looking at (G0003).
     */
    private static void face(Entity entity, Appearance appearance) {
        if (TURRET.has(entity)) {
            // A gun on a mount faces where it is pointing, which is the whole tell: the player has to
            // be able to read how far it still has to come round before it can reach him.
            Turret turret = TURRET.get(entity);
            appearance.face(turret.aimX, turret.aimY);
            return;
        }
        if (TROOPER.has(entity)) {
            // A trooper decides where it is looking rather than looking wherever it is walking. That
            // is what lets it stand still and keep watching, and what makes its arc a thing the
            // player can read off the picture.
            Trooper trooper = TROOPER.get(entity);
            appearance.face(trooper.lookX, trooper.lookY);
            return;
        }
        if (INTENT.has(entity) && POSITION.has(entity)) {
            Position at = POSITION.get(entity);
            Intent intent = INTENT.get(entity);
            appearance.face(intent.aimX - at.x, intent.aimY - at.y);
            return;
        }
        if (RANGED.has(entity) && RANGED.get(entity).aiming()) {
            // A shooter faces where its gun is pointing, not where it last walked. Its field of view
            // is measured from that same direction, so this is the tell that makes the arc something
            // the player can read off the picture rather than learn by being shot (pillar 5, G0015).
            Ranged ranged = RANGED.get(entity);
            appearance.face(ranged.aimX, ranged.aimY);
            return;
        }
        if (STEERING.has(entity) && VELOCITY.has(entity)) {
            Velocity velocity = VELOCITY.get(entity);
            appearance.face(velocity.x, velocity.y);
        }
    }

    /**
     * Where a shot is drawn back to: the muzzle it came from, no further back than its gun allows.
     *
     * <p>Everything else in the game is a dot and publishes its own position, which the renderer
     * reads as "no line". A gun that fires forty times a second is read as a stream of dots unless
     * the dots are joined, and the beam is the whole of what the laser is (pillar 5) - so the shot
     * says where it came from and the renderer joins the two ends.
     *
     * <p><strong>Every pixel of that line is on the path a real simulated shot actually took.</strong>
     * Nothing here is a hitscan and nothing is invented: the line starts at the muzzle, grows at the
     * shot's own speed, and stops dead where the shot did - which is what makes a beam that has hit
     * something look different from one that has not.
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
     * <p>Pillar 5 says the player never tracks a number the game does not show, and how much an
     * enemy has eaten is exactly such a number. Published as a fraction rather than as a count so the
     * renderer needs to know nothing about strains or thresholds - and so that anything else that
     * fills up can use the same channel later.
     *
     * <p>A strain with nowhere to grow reports nothing, which is what almost everything reports.
     *
     * <p>It publishes the look alongside the number, in one pass, for the reason this whole class
     * exists: two methods walking the same conditions is how a charge ends up drawn in the colour of
     * the mechanic above the one that set it.
     */
    private void publishCharge(Entity entity, Appearance appearance) {
        appearance.chargeLook = Look.NONE;
        if (ACTION.has(entity) && ACTION.get(entity).progress > 0f) {
            // The work shows on the man doing it, not on the man being worked on - the casualty's
            // own circle is already saying how long he has left, and one circle cannot carry two
            // meanings without the player having to guess which.
            //
            // It is the one charge with a colour of its own. Everything else on this channel is a
            // clock the player is losing; this is the one he is winning, and at a glance the two
            // must not be the same picture.
            appearance.chargeLook = Look.MENDING;
            appearance.charge = ACTION.get(entity).progress;
            return;
        }
        if (BLOAT.has(entity) && BLOAT.get(entity).charged()) {
            // A thing being pumped up, above everything else on this channel because it is the only
            // thing here that is about to kill somebody. In a colour of its own for the reason the
            // fuse has one: everything else that fills up is a thing getting ready, and two circles
            // that ask for opposite reactions must not look alike.
            //
            // Drawn on the living as readily as on the dead, so the player can watch the thing he is
            // burning fill up and decide whether to keep the beam on it through the kill. What he
            // must not be able to read off it is any change to the creature itself - the ring grows
            // over the body and the body is untouched.
            appearance.chargeLook = Look.BLOAT;
            appearance.charge = BLOAT.get(entity).charge;
            return;
        }
        if (CASUALTY.has(entity) && CASUALTY.get(entity).begun) {
            // How long a man on the ground has left, on the same channel as everything else that
            // fills up - read backwards, as a blast is. Pillar 5 at its sharpest: a rescue the
            // player cannot time is a rescue he cannot decide to attempt.
            appearance.charge = CASUALTY.get(entity).fraction();
            return;
        }
        if (BLAST.has(entity)) {
            // A blast burning out rides the same channel as a unit filling up, read backwards. One
            // way of drawing "how far through this is", learnt once and recognised everywhere.
            appearance.charge = BLAST.get(entity).fade();
            return;
        }
        if (MINE.has(entity)) {
            // The fuse, in a colour of its own, and the only thing on this channel that is darker
            // than what it is drawn on. Everything else that fills up is a body brightened, and a
            // thing that is about to go off in your face must not read as a thing getting ready.
            // Before it is armed it says nothing at all, which is what makes a minefield a field of
            // dots until somebody steps into it.
            appearance.chargeLook = Look.FUSE;
            appearance.charge = MINE.get(entity).flash();
            return;
        }
        if (SLICK.has(entity)) {
            // Ground that is changing says how far through it is, and ground that is simply lying
            // there says nothing. A fire swells, burns down and goes out, and the same channel that
            // draws a blast burning out draws the heart of it; venom is the same circle from the
            // tick it lands to the tick it is gone, and a core on it would be a tell about nothing.
            Slick slick = SLICK.get(entity);
            appearance.charge = slick.animated() ? slick.fade() : 0f;
            return;
        }
        if (POINT.has(entity)) {
            // A point filling up is the same tell as a unit filling up, deliberately: one
            // way of drawing \"nearly there\", learnt once and read everywhere.
            appearance.charge = POINT.get(entity).progress();
            return;
        }
        if (NEST.has(entity)) {
            // What comes out of it, in that strain's own colour, and how close the next lot is. It
            // never falls to nothing, which is the one thing on this channel that does not: every
            // other charge is a thing in progress, and half of this one is an identity. A hole the
            // player cannot tell a grunt nest from until it opens is a hole he learns about by being
            // caught by it, and pillar 5 says what a thing is must be legible from how it looks.
            appearance.chargeLook = units.of(NEST.get(entity).kind).look();
            appearance.charge = WAITING + (1f - WAITING) * NEST.get(entity).progress();
            return;
        }
        if (PICKUP.has(entity)) {
            // Something small on a large map, saying so. It is never full and never empty, which is
            // what separates a glow from every other thing on this channel - all of those are going
            // somewhere, and this one is only asking to be noticed.
            PickupDef def = pickups.of(PICKUP.get(entity).kind);
            if (def.shines()) {
                appearance.chargeLook = Look.GLEAM;
                appearance.charge = pulse(def.shineSeconds());
            } else {
                appearance.charge = 0f;
            }
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
     * <p>A triangle rather than a sine because {@code sin} is banned in {@code sim} - it is specified
     * only to within an ulp and may differ between JVMs, which pillar 6 does not allow. At this speed
     * nobody can tell the two apart, and the wave never rests at either end, so the thing is always
     * either brightening or dimming rather than sitting still at full.
     */
    private float pulse(float periodSeconds) {
        float turns = shinePhase / periodSeconds;
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
        if (CASUALTY.has(entity) && CASUALTY.get(entity).begun) {
            // Above staggered, below dying. A man who was shoved and then shot off his feet is a
            // casualty, not a stagger - worst news first - but a casualty who ran out of time is a
            // corpse, and the two must never be confused for each other.
            return Appearance.State.CASUALTY;
        }
        if (held) {
            return Appearance.State.STAGGERED;
        }
        if (TURRET.has(entity) && TURRET.get(entity).crewed) {
            return Appearance.State.CREWED;
        }
        if (CREW.has(entity) && CREW.get(entity).aboard()) {
            // Inside the gun rather than standing beside it. Below the states that say he is hurt,
            // because a man blown off a gun is a casualty first and a passenger not at all.
            return Appearance.State.ABOARD;
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
            // Above unaware because being slowed means something is shooting at it, which means it
            // has noticed somebody; a unit reported as both would be reported as the wrong one.
            return Appearance.State.SLOWED;
        }
        if (PERCEPTION.has(entity) && !PERCEPTION.get(entity).aggro) {
            return Appearance.State.UNAWARE;
        }
        return Appearance.State.NORMAL;
    }
}
