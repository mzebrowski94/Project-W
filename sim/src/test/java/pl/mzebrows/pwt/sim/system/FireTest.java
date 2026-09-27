package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.AVOID_LOOKAHEAD;
import static pl.mzebrows.pwt.sim.AlphaWorlds.AVOID_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.AVOID_WEIGHT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BARREL_BLAST_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BLAST_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.FIRE_BLOBS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.FIRE_RADIUS_FACTOR;
import static pl.mzebrows.pwt.sim.AlphaWorlds.FIRE_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.burning;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ordering;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.def.AvoidanceDef;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * Ground left burning by an explosion.
 *
 * <p>Fire is <strong>ruined ground with a different declaration</strong> (G0035), not a second
 * mechanic and not a blast that lingers. Everything a slick does it does: it is a place rather than
 * an event, each circle is an ordinary body carrying an ordinary blow, and the blow is the wounding
 * kind. Three things are its own, and they are what these tests are about.
 *
 * <p><strong>It arrives by the explosion dying.</strong> A blast is a thing that dies like anything
 * else, so fire is the same death reaction asked of a different carrier - which is why nothing that
 * explodes had to learn that fire exists, and why a gun that leaves no blast leaves no fire.
 *
 * <p><strong>It is on nobody's side</strong>, as the blast it came out of was, so it burns the
 * commander who threw the grenade exactly as readily as what he threw it at.
 *
 * <p><strong>It is steered around</strong>, where venom is walked through. The body is the drawing
 * at every instant, ramps included, so a circle still growing has not reached you yet.
 */
class FireTest {

    private static final float MIDDLE = 50f;

    /** How wide one circle of fire gets after an explosion of the barrel's size. */
    private static final float FIRE_RADIUS = BARREL_BLAST_RADIUS * FIRE_RADIUS_FACTOR;

    /** The flash has to burn out before the ground it leaves is there, plus a tick to be laid. */
    private static final int UNTIL_IT_BURNS = ticksIn(BLAST_SECONDS) + 2;

    /** Enough for a blow owed this tick to land on the next one. */
    private static final int SETTLE = 3;

    private static final float LANE = 50f;
    private static final float START = 20f;
    private static final float BLOCKER = 50f;
    private static final float TARGET = 80f;

    /** Far enough short of the blocker that a unit only touching it would still be dead straight. */
    private static final int WELL_BEFORE_CONTACT = 40;

    private static final float A_CLEAR_DEVIATION = 1f;

    /** How much wider than its blast a swelling fire gets. */
    private static final float SWELLING_FACTOR = 1.5f;

    /** Outside the blast, well inside where the swelling fire ends up. */
    private static final float FIRE_REACH_OUTSIDE_THE_BLAST = BARREL_BLAST_RADIUS + 2f;

    private static final float STANDING_X = 60f;

    /** Near enough that he hears the order, far enough off the lane that he is out of the fire. */
    private static final float ORDER_REACH = 18f;

    @Test
    void burnsTheGroundWhereAnExplosionWent() {
        Simulation simulation = burningWorld();

        simulation.spawnBlast(MIDDLE, MIDDLE, BARREL_BLAST_RADIUS);
        steps(simulation, UNTIL_IT_BURNS);

        assertThat(count(simulation, Slick.class)).isEqualTo(FIRE_BLOBS);
    }

    @Test
    void drawsWhatItLeavesAsFireRatherThanAsRuinedGround() {
        Simulation simulation = burningWorld();

        simulation.spawnBlast(MIDDLE, MIDDLE, BARREL_BLAST_RADIUS);
        steps(simulation, UNTIL_IT_BURNS);

        assertThat(firstFire(simulation).getComponent(Appearance.class).look).isEqualTo(Look.FIRE);
    }

    /** A shot that leaves no blast leaves no fire. The whole of why the flamethrower is an exception. */
    @Test
    void leavesNothingBurningWhereNothingExploded() {
        Simulation simulation = burningWorld();

        simulation.spawnProjectile(MIDDLE, MIDDLE, 1f, 0f);
        steps(simulation, ticksIn(1f));

        assertThat(count(simulation, Slick.class)).isZero();
    }

    /** The one way fire is not venom: a blast is on nobody's side, and so is what it leaves. */
    @Test
    void burnsWhoeverWalksIntoItWhicheverSideHeIsOn() {
        Simulation simulation = burningWorld();
        simulation.spawnBlast(MIDDLE, MIDDLE, BARREL_BLAST_RADIUS);
        steps(simulation, UNTIL_IT_BURNS);

        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);
        steps(simulation, SETTLE);

