package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BOSS_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TURRET_MUZZLE_OFFSET;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Barrage;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.BarrageDef;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PatternDef;
import pl.mzebrows.pwt.sim.def.ShakeDef;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.SlickDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The boss (G0048): it never walks, and it shoots a cycle of patterns - each told by a wind-up, each
 * followed by a rest, and nothing about any of it rolled.
 */
class BarrageTest {

    private static final float MIDDLE = 50f;

    /** Inside the fixture's detection range, and well clear of its body and its claws. */
    private static final float NEAR = 14f;

    /**
     * Where the commander stands for a test that counts shots: up and to the left, between two lines of
     * a twelve-shot ring, so none of them is spent on him before it is counted.
     */
    private static final float OFF_THE_LINES = NEAR * 0.7071f;

    private static final float WIND_UP = 0.5f;

    private static final float REST = 0.5f;

    private static final float INTERVAL = 0.25f;

    private static final int RING_SHOTS = 12;

    private static final float TURN_DEGREES = 30f;

    private static final float FAN_DEGREES = 60f;

    private static final float ENRAGED_BELOW = 0.5f;

    private static final float ENRAGED_FACTOR = 0.5f;

    private static final Offset<Float> CLOSE = Offset.offset(1e-3f);

    /**
     * How far past its stop a shot may get before it is gone: two ticks of the fixture's flight - the one it notices
     * it has flown far enough on, and the one it is retired on.
     */
    private static final float STEP_SLACK = 2f;

    /** A giant's reach: nearer than {@link #NEAR}, so a man standing there is hunted but not yet shot at. */
    private static final float REACH = 10f;

    private static final float GIANT_SPEED = 4f;

    /** How far off a nest that rouses it stands: inside the nest's earshot, outside its own detection. */
    private static final float ROUSED_FROM = 25f;

    private static final float ROAR_STRENGTH = 2f;

    private static final float ROAR_RADIUS = 30f;

    /** Until the first salvo of a pattern the barrage has just started winding up: one tick to start, then the wind-up. */
    private static final int UNTIL_THE_FIRST_SALVO = 1 + ticksIn(WIND_UP);

    // ---- holding fire, and telling what is coming ----

