package pl.mzebrows.pwt.sim.system.death;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.AVOID_LOOKAHEAD;
import static pl.mzebrows.pwt.sim.AlphaWorlds.AVOID_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.AVOID_WEIGHT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BLAST_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SLICK_BLOBS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TOXIC_BLOOD_CIRCLES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.hold;
import static pl.mzebrows.pwt.sim.AlphaWorlds.inRuinedGround;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.Arrays;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.def.AvoidanceDef;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * The swarm's toxic blood (G0076): every alien leaves a spatter of its own venom where it dies, and a man goes round
 * one only on a chance.
 *
 * <p><strong>It is the alien's own death</strong>, so it arrives through the one death reaction (G0018), however the
 * alien dies, and it is counted as it dies - one already on the field when the swarm adapts leaves one too. <strong>It is
 * the alien's, not its gun's</strong>: a frog's shot spatters exactly as it always did. <strong>And a man heeds a
 * spatter whole or not at all</strong>, on a draw keyed from him and the spatter, so it is the same answer on every tick
 * and on every machine (pillars 6 and 7).
 */
class ToxicBloodTest {

    private static final float X = 50f;
    private static final float Y = 50f;

    /** A blast wide enough to kill whatever stands at its middle. */
    private static final float KILLING_BLAST = 3f;

    /** Long enough for the blast to land and the alien's throes to end - the moment it is dead. */
    private static final int UNTIL_IT_HAS_FALLEN = ticksIn(ENEMY_THROES + BLAST_SECONDS) + 2;

    /** How far from where it fell a circle may lie: the blood's own spread, and a little slack. */
    private static final float WITHIN_THE_SPATTER = config().blood().spread() + 0.5f;

    /** Long enough for a shot of venom to fly its whole reach and burst. */
    private static final int UNTIL_IT_RUNS_OUT = 50;

    private static final float SET_OFF_X = 30f;
    private static final float GOAL_X = 75f;
    private static final float CORNER = 5f;

    /** Long enough for a man to walk from the start to the goal, the long way round. */
    private static final int THE_WALK = 150;

    /** Enough men for the share who go round to say something about the chance. */
    private static final int MEN = 200;

    private static final float HEEDED = 0.85f;

    /** A spatter that lies for as long as a test takes to walk past it. */
    private static final float LASTING_SECONDS = 60f;

    /** Four standard deviations of two hundred draws at 0.85, either side - so "every man" or "no man" fails. */
    private static final float TOLERANCE = 0.1f;

    /** Men sent one at a time past the same spatter in the same world, each with a handle of his own. */
    private static final int MEN_AT_ONE_SPATTER = 20;

    /** A coin: in one world at one spatter, some of twenty men must go round it and some walk in. */
    private static final float EVEN = 0.5f;

    /** Where the things that only take up handles are put, far from the lane. */
    private static final float OUT_OF_THE_WAY_Y = 90f;

    @Test
    void leavesASpatterOfVenomOnItsOwnSideWhereAnAlienFell() {
        Simulation simulation = new Simulation(config());
        hold(simulation, UpgradeKind.TOXIC_BLOOD, 1);

        killAt(simulation);

        assertThat(count(simulation, Slick.class)).isEqualTo(TOXIC_BLOOD_CIRCLES);
        for (Entity circle : circles(simulation)) {
            Position at = circle.getComponent(Position.class);
            assertThat(at.x).isCloseTo(X, Offset.offset(WITHIN_THE_SPATTER));
            assertThat(at.y).isCloseTo(Y, Offset.offset(WITHIN_THE_SPATTER));
            assertThat(circle.getComponent(Faction.class).side).isEqualTo(Side.ALIEN);
        }
    }

    @Test
    void leavesNothingWhereAnAlienFellUntilTheSwarmHasToxicBlood() {
        Simulation simulation = new Simulation(config());

        killAt(simulation);

        assertThat(count(simulation, Slick.class)).isZero();
    }

    @Test
    void leavesABiggerSpatterAtTheSecondRank() {
        Simulation simulation = new Simulation(config());
        hold(simulation, UpgradeKind.TOXIC_BLOOD, 2);

        killAt(simulation);

        assertThat(count(simulation, Slick.class)).isEqualTo(2 * TOXIC_BLOOD_CIRCLES);
    }

    @Test
    void leavesAFrogsSpatterAsItWasWhetherOrNotTheSwarmHasToxicBlood() {
        Simulation simulation = new Simulation(config());
        hold(simulation, UpgradeKind.TOXIC_BLOOD, 2);

        simulation.spawnProjectile(X, Y, 1f, 0f, WeaponKind.VENOM, Side.ALIEN);
        steps(simulation, UNTIL_IT_RUNS_OUT);

        assertThat(count(simulation, Slick.class)).isEqualTo(SLICK_BLOBS);
    }

    @Test
    void isWalkedStraightThroughByAManWhoDoesNotHeedIt() {
        SimConfig world = heeding(0f);
        Spot onHisLine = onHisLine(world);

        Walk bled = walk(world, onHisLine, true, 0);

        assertThat(bled.ticksIn()).isPositive();
        assertThat(walkedHisOwnLineInto(bled, walk(world, onHisLine, false, 0))).isTrue();
    }

    @Test
    void isGoneRoundWholeByAManWhoHeedsIt() {
        SimConfig world = heeding(1f);

        assertThat(walk(world, onHisLine(world), true, 0).ticksIn()).isZero();
    }

