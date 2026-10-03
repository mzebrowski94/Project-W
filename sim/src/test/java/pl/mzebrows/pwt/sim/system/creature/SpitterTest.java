package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CHARGED_SPIT_BLAST_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DETECTION_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LOSE_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_AGGRO_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SECONDARY_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SPIT_CLOSE_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SPIT_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SPIT_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SPIT_SPREAD_DEGREES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WANDER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.chargedByBarrels;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.godMode;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.holdingAPreferredDistance;
import static pl.mzebrows.pwt.sim.AlphaWorlds.huntingBarrels;
import static pl.mzebrows.pwt.sim.AlphaWorlds.shooting;
import static pl.mzebrows.pwt.sim.AlphaWorlds.shootingStraight;
import static pl.mzebrows.pwt.sim.AlphaWorlds.shufflingShooters;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watchingItsArc;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * A strain that keeps its distance and shoots. Everything about firing already existed - the gun,
 * the projectile, the range tests - so what these are about is when it pulls the trigger, where the
 * shot goes, and the fact that it stops walking to do it.
 *
 * <p>The commander is invulnerable throughout. These are tests about a shooter, and one that killed
 * its target on the second shot would end most of them before the interesting part.
 */
class SpitterTest {

    private static final float MIDDLE = 50f;

    /** Two thirds of the firing range: comfortably inside it, and nowhere near touching. */
    private static final float INSIDE = SPIT_RANGE * 0.66f;

    /** Diagonally behind it: well outside a ninety-degree arc centred on where it starts out looking. */
    private static final float BEHIND_IT = SPIT_RANGE * 0.5f;

    /** Enough for a shooter turning at a fraction of the angle per second to bring the gun round. */
    private static final int LONG_ENOUGH_TO_COME_ROUND = 300;

    /** Nowhere near enough: after these its aim has covered a fraction of a hundred-odd degrees. */
    private static final int WHILE_IT_IS_STILL_COMING_ROUND = 10;

    /** Touching it: a spitter is one across in the fixtures and so is a pickup. */
    private static final float TOUCHING = 1.5f;

    /** Long enough for an amble to have started, given the fixture's pause between waypoints. */
    private static final int LONG_ENOUGH_TO_AMBLE = 60;

    /** Inside the fixture's secondary range, so a hungry unit notices the thing at all. */
    private static final float WITHIN_SMELLING_DISTANCE = SECONDARY_RANGE * 0.6f;

    /** Enough to notice it, walk the distance and put its mouth on it. */
    private static final int LONG_ENOUGH_TO_WALK_OVER = 240;

    /** Enough to bring an aim right round from where it started and take a few steps. */
    private static final int LONG_ENOUGH_TO_TURN_ROUND = 120;

    /** Enough to cross three times its firing range at a spitter's pace, and then settle. */
    private static final int LONG_ENOUGH_TO_TAKE_A_POSITION = 180;

    /** Slack worth about one step, so a test is not about which tick the approach stopped on. */
    private static final float A_STRIDE = 2f;

    /** Past the lose range in {@code huntingBarrels()}, so it stops hunting him rather than losing sight. */
    private static final float OUT_OF_MIND = LOSE_RANGE + 10f;

    /** Back inside its detection range, and still outside the range it can shoot from. */
    private static final float JUST_NOTICEABLE = (SPIT_RANGE + DETECTION_RANGE) * 0.5f;

    /** Inside a point's rouse radius and outside the spitter's own detection range. */
    private static final float ROUSED_NOT_SEEN = (POINT_AGGRO_RADIUS + DETECTION_RANGE) * 0.5f;

    @Test
    void holdsItsGroundOnceWhatItIsHuntingIsInRange() {
        Simulation simulation = shooting();
        godMode(simulation);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        steps(simulation, 20);

        assertThat(spitter.getComponent(Position.class).x).isCloseTo(MIDDLE, Offset.offset(0.01f));
    }

    /**
     * The other half of the same rule, for a strain that does not hold ground. The shipped spitter
     * does - see {@link #movesUpToItsFiringRangeAndNoFurther()} - and this is the behaviour
     * that switch turns off.
     */
    @Test
    void closesTheDistanceWhileItIsStillTooFarAwayToShoot() {
        Simulation simulation = shooting();
        godMode(simulation);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + SPIT_RANGE * 3f, MIDDLE);

        steps(simulation, 20);