    @Test
    void holdsFireWithNobodyToShootAt() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO * 4);

        assertThat(alienShots(simulation)).isEmpty();
    }

    @Test
    void showsItIsWindingUpBeforeAnythingLeaves() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO - 1);

        assertThat(alienShots(simulation)).isEmpty();
        assertThat(boss.getComponent(Appearance.class).state).isEqualTo(Appearance.State.WINDING_UP);

        simulation.step();

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);
    }

    /**
     * A nest being hit sends it to look (G0016), but that place is trouble, not somebody who hurt it: it shoots
     * men, never the nest it was sent to.
     */
    @Test
    void holdsFireAtANestItWasRousedTo() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        Entity nest = simulation.spawnNest(UnitKind.GRUNT, MIDDLE + ROUSED_FROM, MIDDLE);

        nest.getComponent(Damage.class).land(1, Harm.KILL);
        steps(simulation, UNTIL_THE_FIRST_SALVO * 4);

        assertThat(alienShots(simulation)).hasSize(0);
    }

    // ---- which way it looks ----

    /** It rises facing east, as everything does; a man to its west turns it round, ring or not. */
    @Test
    void facesWhoeverItIsHunting() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - NEAR, MIDDLE);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, 2);

        Facing facing = boss.getComponent(Facing.class);
        assertThat(facing.x).isCloseTo(-1f, CLOSE);
        assertThat(facing.y).isCloseTo(0f, CLOSE);
    }

    /** With nobody in range it faces the place it was last hurt from, which is where it shoots. */
    @Test
    void facesThePlaceItWasHurtFromWithNobodyInRange() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        boss.getComponent(Perception.class).provokeAt(MIDDLE, MIDDLE - NEAR);

        simulation.step();

        Facing facing = boss.getComponent(Facing.class);
        assertThat(facing.x).isCloseTo(0f, CLOSE);
        assertThat(facing.y).isCloseTo(-1f, CLOSE);
    }

    // ---- the shapes ----

    /** A full ring: evenly all the way round, so the shots cancel out and none leaves twice. */
    @Test
    void firesARingEvenlyAllTheWayRound() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO);

        float sumX = 0f;
        float sumY = 0f;
        for (Entity shot : alienShots(simulation)) {
            Velocity velocity = shot.getComponent(Velocity.class);
            sumX += velocity.x;
            sumY += velocity.y;
        }
        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);
        assertThat(sumX).isCloseTo(0f, Offset.offset(0.01f));
        assertThat(sumY).isCloseTo(0f, Offset.offset(0.01f));
    }

    /** An aimed fan leaves centred on whoever it is hunting, and its two edges are the arc apart. */
    @Test
    void centresAnAimedFanOnWhoeverItIsHunting() {
        Simulation simulation = new Simulation(barraging(fan(3)));
        simulation.spawnCommander(MIDDLE + NEAR, MIDDLE + NEAR);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO);

        List<Entity> shots = alienShots(simulation);
        assertThat(shots).hasSize(3);
        float[] middle = direction(shots.get(1));
        float diagonal = (float) Math.sqrt(0.5);
        assertThat(middle[0]).isCloseTo(diagonal, CLOSE);
        assertThat(middle[1]).isCloseTo(diagonal, CLOSE);
        float[] left = direction(shots.getFirst());
        float[] right = direction(shots.getLast());
        float cosine = left[0] * right[0] + left[1] * right[1];
        assertThat(cosine).isCloseTo((float) StrictMath.cos(StrictMath.toRadians(FAN_DEGREES)), CLOSE);
    }

    /**
     * A locked pattern keeps the bearing it had when its wind-up ended (G0073): the line is told by the tint, and a
     * sidestep clears it.
     */
    @Test
    void keepsALockedLineOnTheBearingItWoundUpOn() {
        Simulation simulation = new Simulation(barraging(stream(true)));
        Entity commander = simulation.spawnCommander(MIDDLE + NEAR, MIDDLE);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        steps(simulation, UNTIL_THE_FIRST_SALVO);

        sidestep(commander);
        steps(simulation, ticksIn(INTERVAL) * 2);

        float[] last = direction(newest(simulation));
        assertThat(last[0]).isCloseTo(1f, CLOSE);
        assertThat(last[1]).isCloseTo(0f, CLOSE);
    }

    /** An aimed pattern that is not locked follows him salvo by salvo, as the boss's stream does. */
    @Test
    void followsWhoeverItHuntsSalvoBySalvoWhenItIsNotLocked() {
        Simulation simulation = new Simulation(barraging(stream(false)));
        Entity commander = simulation.spawnCommander(MIDDLE + NEAR, MIDDLE);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        steps(simulation, UNTIL_THE_FIRST_SALVO);

        sidestep(commander);
        steps(simulation, ticksIn(INTERVAL) * 2);

        assertThat(direction(newest(simulation))[1]).isGreaterThan(0.5f);
    }

    /**
     * A stepped pattern stops each salvo a step further along the way to its aim, the last at it (G0073): what its
     * shots leave on the ground is a trail from its mouth to whoever it aimed at.
     */
    @Test
    void laysAStepFurtherEachSalvoTheLastReachingItsAim() {
        PatternDef trail = new PatternDef(true, WeaponKind.VENOM, 1, 0f, true, 0f, 3, INTERVAL, 0f, true, true);
        SimConfig config = barraging(trail);
        SlickDef puddle = new SlickDef(1, 0.5f, 0f, 30f, 30f, 0, 0f, 0f, 0f, Look.SLICK, 0f, Harm.NONE, 1f, 0f, 1f);
        Simulation simulation = new Simulation(
                config.withWeapons(config.weapons().map(WeaponKind.VENOM, gun -> gun.withSlicks(puddle))));
        simulation.spawnCommander(MIDDLE + NEAR, MIDDLE);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO + ticksIn(INTERVAL) * 2 + ticksIn(REST) - 1);

        List<Float> stops = new ArrayList<>();
        for (Entity slick :
                simulation.engine().getEntitiesFor(Family.all(Slick.class).get())) {
            stops.add(slick.getComponent(Position.class).x - MIDDLE);
        }
        stops.sort(null);
        assertThat(stops).hasSize(3);
        assertThat(stops.get(0)).isCloseTo(NEAR / 3f, Offset.offset(STEP_SLACK));
        assertThat(stops.get(1)).isCloseTo(NEAR * 2f / 3f, Offset.offset(STEP_SLACK));
        assertThat(stops.get(2)).isGreaterThan(NEAR * 2f / 3f + STEP_SLACK);
    }

    // ---- the cycle ----

    @Test
    void goesOnToTheNextPatternAfterItsRest() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1), fan(3)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        Barrage barrage = boss.getComponent(Barrage.class);

        steps(simulation, UNTIL_THE_FIRST_SALVO);

        assertThat(barrage.phase).isEqualTo(Barrage.Phase.RESTING);

        steps(simulation, ticksIn(REST));

        assertThat(barrage.phase).isEqualTo(Barrage.Phase.WINDING_UP);
        assertThat(barrage.pattern).isEqualTo(1);
    }

    @Test
    void firesEverySalvoOfAPatternAtItsInterval() {
        int salvos = 3;
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, salvos)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO + ticksIn(INTERVAL) - 1);

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);

        simulation.step();

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS * 2);
    }

    /**
     * A turning pattern turns one way the first time through the cycle and the other way the next, so a
     * spiral is never learnt as a single direction to circle in.
     */
    @Test
    void turnsThePatternTheOtherWayEachTimeRound() {
        Simulation simulation = new Simulation(barraging(ring(1, 2)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        int cycle = ticksIn(INTERVAL) + ticksIn(REST) + ticksIn(WIND_UP);

        steps(simulation, UNTIL_THE_FIRST_SALVO + ticksIn(INTERVAL));
        float firstTimeRound = direction(newest(simulation))[1];
        steps(simulation, cycle);
        float secondTimeRound = direction(newest(simulation))[1];

        assertThat(firstTimeRound).isPositive();
        assertThat(secondTimeRound).isNegative();
        assertThat(secondTimeRound).isCloseTo(-firstTimeRound, CLOSE);
    }

    // ---- what does not stop it, and what makes it faster ----

    /** A fist holds a grunt still; a boss held still by every swing would be beaten by one key. */
    @Test
    void keepsFiringWhileSomethingHoldsItStill() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        steps(simulation, 2);

        boss.getComponent(Stunned.class).apply(UNTIL_THE_FIRST_SALVO * 10, 0f);
        steps(simulation, UNTIL_THE_FIRST_SALVO);

        assertThat(alienShots(simulation)).isNotEmpty();
    }

    @Test
    void windsUpFasterOnceItIsBadlyHurt() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        boss.getComponent(Health.class).current = (int) (BOSS_HEALTH * ENRAGED_BELOW * 0.5f);

        steps(simulation, 1 + Math.round(ticksIn(WIND_UP) * ENRAGED_FACTOR));

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);
    }

    // ---- standing still, and saying how much is left ----

    @Test
    void neverWalksAndCannotBePushed() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - NEAR, MIDDLE);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, 90, new CommanderInput(1f, 0f, MIDDLE, MIDDLE, false, false));

        Position at = boss.getComponent(Position.class);
        assertThat(at.x).isEqualTo(MIDDLE);
        assertThat(at.y).isEqualTo(MIDDLE);
    }

    /** Pillar 5: nobody can count thousands of points of damage by eye, so the core is what is left. */
    @Test
    void showsHowMuchOfItIsLeftOnItsCharge() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        boss.getComponent(Health.class).current = BOSS_HEALTH / 4;

        simulation.step();

        assertThat(boss.getComponent(Appearance.class).charge).isCloseTo(0.25f, CLOSE);
    }

    // ---- a giant's barrage: one that walks (G0071) ----

    @Test
    void startsAPatternOnlyOnceWhatItHuntsIsWithinReach() {
        Simulation simulation = new Simulation(giant());
        simulation.spawnCommander(MIDDLE - NEAR * 3f, MIDDLE);
        Entity giant = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        giant.getComponent(Perception.class).provokeAt(MIDDLE - NEAR * 3f, MIDDLE);

        steps(simulation, 2);

        assertThat(giant.getComponent(Barrage.class).phase).isEqualTo(Barrage.Phase.IDLE);

        Simulation near = new Simulation(giant());
        near.spawnCommander(MIDDLE - OFF_THE_LINES * 0.6f, MIDDLE + OFF_THE_LINES * 0.6f);
        Entity close = near.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(near, 2);

        assertThat(close.getComponent(Barrage.class).phase).isEqualTo(Barrage.Phase.WINDING_UP);
    }

    @Test
    void standsStillWhileItWindsUpAndFiresAndWalksWhileItRests() {
        Simulation simulation = new Simulation(giant());
        simulation.spawnCommander(MIDDLE - REACH * 0.8f, MIDDLE);
        Entity giant = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        steps(simulation, 2);
        float x = giant.getComponent(Position.class).x;

        steps(simulation, ticksIn(WIND_UP) - 2);

        assertThat(giant.getComponent(Barrage.class).phase).isEqualTo(Barrage.Phase.WINDING_UP);
        assertThat(giant.getComponent(Position.class).x).isEqualTo(x);

        steps(simulation, ticksIn(REST));

        assertThat(giant.getComponent(Position.class).x).isLessThan(x);
    }

    @Test
    void shakesTheGroundWhenItStartsWindingUp() {
        Simulation simulation = new Simulation(giant());
        simulation.spawnCommander(MIDDLE - REACH * 0.8f, MIDDLE);
        Entity giant = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, 2);

        Shake shake = giant.getComponent(Shake.class);
        assertThat(shake.strength).isEqualTo(ROAR_STRENGTH);
        assertThat(shake.radius).isEqualTo(ROAR_RADIUS);
        assertThat(shake.fade()).isPositive();
    }

    @Test
    void breaksOffThePatternWhenADeclaredStunLandsAndWindsItUpAgainAfter() {
        Simulation simulation = new Simulation(giant());
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES * 0.6f, MIDDLE + OFF_THE_LINES * 0.6f);
        Entity giant = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        steps(simulation, UNTIL_THE_FIRST_SALVO - 2);

        giant.getComponent(Stunned.class).daze(UNTIL_THE_FIRST_SALVO);
        steps(simulation, UNTIL_THE_FIRST_SALVO);

        assertThat(alienShots(simulation)).isEmpty();

        steps(simulation, UNTIL_THE_FIRST_SALVO + 1);

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);
    }

    /** A slam is read at a glance (pillar 5): the ground its shots cover, drawn round it, fading, after the salvo. */
    @Test
    void showsTheCircleItsShotsReachForAMomentAfterASalvoThatSaysSo() {
        PatternDef slam = ring(RING_SHOTS, 1).withBurstSeconds(REST);
        Simulation simulation = new Simulation(barraging(slam));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO - 1);

        assertThat(boss.getComponent(Appearance.class).burst).isZero();

        steps(simulation, 2);

        Appearance appearance = boss.getComponent(Appearance.class);
        assertThat(appearance.burst).isGreaterThan(boss.getComponent(Body.class).radius);
        assertThat(appearance.burstFade).isBetween(0.5f, 1f);

        steps(simulation, ticksIn(REST));

        assertThat(boss.getComponent(Appearance.class).burst).isZero();
    }

    /** A roar is always answered (G0071): a man who walks out of reach and out of mind while it winds up is still fired at. */
    @Test
    void finishesAPatternItHasStartedWhenWhatItHuntsGetsAway() {
        Simulation simulation = new Simulation(giant());
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES * 0.6f, MIDDLE + OFF_THE_LINES * 0.6f);
        Entity giant = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        steps(simulation, 2);

        giant.getComponent(Perception.class).aggro = false;
        giant.getComponent(Perception.class).detectionRange = 0f;
        steps(simulation, UNTIL_THE_FIRST_SALVO);

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);
    }

    /** A stagger is not a declared stun: a fist does not stop a giant's roar any more than a boss's barrage. */
    @Test
    void keepsWindingUpThroughAStagger() {
        Simulation simulation = new Simulation(giant());
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES * 0.6f, MIDDLE + OFF_THE_LINES * 0.6f);
        Entity giant = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        steps(simulation, 2);

        giant.getComponent(Stunned.class).apply(UNTIL_THE_FIRST_SALVO * 10, 0f);
        steps(simulation, UNTIL_THE_FIRST_SALVO);

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);
    }

    // ---- through the one trigger ----

    /**
     * A boss fires through the same muzzle as every other shooter: a pattern whose gun names an
     * offset leaves from beside the bearing rather than on it, which a barrage that worked out its own
     * clearance never did.
     */
    @Test
    void firesOutOfTheMuzzleItsGunNamesLikeEveryOtherShooter() {
        PatternDef aimedTwin =
                new PatternDef(true, WeaponKind.TURRET_GUN, 1, 0f, true, 0f, 1, INTERVAL, 0f, false, false);
        Simulation simulation = new Simulation(barraging(aimedTwin));
        simulation.spawnCommander(MIDDLE + NEAR, MIDDLE);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO);

        assertThat(alienShots(simulation)).hasSize(1);
        assertThat(newest(simulation).getComponent(Position.class).y).isCloseTo(MIDDLE - TURRET_MUZZLE_OFFSET, CLOSE);
    }

    private static SimConfig barraging(PatternDef first) {
        return barraging(first, PatternDef.off());
    }

    private static SimConfig barraging(PatternDef first, PatternDef second) {
        return config().withUnit(
                        UnitKind.BOSS_GRUNT, boss -> boss.withContactDamage(0).withBarrage(barrage(first, second)));
    }

    private static BarrageDef barrage(PatternDef first, PatternDef second) {
        return new BarrageDef(
                true,
                WIND_UP,
                REST,
                ENRAGED_BELOW,
                ENRAGED_FACTOR,
                first,
                second,
                PatternDef.off(),
                PatternDef.off(),
                0f,
                false,
                false,
                ShakeDef.off());
    }

    /** A giant's barrage (G0071): it walks, starts a pattern only within reach, stands for it, roars and is stunned out of it. */
    private static SimConfig giant() {
        BarrageDef barrage = barrage(ring(RING_SHOTS, 1), PatternDef.off())
                .withReach(REACH)
                .withHoldsStill(true)
                .withStunCancels(true)
                .withRoar(new ShakeDef(ROAR_STRENGTH, ROAR_RADIUS, WIND_UP));
        return config().withUnit(
                        UnitKind.BOSS_GRUNT,
                        boss -> boss.withContactDamage(0).withSpeed(GIANT_SPEED).withBarrage(barrage));
    }

    private static PatternDef ring(int shots, int salvos) {
        return new PatternDef(
                true, WeaponKind.SPEW, shots, 360f, false, TURN_DEGREES, salvos, INTERVAL, 0f, false, false);
    }

    /** Three single shots down one line, aimed, locked or not. */
    private static PatternDef stream(boolean locked) {
        return new PatternDef(true, WeaponKind.SPEW, 1, 0f, true, 0f, 3, INTERVAL, 0f, locked, false);
    }

    /** Moves him off the line it is firing down, to its north, between two salvos. */
    private static void sidestep(Entity commander) {
        commander.getComponent(Position.class).x = MIDDLE;
        commander.getComponent(Position.class).y = MIDDLE + NEAR;
    }

    private static PatternDef fan(int shots) {
        return new PatternDef(true, WeaponKind.SPEW, shots, FAN_DEGREES, true, 0f, 1, INTERVAL, 0f, false, false);
    }

    private static List<Entity> alienShots(Simulation simulation) {
        List<Entity> shots = new ArrayList<>();
        for (Entity entity : simulation
                .engine()
                .getEntitiesFor(Family.all(Projectile.class, Faction.class).get())) {
            if (entity.getComponent(Faction.class).side == Side.ALIEN) {
                shots.add(entity);
            }
        }
        return shots;
    }

    private static Entity newest(Simulation simulation) {
        return alienShots(simulation).getLast();
    }

    private static float[] direction(Entity shot) {
        Velocity velocity = shot.getComponent(Velocity.class);
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        return new float[] {velocity.x / speed, velocity.y / speed};
    }
}