    @Test
    void decidesForEachManHimselfAndForAllOfTheSpatterAtOnce() {
        SimConfig world = heeding(EVEN);
        Spot onHisLine = onHisLine(world);
        int wentRound = 0;
        for (int before = 0; before < MEN_AT_ONE_SPATTER; before++) {
            Walk bled = walk(world, onHisLine, true, before);
            if (bled.ticksIn() == 0) {
                wentRound++;
            } else {
                assertThat(walkedHisOwnLineInto(bled, walk(world, onHisLine, false, before)))
                        .as("man after %d others leant off some of it", before)
                        .isTrue();
            }
        }

        assertThat(wentRound).isPositive().isLessThan(MEN_AT_ONE_SPATTER);
    }

    @Test
    void isGoneRoundByAboutAsManyMenAsItsChanceAndTheSameOnesOnAReplay() {
        int wentRound = 0;
        for (int man = 0; man < MEN; man++) {
            SimConfig world = heeding(HEEDED).withSeed(man + 1L);
            Spot onHisLine = onHisLine(world);
            boolean round = walk(world, onHisLine, true, 0).ticksIn() == 0;
            if (round) {
                wentRound++;
            }
            assertThat(walk(world, onHisLine, true, 0).ticksIn() == 0)
                    .as("seed %d replayed", man + 1)
                    .isEqualTo(round);
        }

        assertThat(wentRound / (float) MEN).isCloseTo(HEEDED, Offset.offset(TOLERANCE));
    }

    /**
     * An alien killed where it stands, by a blast that leaves no fire in this fixture - so whatever lies there after
     * is what the alien left.
     */
    private static void killAt(Simulation simulation) {
        killAt(simulation, new Spot(X, Y));
    }

    private static void killAt(Simulation simulation, Spot spot) {
        simulation.spawnEnemy(spot.x(), spot.y());
        simulation.spawnBlast(spot.x(), spot.y(), KILLING_BLAST);
        steps(simulation, UNTIL_IT_HAS_FALLEN);
    }

    private static Iterable<Entity> circles(Simulation simulation) {
        return simulation.engine().getEntitiesFor(Family.all(Slick.class).get());
    }

    /** A squad that steers, aliens that hit nothing, and a spatter heeded on this chance. */
    private static SimConfig heeding(float chance) {
        SimConfig harmless = everyStrain(config(), strain -> strain.withContactDamage(0));
        return harmless.withTrooper(harmless.trooper()
                        .withAvoidance(new AvoidanceDef(true, AVOID_RANGE, AVOID_LOOKAHEAD, AVOID_WEIGHT)))
                .withBlood(harmless.blood()
                        .withHeeded(chance)
                        .withSecondsMax(LASTING_SECONDS)
                        .withSeconds(LASTING_SECONDS));
    }

    private record Spot(float x, float y) {}

    /**
     * Where the man's own line crosses the middle of the field, with nothing lying on it: a man closes on a point on
     * a bearing of his own rather than dead straight, so a spatter has to be put where he walks to be in his way.
     */
    private static Spot onHisLine(SimConfig world) {
        Walk unbled = walk(world, new Spot(X, Y), false, 0);
        int nearest = 0;
        for (int tick = 0; tick < THE_WALK; tick++) {
            if (Math.abs(unbled.xs()[tick] - X) < Math.abs(unbled.xs()[nearest] - X)) {
                nearest = tick;
            }
        }
        return new Spot(unbled.xs()[nearest], unbled.ys()[nearest]);
    }

    /**
     * An alien killed at {@code spot} - with toxic blood, or not - and then a man sent from the left to a point on the
     * right, after {@code before} things far off the lane that do nothing but take up handles: the same world, the
     * same spatter, a different man.
     */
    private static Walk walk(SimConfig world, Spot spot, boolean toxic, int before) {
        Simulation simulation = new Simulation(world);
        simulation.spawnCommander(CORNER, CORNER);
        if (toxic) {
            hold(simulation, UpgradeKind.TOXIC_BLOOD, 1);
        }
        killAt(simulation, spot);
        for (int other = 0; other < before; other++) {
            simulation.spawnObstacle(CORNER + other, OUT_OF_THE_WAY_Y);
        }
        simulation.spawnControlPoint(GOAL_X, Y);
        Entity man = simulation.spawnTrooper(SET_OFF_X, Y);
        int ticksIn = 0;
        int firstIn = THE_WALK;
        float[] xs = new float[THE_WALK];
        float[] ys = new float[THE_WALK];
        for (int tick = 0; tick < THE_WALK; tick++) {
            simulation.step();
            Position at = man.getComponent(Position.class);
            xs[tick] = at.x;
            ys[tick] = at.y;
            if (inRuinedGround(simulation, man)) {
                firstIn = Math.min(firstIn, tick);
                ticksIn++;
            }
        }
        return new Walk(ticksIn, firstIn, xs, ys);
    }

    /** How long the man spent in the spatter, when he first set foot in it, and where he was on every tick. */
    private static final class Walk {
        private final int ticksIn;
        private final int firstIn;
        private final float[] xs;
        private final float[] ys;

        Walk(int ticksIn, int firstIn, float[] xs, float[] ys) {
            this.ticksIn = ticksIn;
            this.firstIn = firstIn;
            this.xs = xs;
            this.ys = ys;
        }

        int ticksIn() {
            return ticksIn;
        }

        /** The first tick he was in it, or the length of the walk if he never was. */
        int firstIn() {
            return firstIn;
        }

        float[] xs() {
            return xs;
        }

        float[] ys() {
            return ys;
        }
    }

    /**
     * Whether a man who walked into the spatter walked his own line into it: up to the moment it caught him, the line
     * he walked with no spatter there at all - not one lean off any circle of it.
     */
    private static boolean walkedHisOwnLineInto(Walk bled, Walk unbled) {
        return Arrays.equals(Arrays.copyOf(bled.ys(), bled.firstIn()), Arrays.copyOf(unbled.ys(), bled.firstIn()));
    }
}
