package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DETECTION_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LOSE_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SECONDARY_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.leapConfig;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ordering;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * What a trooper does about something in front of it.
 *
 * <p>Two rules here are load-bearing beyond this file. <strong>It engages what is in its arc and
 * through open ground</strong> (G0015), which is what makes the facing spoke a tell rather than
 * decoration; and <strong>it hardly ever swings at a leap</strong>, because answering one is the
 * commander's moment and G0013 built the clinch around it. A squad that swatted leapers reliably
 * would take that away from him without any test noticing.
 */
class TrooperCombatTest {

    private static final float MIDDLE = 50f;

    /** Far enough that he pulls nothing towards himself and captures nothing. */
    private static final float CORNER = 95f;

    /** Behind him, out of his arc and inside his sight, and far enough off not to wake a nest. */
    private static final float BEHIND = 25f;

    @Test
    void shootsWhatItCanSeeInFrontOfIt() {
        Simulation simulation = stillEnemies();
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + 15f, MIDDLE);
        facing(trooper, 1f, 0f);

        steps(simulation, 40);

        assertThat(enemy.getComponent(Health.class).current).isLessThan(ENEMY_HEALTH);
    }

    /** 1.d. A trooper plants itself to shoot; it does not walk and fire. */
    @Test
    void standsItsGroundWhileItIsShooting() {
        Simulation simulation = harmless();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(20f, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE + 15f, MIDDLE);
        facing(trooper, 1f, 0f);
        simulation.step();
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);

        Position before = at(trooper);
        float wasX = before.x;
        float wasY = before.y;
        steps(simulation, 20);

        assertThat(at(trooper).x).isCloseTo(wasX, org.assertj.core.data.Offset.offset(0.6f));
        assertThat(at(trooper).y).isCloseTo(wasY, org.assertj.core.data.Offset.offset(0.6f));
    }

    /** 1.c. In front of it, at close range - not everything within a radius of it. */
    @Test
    void takesNoNoticeOfSomethingBehindIt() {
        Simulation simulation = harmless();
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE - 12f, MIDDLE);
        facing(trooper, 1f, 0f);

        simulation.step();

        assertThat(mode(trooper)).isNotEqualTo(Trooper.Mode.FIGHT);
        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /**
     * <strong>Being hit turns a man round.</strong> Something behind him he had not noticed shoots him,
     * and he looks for it all round rather than standing there facing the other way - the place a shot
     * came from, as an alien's provocation is (G0016), never who fired it.
     */
    @Test
    void turnsOnWhateverShotItFromBehind() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE + 10f);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnNest(UnitKind.GRUNT, MIDDLE - BEHIND, MIDDLE);
        simulation.step(ordering(Order.DEFENCE));
        facing(trooper, 1f, 0f);
        steps(simulation, 30);
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.HOLD);

        shotFrom(trooper, MIDDLE - BEHIND, MIDDLE);
        steps(simulation, 3);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);
    }

    /** Looking round forgives the arc and never the wall: what hit him from behind rock stays hidden. */
    @Test
    void stillWillNotLookThroughRockForWhateverShotIt() {
        Simulation simulation =
                on(harmless().config(), ground().rect(8, 8, 1, 4, Terrain.ROCK).build());
        simulation.spawnCommander(MIDDLE, MIDDLE + 10f);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnNest(UnitKind.GRUNT, MIDDLE - BEHIND, MIDDLE);
        simulation.step(ordering(Order.DEFENCE));
        facing(trooper, 1f, 0f);
        steps(simulation, 30);

        shotFrom(trooper, MIDDLE - BEHIND, MIDDLE);
        steps(simulation, 3);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.HOLD);
    }

    /**
     * What is coming for somebody outranks what is standing still, even a little nearer: a man shoots
     * the grunt walking at him before the nest behind it.
     */
    @Test
    void shootsWhatIsComingForSomebodyBeforeWhatIsStandingStill() {
        Simulation simulation = harmless();
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnNest(UnitKind.GRUNT, MIDDLE + 12f, MIDDLE);
        simulation.spawnEnemy(MIDDLE + 22f, MIDDLE + 3f);
        facing(trooper, 1f, 0f);

        simulation.step();

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);
        assertThat(trooper.getComponent(Trooper.class).targetX).isGreaterThan(MIDDLE + 15f);
    }

    /** Two men with two things in front of them shoot one each, rather than both at the nearer one. */
    @Test
    void spreadsItsFireRatherThanEveryManShootingTheSameThing() {
        Simulation simulation = harmless();
        simulation.spawnCommander(CORNER, CORNER);
        Entity first = simulation.spawnTrooper(MIDDLE, MIDDLE - 2f);
        Entity second = simulation.spawnTrooper(MIDDLE, MIDDLE + 2f);
        simulation.spawnNest(UnitKind.GRUNT, MIDDLE + 12f, MIDDLE);
        simulation.spawnNest(UnitKind.GRUNT, MIDDLE + 16f, MIDDLE);
        facing(first, 1f, 0f);
        facing(second, 1f, 0f);

        simulation.step();

        assertThat(first.getComponent(Trooper.class).targetX).isNotEqualTo(second.getComponent(Trooper.class).targetX);
    }

    @Test
    void doesNotShootThroughRock() {
        Simulation simulation = on(
                everyStrain(AlphaWorlds.config(), strain -> strain.withContactDamage(0f)),
                ground().rect(11, 0, 1, 20, Terrain.ROCK).build());
        simulation.spawnCommander(CORNER, 5f);
        Entity trooper = simulation.spawnTrooper(52f, MIDDLE);
        simulation.spawnEnemy(63f, MIDDLE);
        facing(trooper, 1f, 0f);

        simulation.step();

        assertThat(mode(trooper)).isNotEqualTo(Trooper.Mode.FIGHT);
    }

    /**
     * The other half of the hysteresis, and the half that was missing: a fight in progress forgives
     * the arc and never the wall.
     *
     * <p>Where a man is looking is his own doing and changes twice a second as he sweeps. Rock is not
     * his doing and does not move. Forgiving both meant a squad stood in the open firing into the
     * wall whatever it had been shooting at had stepped behind.
     */
    @Test
    void stopsFightingSomethingThatStepsBehindRock() {
        Simulation simulation = on(
                everyStrain(AlphaWorlds.config(), strain -> strain.withContactDamage(0f)),
                ground().rect(11, 0, 1, 20, Terrain.ROCK).build());
        simulation.spawnCommander(CORNER, 5f);
        Entity trooper = simulation.spawnTrooper(52f, MIDDLE);
        Entity enemy = simulation.spawnEnemy(45f, MIDDLE);
        facing(trooper, -1f, 0f);
        simulation.step();
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);

        enemy.getComponent(Position.class).set(63f, MIDDLE);
        simulation.step();

        assertThat(mode(trooper)).isNotEqualTo(Trooper.Mode.FIGHT);
    }

    /**
     * Hysteresis. Starting a fight costs every test it declares; staying in one costs range alone, or
     * a trooper would drop its target the moment its own sweep carried its head past it.
     */
    @Test
    void keepsFightingSomethingThatSlipsOutOfItsArc() {
        Simulation simulation = harmless();
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + 15f, MIDDLE);
        facing(trooper, 1f, 0f);
        simulation.step();
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);

        enemy.getComponent(Position.class).set(MIDDLE - 15f, MIDDLE);
        simulation.step();

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);
    }

    /** 1.c, the other half: the fight ends and the squad gets back to what it was doing. */
    @Test
    void goesBackToTheObjectiveOnceTheFightIsOver() {
        Simulation simulation = harmless();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(20f, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + 15f, MIDDLE);
        facing(trooper, 1f, 0f);
        simulation.step();
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);

        enemy.getComponent(Damage.class).add(ENEMY_HEALTH, 0f);
        steps(simulation, 3);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.ADVANCE);
    }

    /**
     * 1.e. Rare in the shipped numbers; here the dice are rigged so the behaviour is testable at all.
     *
     * <p><strong>The probe is the trooper's hand, not a sweep still standing on the field.</strong> A
     * sweep that connects is spent on the tick it connects, and a creature leaping at the man beside
     * it now genuinely arrives - so counting strike entities afterwards counts whether the blow
     * missed, which is not what this test is about.
     */
    @Test
    void swingsAtSomethingAlreadyCommittedToALeap() {
        Simulation simulation = alwaysSwinging();
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + 2f, MIDDLE);
        facing(trooper, 1f, 0f);
        enemy.getComponent(Leap.class).phase = Leap.Phase.HESITATING;

        steps(simulation, 3);

        assertThat(swung(trooper)).isTrue();
    }

    /**
     * A sweep at something merely walking is a swat that earns no clinch (G0013). A trooper spending
     * its swing on one would be standing there with its hands busy while the thing walked into it.
     *
     * <p>The creature here can leap and never does: its trigger range is nothing, so it is walking at
     * the trooper with its {@code Leap} in hand. That is the case the rule is about - the version
     * with no {@code Leap} at all would pass by having nothing to swing at.
     */
    @Test
    void neverSwingsAtSomethingThatIsMerelyWalkingAtIt() {
        Simulation simulation = alwaysSwingingAtSomethingThatNeverLeaps();
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE + 2f, MIDDLE);
        facing(trooper, 1f, 0f);

        steps(simulation, 3);

        assertThat(swung(trooper)).isFalse();
        assertThat(count(simulation, Strike.class)).isZero();
    }

    /**
     * Enemies that hold still: they have to notice the commander before they hunt, and he is in the
     * far corner. A charging enemy walks out of its own bullet's path, which makes a test about
     * whether the shot connects a test about where the commander happened to be standing.
     */
    private static Simulation stillEnemies() {
        return new Simulation(everyStrain(
                AlphaWorlds.config(),
                strain -> strain.withContactDamage(0f)
                        .withPerception(
                                PerceptionDef.allRound(false, true, DETECTION_RANGE, LOSE_RANGE, SECONDARY_RANGE))));
    }

    /** The dice rigged so a rare thing happens every time it is offered. */
    private static Simulation alwaysSwinging() {
        SimConfig base = everyStrain(
                AlphaWorlds.config(), strain -> strain.withContactDamage(0f).withLeap(leapConfig()));
        return new Simulation(base.withChance(AiChance.SWING_AT_A_LEAP, AlphaWorlds.CERTAIN));
    }

    /** Leaping strains whose trigger range is nothing: they carry a leap and never commit to one. */
    private static Simulation alwaysSwingingAtSomethingThatNeverLeaps() {
        SimConfig base = everyStrain(
                AlphaWorlds.config(),
                strain -> strain.withContactDamage(0f).withLeap(leapConfig().withTriggerRange(0f)));
        return new Simulation(base.withChance(AiChance.SWING_AT_A_LEAP, AlphaWorlds.CERTAIN));
    }

    /** Whether this man's hands have been busy: a swing is what puts his melee on its cooldown. */
    private static boolean swung(Entity trooper) {
        return trooper.getComponent(Melee.class).remainingSeconds > 0f;
    }

    /** A hit landing on him this tick from a place, the way contact resolution records one. */
    private static void shotFrom(Entity trooper, float x, float y) {
        Damage damage = trooper.getComponent(Damage.class);
        damage.amount = 1f;
        damage.hasSource = true;
        damage.sourceX = x;
        damage.sourceY = y;
    }

    private static void facing(Entity trooper, float x, float y) {
        trooper.getComponent(Trooper.class).look(x, y);
    }

    private static Trooper.Mode mode(Entity trooper) {
        return trooper.getComponent(Trooper.class).mode;
    }

    private static Position at(Entity trooper) {
        return trooper.getComponent(Position.class);
    }
}