        assertThat(spitter.getComponent(Position.class).x).isGreaterThan(MIDDLE + 1f);
    }

    @Test
    void firesOnceWhatItIsHuntingComesInsideItsRange() {
        Simulation simulation = shootingStraight();
        godMode(simulation);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
    }

    @Test
    void firesNothingAtSomethingOutOfRange() {
        Simulation simulation = shootingStraight();
        godMode(simulation);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + SPIT_RANGE * 3f, MIDDLE);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /**
     * It will not empty itself into a wall. The shot would stop in the rock anyway - that rule is
     * older than this strain - so what this is about is the picture, which would read as broken.
     */
    @Test
    void willNotFireThroughRock() {
        Simulation simulation =
                shootingStraight(ground().rect(11, 0, 1, 20, Terrain.ROCK).build());
        godMode(simulation);
        simulation.spawnUnit(UnitKind.SPITTER, 52f, MIDDLE);
        simulation.spawnCommander(52f + INSIDE, MIDDLE);

        steps(simulation, 10);

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void sendsAShotStraightWhenItHasNoSpreadToDrawFrom() {
        Simulation simulation = shootingStraight();
        godMode(simulation);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        simulation.step();

        assertThat(shots(simulation).first().getComponent(Velocity.class).y).isCloseTo(0f, Offset.offset(1e-4f));
    }

    /** A cone, not a line: no two shots need go the same way, and that is what reaches the men beside him. */
    @Test
    void throwsItsShotsAroundTheAimRatherThanAlongIt() {
        Simulation simulation = shooting();
        godMode(simulation);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        float first = nextShotBearing(simulation);
        float second = nextShotBearing(simulation);
        float third = nextShotBearing(simulation);

        assertThat(first).isNotEqualTo(second);
        assertThat(second).isNotEqualTo(third);
    }

    /** Nothing leaves the cone. A spread that could miss by ninety degrees is not aiming at all. */
    @Test
    void keepsEveryShotInsideTheConeItDeclares() {
        Simulation simulation = shooting();
        godMode(simulation);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        float widest = (float) Math.sin(Math.toRadians(SPIT_SPREAD_DEGREES * 0.5));
        for (int i = 0; i < 6; i++) {
            assertThat(Math.abs(nextShotBearing(simulation))).isLessThanOrEqualTo(widest + 1e-4f);
        }
    }

    /**
     * The cone is applied by turning the shot, not by nudging where it is aimed. Turning keeps every
     * shot the same length and the cone the same width at every distance; nudging a target point
     * would make a creature wilder the further away it stood, which is backwards.
     */
    @Test
    void sendsEveryShotOutAtTheSameSpeedHoweverFarOffTrueItIs() {
        Simulation simulation = shooting();
        godMode(simulation);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        for (int i = 0; i < 4; i++) {
            Velocity velocity = nextShot(simulation);
            assertThat((float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y))
                    .isCloseTo(SPIT_SPEED, Offset.offset(0.01f));
        }
    }

    /**
     * It moves for somewhere to be, never for somebody to follow. The gap between noticing you and
     * being able to shoot you is ground it has to cross once - it notices at thirty and shoots at
     * eighteen - so a creature that refused to move at all would stand and watch, which is what
     * playtesting found.
     */
    @Test
    void movesUpToItsFiringRangeAndNoFurther() {
        Simulation simulation = watchingItsArc();
        godMode(simulation);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(MIDDLE + SPIT_RANGE * 3f, MIDDLE);

        steps(simulation, LONG_ENOUGH_TO_TAKE_A_POSITION);

        assertThat(gapBetween(spitter, commander)).isCloseTo(SPIT_RANGE, Offset.offset(A_STRIDE));
    }

    /**
     * And once it has the ground it wanted it holds it. Backing away must not pull the thing after
     * you - that is the whole of what holding ground is for, and the reason the memory of having
     * arrived outlives the memory of being in range.
     */
    @Test
    void neverFollowsOnceItIsInPosition() {
        Simulation simulation = watchingItsArc();
        godMode(simulation);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);
        steps(simulation, LONG_ENOUGH_TO_TAKE_A_POSITION);

        at(commander).set(MIDDLE + SPIT_RANGE * 3f, MIDDLE);
        steps(simulation, LONG_ENOUGH_TO_TAKE_A_POSITION);

        assertThat(at(spitter).x).isCloseTo(MIDDLE, Offset.offset(A_STRIDE));
    }

    /**
     * It walks to the distance it shoots best from, not to the edge of what it can reach. Stopping at
     * the edge leaves it ambling in and out of its own range between shots, which is worse than not
     * having walked up at all.
     */
    @Test
    void walksToTheDistanceItShootsBestFrom() {
        Simulation simulation = holdingAPreferredDistance();
        godMode(simulation);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(MIDDLE + SPIT_RANGE * 3f, MIDDLE);

        steps(simulation, LONG_ENOUGH_TO_TAKE_A_POSITION);

        assertThat(gapBetween(spitter, commander)).isCloseTo(SPIT_CLOSE_RANGE, Offset.offset(A_STRIDE));
    }

    /** Getting out of its way is not getting away. Lose it altogether and it will take a position again. */
    @Test
    void takesAPositionAgainOnceItHasLostHimAltogether() {
        Simulation simulation = huntingBarrels();
        godMode(simulation);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);
        steps(simulation, LONG_ENOUGH_TO_TAKE_A_POSITION);

        at(commander).set(MIDDLE + OUT_OF_MIND, MIDDLE);
        steps(simulation, LONG_ENOUGH_TO_TAKE_A_POSITION);
        at(commander).set(MIDDLE + JUST_NOTICEABLE, MIDDLE);
        steps(simulation, LONG_ENOUGH_TO_TAKE_A_POSITION);

        assertThat(gapBetween(spitter, commander)).isCloseTo(SPIT_RANGE, Offset.offset(A_STRIDE));
    }

    /**
     * Its field of view is a rule the player can use. Stepping round one buys a moment in which it
     * has you in range and cannot bring the gun to bear, and that moment is the whole of what
     * flanking a shooter means.
     */
    @Test
    void willNotFireAtSomethingOutsideItsFieldOfView() {
        Simulation simulation = watchingItsArc();
        godMode(simulation);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE - BEHIND_IT, MIDDLE + BEHIND_IT);

        assertThat(firesWithin(simulation, WHILE_IT_IS_STILL_COMING_ROUND)).isFalse();
    }

    @Test
    void swingsOntoItAndThenFires() {
        Simulation simulation = watchingItsArc();
        godMode(simulation);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE - BEHIND_IT, MIDDLE + BEHIND_IT);

        assertThat(firesWithin(simulation, LONG_ENOUGH_TO_COME_ROUND)).isTrue();
    }

    /**
     * What a barrel is worth to something with a gun. It swallows the thing whole rather than setting
     * it off - which is why a barrel beside a shooter is a problem and a barrel beside a grunt is a
     * gift - and what it fires afterwards goes off where it lands.
     */
    @Test
    void swallowsABarrelAndFiresShotsThatGoOff() {
        Simulation simulation = chargedByBarrels();
        godMode(simulation);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnBarrel(MIDDLE + TOUCHING, MIDDLE);
        simulation.spawnCommander(MIDDLE - INSIDE, MIDDLE);

        steps(simulation, 4);

        assertThat(count(simulation, Blast.class)).isZero();
        assertThat(spitter.getComponent(Weapon.class).kind).isEqualTo(WeaponKind.CHARGED_SPIT);
        assertThat(spitter.getComponent(Weapon.class).blastRadius).isEqualTo(CHARGED_SPIT_BLAST_RADIUS);
    }

    /** How far off true the next shot went, as the sine of its bearing - no trigonometry to read it. */
    private static float nextShotBearing(Simulation simulation) {
        Velocity velocity = nextShot(simulation);
        float length = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        return velocity.y / length;
    }

    /**
     * The next shot fired, read on the tick it leaves the muzzle.
     *
     * <p>It waits for the air to clear first, and that is not tidiness: the gun's cooldown is longer
     * than a shot's flight, so stepping a fixed number of ticks and reading whatever is in the air
     * samples one projectile twice as readily as it samples two - which looks exactly like a shooter
     * that fires the same way every time.
     */
    private static Velocity nextShot(Simulation simulation) {
        stepUntil(simulation, false);
        stepUntil(simulation, true);
        return shots(simulation).first().getComponent(Velocity.class);
    }

    /**
     * Playtesting called this out as the thing that made a spitter read as a turret: it will not
     * close and it will not follow, so with nothing else driving its legs it stood perfectly still
     * for a whole fight. It ambles inside its patch instead, while it shoots.
     */
    @Test
    void goesOnAmblingAboutWhileItShoots() {
        Simulation simulation = shufflingShooters();
        godMode(simulation);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        steps(simulation, LONG_ENOUGH_TO_AMBLE);

        assertThat(movedFrom(spitter, MIDDLE, MIDDLE)).isTrue();
    }

    /** And it is holding a line, not wandering off one: the patch it ambles in is a short walk wide. */
    @Test
    void neverAmblesFurtherThanItsOwnPatch() {
        Simulation simulation = shufflingShooters();
        godMode(simulation);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        steps(simulation, LONG_ENOUGH_TO_AMBLE * 4);

        Position at = spitter.getComponent(Position.class);
        assertThat(Math.hypot(at.x - MIDDLE, at.y - MIDDLE)).isLessThan(WANDER_RADIUS + 1f);
    }

    /**
     * The other half of what playtesting found: a spitter never went for a barrel. Three things had
     * to be true for it to - see {@code AlphaWorlds.huntingBarrels()} - and the one that was false in
     * the shipped config was that it hunted from the first tick, so it was never idle enough to be
     * hungry.
     */
    @Test
    void crossesTheGroundToABarrelItCanUse() {
        Simulation simulation = huntingBarrels();
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnBarrel(MIDDLE + WITHIN_SMELLING_DISTANCE, MIDDLE);

        steps(simulation, LONG_ENOUGH_TO_WALK_OVER);

        assertThat(spitter.getComponent(Weapon.class).kind).isEqualTo(WeaponKind.CHARGED_SPIT);
    }

    /**
     * Playtesting called this out as "they walk backward". A shooter's facing is its aim, so one
     * crossing the ground with a stale aim points at where a fight used to be while it walks the
     * other way. With nothing to shoot at, the aim follows what it is doing.
     */
    @Test
    void facesTheThingItIsWalkingOverToEat() {
        Simulation simulation = huntingBarrels();
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnBarrel(MIDDLE - WITHIN_SMELLING_DISTANCE, MIDDLE);

        steps(simulation, LONG_ENOUGH_TO_TURN_ROUND);

        assertThat(spitter.getComponent(Facing.class).x).isLessThan(0f);
    }

    /**
     * Pillar 5 for the barrel power-up. Until this, the only thing saying a spitter had swallowed one
     * was the colour of its shots - which you learn by being caught in a blast.
     */
    @Test
    void showsThatItIsCarryingABarrel() {
        Simulation simulation = chargedByBarrels();
        godMode(simulation);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnBarrel(MIDDLE + TOUCHING, MIDDLE);
        simulation.spawnCommander(MIDDLE - INSIDE, MIDDLE);

        assertThat(spitter.getComponent(Appearance.class).charged).isFalse();
        steps(simulation, 4);

        assertThat(spitter.getComponent(Appearance.class).charged).isTrue();
    }

    /**
     * It does not pursue, but it can be summoned. Taking a point is loud, and a shooter that refused
     * to walk to a place was unreachable by the one lever a level has for bringing the aliens to the
     * player - which is not what "it never chases you" was meant to mean (G0016).
     */
    @Test
    void comesToAPointBeingTakenOffIt() {
        Simulation simulation = huntingBarrels();
        godMode(simulation);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE + ROUSED_NOT_SEEN, MIDDLE);
        simulation.spawnCommander(MIDDLE, MIDDLE);

        steps(simulation, LONG_ENOUGH_TO_TURN_ROUND);

        assertThat(spitter.getComponent(Position.class).x).isLessThan(MIDDLE + ROUSED_NOT_SEEN);
    }

    /** How far apart two things are, in world units. */
    private static float gapBetween(Entity one, Entity other) {
        Position here = at(one);
        Position there = at(other);
        return (float) Math.hypot(there.x - here.x, there.y - here.y);
    }

    private static Position at(Entity entity) {
        return entity.getComponent(Position.class);
    }

    /** Whether it has left the spot it was put down on at all. */
    private static boolean movedFrom(Entity entity, float x, float y) {
        Position at = entity.getComponent(Position.class);
        return Math.abs(at.x - x) > 0.5f || Math.abs(at.y - y) > 0.5f;
    }

    /**
     * Whether anything is fired at all within this many ticks.
     *
     * <p>Stepping a fixed number and then counting what is in the air answers a different question:
     * a shot fired early has already landed by then, so "nothing in the air" and "nothing was ever
     * fired" look identical. This watches every tick instead.
     */
    private static boolean firesWithin(Simulation simulation, int ticks) {
        for (int i = 0; i < ticks; i++) {
            simulation.step();
            if (shots(simulation).size() > 0) {
                return true;
            }
        }
        return false;
    }

    /** Steps until the arena holds a shot, or holds none, whichever was asked for. */
    private static void stepUntil(Simulation simulation, boolean wanted) {
        for (int i = 0; i < 300; i++) {
            if ((shots(simulation).size() > 0) == wanted) {
                return;
            }
            simulation.step();
        }
        throw new AssertionError(wanted ? "nothing was ever fired" : "the air never cleared");
    }

    private static ImmutableArray<Entity> shots(Simulation simulation) {
        return simulation.engine().getEntitiesFor(Family.all(Projectile.class).get());
    }
}