        assertThat(commander.getComponent(Casualty.class).begun).isTrue();
        assertThat(enemy.getComponent(Dying.class).begun).isTrue();
    }

    @Test
    void putsAManStandingInItOnTheGroundRatherThanKillingHim() {
        Simulation simulation = burningWorld();
        simulation.spawnBlast(MIDDLE, MIDDLE, BARREL_BLAST_RADIUS);
        steps(simulation, UNTIL_IT_BURNS);

        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        steps(simulation, SETTLE);

        assertThat(commander.getComponent(Dying.class).begun).isFalse();
    }

    @Test
    void goesOutOnceItHasBurnedDown() {
        Simulation simulation = burningWorld();

        simulation.spawnBlast(MIDDLE, MIDDLE, BARREL_BLAST_RADIUS);
        steps(simulation, UNTIL_IT_BURNS + ticksIn(FIRE_SECONDS) + 2);

        assertThat(count(simulation, Slick.class)).isZero();
    }

    @Test
    void leansOffBurningGroundAsReadilyAsOffARock() {
        Simulation simulation = avoidingFire();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);

        simulation.spawnBlast(BLOCKER, LANE, BARREL_BLAST_RADIUS);
        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(offLane(enemy)).isGreaterThan(A_CLEAR_DEVIATION);
    }

    /**
     * The body is the drawing at every instant, so a circle that is still swelling has not reached as
     * far as it is going to - which is what keeps "drawn and dangerous are the same interval" true of
     * something that changes size.
     */
    @Test
    void swellsAndThenCollapsesRatherThanBeingOneCircleThroughout() {
        Simulation simulation = new Simulation(ramped());
        simulation.spawnBlast(MIDDLE, MIDDLE, BARREL_BLAST_RADIUS);
        steps(simulation, UNTIL_IT_BURNS);

        float whenItCatches = radiusOfFire(simulation);
        steps(simulation, ticksIn(FIRE_SECONDS * 0.5f));
        float atItsHeight = radiusOfFire(simulation);
        steps(simulation, ticksIn(FIRE_SECONDS * 0.45f));
        float asItDies = radiusOfFire(simulation);

        assertThat(whenItCatches).isLessThan(atItsHeight);
        assertThat(atItsHeight).isCloseTo(FIRE_RADIUS, Offset.offset(1e-3f));
        assertThat(asItDies).isLessThan(atItsHeight);
    }

    /** A blast against a wall throws its fire round the point it went off at, and some of that is wall. */
    @Test
    void leavesNothingBurningInsideRock() {
        Simulation simulation = new Simulation(burning(config()), wall());

        simulation.spawnBlast(MIDDLE, MIDDLE, BARREL_BLAST_RADIUS);
        steps(simulation, UNTIL_IT_BURNS);

        TileMap map = simulation.map();
        for (Entity fire : simulation
                .engine()
                .getEntitiesFor(Family.all(Slick.class, Position.class).get())) {
            Position at = fire.getComponent(Position.class);
            assertThat(map.passableAt(at.x, at.y)).isTrue();
        }
    }

    /**
     * <strong>A man standing still steps off ground that is about to burn him.</strong> Avoidance used
     * to be about a course, so a man with no course - holding, securing, standing to shoot - watched
     * fire swell up round his boots. Standing is not a reason to stand in it.
     */
    @Test
    void stepsAManStandingStillOffGroundThatIsCatchingFire() {
        Simulation simulation = new Simulation(swelling(avoidingSquad()));
        simulation.spawnCommander(STANDING_X, LANE + ORDER_REACH);
        Entity trooper = simulation.spawnTrooper(STANDING_X, LANE);
        simulation.step(ordering(Order.DEFENCE));

        simulation.spawnBlast(STANDING_X - FIRE_REACH_OUTSIDE_THE_BLAST, LANE, BARREL_BLAST_RADIUS);
        boolean wentDown = false;
        for (int tick = 0; tick < ticksIn(FIRE_SECONDS); tick++) {
            simulation.step();
            wentDown |= trooper.getComponent(Casualty.class).begun;
        }

        assertThat(wentDown).isFalse();
    }

    /** Fire on, and nothing on the field hits back, so a test about burning ground is only about that. */
    private static Simulation burningWorld() {
        return new Simulation(burning(everyStrain(config(), strain -> strain.withContactDamage(0f))));
    }

    private static Simulation avoidingFire() {
        return new Simulation(burning(everyStrain(
                config(),
                strain -> strain.withContactDamage(0f)
                        .withAvoidance(new AvoidanceDef(true, AVOID_RANGE, AVOID_LOOKAHEAD, AVOID_WEIGHT)))));
    }

    /** A squad that steers, so a test about what it gives room to has somebody to give it. */
    private static SimConfig avoidingSquad() {
        SimConfig config = everyStrain(config(), strain -> strain.withContactDamage(0f));
        return config.withTrooper(
                config.trooper().withAvoidance(new AvoidanceDef(true, AVOID_RANGE, AVOID_LOOKAHEAD, AVOID_WEIGHT)));
    }

    /**
     * Fire that swells over half its life to half as wide again as the blast that left it, all of it
     * on the spot the blast went off - so it reaches ground the blast never touched, slowly enough
     * that a man who moves is not caught.
     */
    private static SimConfig swelling(SimConfig config) {
        return burning(config)
                .withFire(burning(config)
                        .fire()
                        .withRadiusFactor(SWELLING_FACTOR)
                        .withSpreadFactor(0f)
                        .withGrowFraction(0.5f));
    }

    /** Fire that spends a quarter of its life swelling and another quarter collapsing again. */
    private static SimConfig ramped() {
        SimConfig base = burning(everyStrain(config(), strain -> strain.withContactDamage(0f)));
        return base.withFire(base.fire().withGrowFraction(0.25f).withFadeFraction(0.25f));
    }

    private static float radiusOfFire(Simulation simulation) {
        return firstFire(simulation).getComponent(Body.class).radius;
    }

    /** The first circle of a spatter is always laid on the burst itself, so it is the one to read. */
    private static Entity firstFire(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Slick.class, Body.class).get())
                .first();
    }

    private static float offLane(Entity entity) {
        return Math.abs(entity.getComponent(Position.class).y - LANE);
    }

    /** A rock column across the middle, with open ground either side of it. */
    private static TileMap wall() {
        return ground().rect(10, 0, 1, ARENA_TILES, Terrain.ROCK).build();
    }
}
